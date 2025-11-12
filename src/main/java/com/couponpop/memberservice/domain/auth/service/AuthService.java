package com.couponpop.memberservice.domain.auth.service;

import com.couponpop.couponpopcoremodule.dto.fcmtoken.request.FcmTokenExpireRequest;
import com.couponpop.memberservice.common.client.FcmTokenSystemFeignClient;
import com.couponpop.memberservice.common.exception.GlobalException;
import com.couponpop.memberservice.domain.auth.dto.request.LoginRequest;
import com.couponpop.memberservice.domain.auth.dto.request.LogoutRequest;
import com.couponpop.memberservice.domain.auth.dto.request.SignUpRequest;
import com.couponpop.memberservice.domain.auth.dto.request.WithdrawRequest;
import com.couponpop.memberservice.domain.auth.dto.response.LoginResponse;
import com.couponpop.memberservice.domain.auth.dto.response.SignUpResponse;
import com.couponpop.memberservice.domain.auth.event.TokenBlacklistEvent;
import com.couponpop.memberservice.domain.auth.exception.AuthErrorCode;
import com.couponpop.memberservice.domain.member.entity.Member;
import com.couponpop.memberservice.domain.member.enums.MemberType;
import com.couponpop.memberservice.domain.member.exception.MemberErrorCode;
import com.couponpop.memberservice.domain.member.repository.MemberRepository;
import com.couponpop.security.blacklist.service.TokenBlacklistService;
import com.couponpop.security.dto.AuthMember;
import com.couponpop.security.token.JwtProvider;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    private final MemberRepository memberRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final ApplicationEventPublisher eventPublisher;

    private final FcmTokenSystemFeignClient fcmTokenSystemFeignClient;

    @Transactional
    public SignUpResponse signUp(SignUpRequest signUpRequest) {

        // Admin MemberType은 내부에서만 가입하도록 API에서는 가입 금지
        if (MemberType.ADMIN.equals(signUpRequest.memberType())) {
            throw new GlobalException(AuthErrorCode.ACCESS_DENIED);
        }

        if (!signUpRequest.password().equals(signUpRequest.confirmPassword())) {
            throw new GlobalException(AuthErrorCode.PASSWORDS_NOT_MATCH);
        }

        if (memberRepository.existsByEmail(signUpRequest.email())) {
            throw new GlobalException(MemberErrorCode.EMAIL_DUPLICATED);
        }

        String encodedPassword = passwordEncoder.encode(signUpRequest.password());

        Member newMember = Member.signUp(signUpRequest.email(),
                signUpRequest.username(),
                encodedPassword,
                signUpRequest.phoneNumber(),
                signUpRequest.memberType());

        Member savedMember;
        try {
            savedMember = memberRepository.saveAndFlush(newMember); // 제약조건 즉시 검증을 위해 사용
        } catch (DataIntegrityViolationException e) {
            throw new GlobalException(MemberErrorCode.EMAIL_DUPLICATED);
        }

        return SignUpResponse.from(savedMember);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest loginRequest) {

        Member loginMember = memberRepository.findByEmail(loginRequest.email())
                .orElseThrow(() -> new GlobalException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(loginRequest.password(), loginMember.getPassword())) {
            throw new GlobalException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtProvider.createAccessToken(
                loginMember.getId(),
                loginMember.getUsername(),
                loginMember.getMemberType().name());

        return LoginResponse.from(accessToken);
    }

    // 트랜잭션은 DB 작업(FcmToken 삭제)만 보장하며,
    // Redis 블랙리스트 작업은 별도 (분산 트랜잭션 고려하지 않음)
    @Transactional
    public void logout(String authorizationHeader, AuthMember authMember, LogoutRequest logoutRequest) {

        String resolvedToken = extractToken(authorizationHeader);
        long expirationMillis = jwtProvider.getExpirationMillis(resolvedToken);

        blacklistToken(resolvedToken, expirationMillis);

        // FCM Token 만료 처리에 실패하더라도 로그아웃은 롤백하지 않음
        try {
            FcmTokenExpireRequest fcmTokenExpireRequest = FcmTokenExpireRequest.of(authMember.id(), logoutRequest.fcmToken());
            fcmTokenSystemFeignClient.expireFcmToken(fcmTokenExpireRequest);
        } catch (FeignException e) {
            log.error("[로그아웃] FCM 토큰 만료 처리 실패 - fcmToken={}, error={}", logoutRequest.fcmToken(), e.getMessage());
        }
    }

    // 회원 탈퇴가 되면 토큰만료 이벤트 발행, 회원탈퇴가 되지 않으면 롤백
    @Transactional
    public void withdraw(String authorizationHeader, AuthMember authMember, WithdrawRequest withdrawRequest) {

        String resolvedToken = extractToken(authorizationHeader);
        long expirationMillis = jwtProvider.getExpirationMillis(resolvedToken);

        Member memberToWithdraw = memberRepository.findById(authMember.id())
                .orElseThrow(() -> new GlobalException(MemberErrorCode.MEMBER_NOT_FOUND));

        memberToWithdraw.withdraw();

        // FCM Token 만료 처리에 실패하더라도 회원탈퇴는 롤백하지 않음
        try {
            FcmTokenExpireRequest fcmTokenExpireRequest = FcmTokenExpireRequest.of(authMember.id(), withdrawRequest.fcmToken());
            fcmTokenSystemFeignClient.expireFcmToken(fcmTokenExpireRequest);
        } catch (FeignException e) {
            log.error("[회원탈퇴] FCM 토큰 만료 처리 실패 - fcmToken={}, error={}", withdrawRequest.fcmToken(), e.getMessage());
        }

        publishBlacklistTokenEvent(resolvedToken, expirationMillis);
    }

    // 즉시 블랙리스트 추가
    private void blacklistToken(String token, long expirationMillis) {
        tokenBlacklistService.blacklistToken(token, expirationMillis);
    }

    // 블랙리스트 이벤트 발행
    private void publishBlacklistTokenEvent(String token, long expirationMillis) {

        TokenBlacklistEvent event = TokenBlacklistEvent.of(token, expirationMillis);
        eventPublisher.publishEvent(event);
        log.debug("[publishBlacklistTokenEvent] 토큰 블랙리스트 이벤트 발행 - token={}", token);
    }

    private String extractToken(String authorizationHeader) {
        return Optional.ofNullable(jwtProvider.resolveToken(authorizationHeader))
                .orElseThrow(() -> new GlobalException(AuthErrorCode.INVALID_TOKEN));
    }
}

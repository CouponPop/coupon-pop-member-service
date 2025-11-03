package com.couponpop.memberservice.domain.auth.service;

import com.couponpop.memberservice.domain.auth.dto.request.LoginRequest;
import com.couponpop.memberservice.domain.auth.dto.request.LogoutRequest;
import com.couponpop.memberservice.domain.auth.dto.request.SignUpRequest;
import com.couponpop.memberservice.domain.auth.dto.request.WithdrawRequest;
import com.couponpop.memberservice.domain.auth.dto.response.LoginResponse;
import com.couponpop.memberservice.domain.auth.dto.response.SignUpResponse;
import com.couponpop.memberservice.domain.auth.event.TokenBlacklistEvent;
import com.couponpop.memberservice.domain.auth.exception.AuthErrorCode;
import com.couponpop.memberservice.domain.member.entity.Member;
import com.couponpop.memberservice.domain.member.exception.MemberErrorCode;
import com.couponpop.memberservice.domain.member.repository.MemberRepository;
import com.couponpop.memberservice.global.exception.GlobalException;
import com.couponpop.security.blacklist.service.TokenBlacklistService;
import com.couponpop.security.dto.AuthMember;
import com.couponpop.security.token.JwtProvider;
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

    @Transactional
    public SignUpResponse signUp(SignUpRequest signUpRequest) {

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


    // TODO: FCM 토큰 관련 로직 다른 서비스로 요청하는 방식으로 변경 필요
    // TODO: JwtProvider 관련 로직은 모듈에서 모두 처리하도록 변경 필요

    // 트랜잭션은 DB 작업(FcmToken 삭제)만 보장하며,
    // Redis 블랙리스트 작업은 별도 (분산 트랜잭션 고려하지 않음)
    @Transactional
    public void logout(String authorizationHeader, LogoutRequest logoutRequest, AuthMember authMember) {

        String resolvedToken = extractToken(authorizationHeader);
        long expirationMillis = jwtProvider.getExpirationMillis(resolvedToken);

        blacklistToken(resolvedToken, expirationMillis);
//        expireFcmToken(authMember.id(), logoutRequest.fcmToken());
    }

    // 회원 탈퇴가 되면 토큰만료 이벤트 발행, 회원탈퇴가 되지 않으면 롤백
    @Transactional
    public void withdraw(String authorizationHeader, AuthMember authMember, WithdrawRequest withdrawRequest) {

        String resolvedToken = extractToken(authorizationHeader);
        long expirationMillis = jwtProvider.getExpirationMillis(resolvedToken);

        Member memberToWithdraw = memberRepository.findById(authMember.id())
                .orElseThrow(() -> new GlobalException(MemberErrorCode.MEMBER_NOT_FOUND));

        memberToWithdraw.withdraw();
//        expireFcmToken(memberToWithdraw.getId(), withdrawRequest.fcmToken());
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

//    // FCM 토큰 삭제는 실패하더라도 전체 작업이 롤백되지는 않도록 ifPresent 사용
//    private void expireFcmToken(Long memberId, String fcmToken) {
//
//        memberFcmTokenRepository
//                .findByMemberIdAndFcmToken(memberId, fcmToken)
//                .ifPresent(memberFcmTokenRepository::delete);
//    }

    private String extractToken(String authorizationHeader) {
        return Optional.ofNullable(jwtProvider.resolveToken(authorizationHeader))
                .orElseThrow(() -> new GlobalException(AuthErrorCode.INVALID_TOKEN));
    }
}

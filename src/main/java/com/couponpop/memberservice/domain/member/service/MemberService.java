package com.couponpop.memberservice.domain.member.service;

import com.couponpop.memberservice.global.exception.GlobalException;
import com.couponpop.memberservice.domain.auth.exception.AuthErrorCode;
import com.couponpop.memberservice.domain.member.dto.request.MemberProfileUpdateRequest;
import com.couponpop.memberservice.domain.member.dto.response.MemberProfileResponse;
import com.couponpop.memberservice.domain.member.entity.Member;
import com.couponpop.memberservice.domain.member.exception.MemberErrorCode;
import com.couponpop.memberservice.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MemberProfileResponse getMemberProfile(Long memberId) {

        Member member = findMemberById(memberId);
        return MemberProfileResponse.from(member);
    }

    @Transactional
    public MemberProfileResponse updateMemberProfile(Long memberId, MemberProfileUpdateRequest request) {

        Member member = findMemberById(memberId);
        String encodedPassword = encodeIfPasswordIsValid(request);
        member.updateProfile(request, encodedPassword);
        return MemberProfileResponse.from(member);
    }

    private String encodeIfPasswordIsValid(MemberProfileUpdateRequest request) {

        if (!hasPasswordInput(request)) {
            return null;
        }

        validatePasswordMatch(request.password(), request.passwordConfirm());
        return passwordEncoder.encode(request.password());
    }

    private boolean hasPasswordInput(MemberProfileUpdateRequest request) {

        boolean hasPassword = StringUtils.hasText(request.password());
        boolean hasPasswordConfirm = StringUtils.hasText(request.passwordConfirm());

        if (!hasPassword && !hasPasswordConfirm) { // 둘 다 없음
            return false;
        }

        if (!hasPassword || !hasPasswordConfirm) { // 둘 중 하나만 없음
            throw new GlobalException(MemberErrorCode.PASSWORD_INPUT_INCOMPLETE);
        }

        return true;
    }

    private void validatePasswordMatch(String password, String confirmPassword) {

        if (!password.equals(confirmPassword)) {
            throw new GlobalException(AuthErrorCode.PASSWORDS_NOT_MATCH);
        }
    }

    private Member findMemberById(Long memberId) {

        return memberRepository.findById(memberId)
                .orElseThrow(() -> new GlobalException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}

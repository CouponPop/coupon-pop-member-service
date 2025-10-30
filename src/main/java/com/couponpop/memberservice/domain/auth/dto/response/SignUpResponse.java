package com.couponpop.memberservice.domain.auth.dto.response;


import com.couponpop.memberservice.domain.member.entity.Member;

public record SignUpResponse(

        Long memberId,
        String email,
        String username,
        String phoneNumber,
        String memberType
) {
    public static SignUpResponse from(Member member) {
        return new SignUpResponse(
                member.getId(),
                member.getEmail(),
                member.getUsername(),
                member.getPhoneNumber(),
                member.getMemberType().name()
        );
    }
}

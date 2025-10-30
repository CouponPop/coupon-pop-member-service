package com.couponpop.memberservice.domain.member.controller;

import com.couponpop.memberservice.domain.member.dto.request.MemberProfileUpdateRequest;
import com.couponpop.memberservice.domain.member.dto.response.MemberProfileResponse;
import com.couponpop.memberservice.domain.member.service.MemberService;
import com.couponpop.memberservice.global.response.ApiResponse;
import com.couponpop.security.annotation.CurrentMember;
import com.couponpop.security.dto.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> getMyProfile(@CurrentMember AuthMember authMember) {

        MemberProfileResponse response = memberService.getMemberProfile(authMember.id());
        return ApiResponse.success(response);
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> updateProfile(
            @CurrentMember AuthMember authMember, @Valid @RequestBody MemberProfileUpdateRequest request) {

        MemberProfileResponse response = memberService.updateMemberProfile(authMember.id(), request);
        return ApiResponse.success(response);
    }
}

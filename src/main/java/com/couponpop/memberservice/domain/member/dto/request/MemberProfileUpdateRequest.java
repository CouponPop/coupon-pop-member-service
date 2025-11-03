package com.couponpop.memberservice.domain.member.dto.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;

public record MemberProfileUpdateRequest(

        @Nullable
        @Pattern(regexp = "^$|^[a-zA-Z가-힣0-9]{2,50}$",
                message = "사용자 이름은 2자 이상 50자 이하로 입력해주세요.")
        String username,

        @Nullable
        @Pattern(regexp = "^$|^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,15}$",
                message = "비밀번호는 8~15자리의 영문, 숫자, 특수문자 조합이어야 합니다.")
        String password,

        @Nullable
        @Pattern(regexp = "^$|^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,15}$",
                message = "비밀번호는 8~15자리의 영문, 숫자, 특수문자 조합이어야 합니다.")
        String passwordConfirm,

        @Nullable
        String phoneNumber
) {
}
package com.couponpop.memberservice.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,15}$",
                message = "비밀번호는 8~15자리의 영문, 숫자, 특수문자 조합이어야 합니다.")
        String password
) {
}

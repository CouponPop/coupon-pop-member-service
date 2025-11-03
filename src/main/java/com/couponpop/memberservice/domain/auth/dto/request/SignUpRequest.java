package com.couponpop.memberservice.domain.auth.dto.request;

import com.couponpop.memberservice.domain.member.enums.MemberType;
import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record SignUpRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "사용자 이름을 입력해주세요.")
        @Size(min = 2, max = 50, message = "사용자 이름은 2자 이상 50자 이하로 입력해주세요.")
        String username,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,15}$",
                message = "비밀번호는 8~15자리의 영문, 숫자, 특수문자 조합이어야 합니다.")
        String password,

        @NotBlank(message = "비밀번호 확인을 입력해주세요.")
        String confirmPassword,

        @NotBlank(message = "전화번호를 입력해주세요.")
        String phoneNumber,

        @NotNull(message = "회원 유형을 확인해주세요.")
        MemberType memberType
) {
}
package com.couponpop.memberservice.domain.auth.controller;

import com.couponpop.memberservice.common.response.ApiResponse;
import com.couponpop.memberservice.domain.auth.dto.request.LoginRequest;
import com.couponpop.memberservice.domain.auth.dto.request.LogoutRequest;
import com.couponpop.memberservice.domain.auth.dto.request.SignUpRequest;
import com.couponpop.memberservice.domain.auth.dto.request.WithdrawRequest;
import com.couponpop.memberservice.domain.auth.dto.response.LoginResponse;
import com.couponpop.memberservice.domain.auth.dto.response.SignUpResponse;
import com.couponpop.memberservice.domain.auth.service.AuthService;
import com.couponpop.security.annotation.CurrentMember;
import com.couponpop.security.dto.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest signUpRequest) {

        SignUpResponse signUpResponse = authService.signUp(signUpRequest);
        return ApiResponse.created(signUpResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {

        LoginResponse loginResponse = authService.login(loginRequest);
        return ApiResponse.success(loginResponse);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()") // auth는 모두 접근가능하므로, 로그아웃은 인증된 사용자만 접근 가능
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                                    @CurrentMember AuthMember authMember,
                                                    @Valid @RequestBody LogoutRequest logoutRequest) {

        authService.logout(authorizationHeader, authMember, logoutRequest);
        return ApiResponse.noContent();
    }

    @DeleteMapping("/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> withdraw(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                                      @CurrentMember AuthMember authMember,
                                                      @Valid @RequestBody WithdrawRequest withdrawRequest) {

        authService.withdraw(authorizationHeader, authMember, withdrawRequest);
        return ApiResponse.noContent();
    }
}

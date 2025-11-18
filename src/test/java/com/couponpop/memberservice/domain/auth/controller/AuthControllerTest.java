package com.couponpop.memberservice.domain.auth.controller;


import com.couponpop.memberservice.domain.auth.dto.request.LoginRequest;
import com.couponpop.memberservice.domain.auth.dto.request.LogoutRequest;
import com.couponpop.memberservice.domain.auth.dto.request.SignUpRequest;
import com.couponpop.memberservice.domain.auth.dto.request.WithdrawRequest;
import com.couponpop.memberservice.domain.auth.dto.response.LoginResponse;
import com.couponpop.memberservice.domain.auth.dto.response.SignUpResponse;
import com.couponpop.memberservice.domain.auth.service.AuthService;
import com.couponpop.memberservice.domain.member.entity.Member;
import com.couponpop.memberservice.domain.member.enums.MemberType;
import com.couponpop.security.dto.AuthMember;
import com.couponpop.security.token.JwtAuthFilter;
import com.couponpop.security.token.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureRestDocs
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @DisplayName("회원 가입을 한다.")
    void signUpSuccess() throws Exception {

        // given
        SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .username("테스트이름")
                .password("test1234!")
                .confirmPassword("test1234!")
                .phoneNumber("01012345678")
                .memberType(MemberType.CUSTOMER)
                .build();


        SignUpResponse response = SignUpResponse.from(
                Member.signUp("test@example.com",
                        "테스트이름",
                        "test1234!",
                        "01012345678",
                        MemberType.CUSTOMER));

        given(authService.signUp(any(SignUpRequest.class))).willReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
                post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)));

        // then
        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.username").value("테스트이름"))
                .andDo(print());

        // docs
//        resultActions.andDo(document("auth-signUp",
//                preprocessRequest(prettyPrint()),
//                preprocessResponse(prettyPrint()),
//                resource(
//                        ResourceSnippetParameters.builder()
//                                .summary("회원가입 API")
//                                .description("새로운 사용자가 입력한 정보를 이용하여 회원가입을 수행합니다. 회원가입 성공 시 저장된 정보를 반환합니다.")
//                                .tag("Auth")
//                                .requestSchema(Schema.schema("Auth.SignUpRequest"))
//                                .responseSchema(Schema.schema("Auth.SignUpResponse"))
//                                .requestFields(
//                                        fieldWithPath("email").description("사용자 이메일 (형식: user@example.com)"),
//                                        fieldWithPath("username").description("사용자 이름 (2자 이상 50자 이하)"),
//                                        fieldWithPath("password").description("비밀번호 (8~15자, 영문+숫자+특수문자 조합)"),
//                                        fieldWithPath("confirmPassword").description("비밀번호 확인"),
//                                        fieldWithPath("phoneNumber").description("전화번호 (예: 01012345678)"),
//                                        fieldWithPath("memberType").description("회원 유형 (CUSTOMER 또는 OWNER)")
//                                )
//                                .responseFields(
//                                        fieldWithPath("data.memberId").description("생성된 회원 고유 식별자"),
//                                        fieldWithPath("data.email").description("가입된 이메일"),
//                                        fieldWithPath("data.username").description("사용자 이름"),
//                                        fieldWithPath("data.phoneNumber").description("전화번호"),
//                                        fieldWithPath("data.memberType").description("회원 유형 (CUSTOMER/OWNER)")
//                                )
//
//                                .build()
//                )));
    }

    @Test
    @DisplayName("회원가입을 할 때 이메일은 필수값이다.")
    void signUpWithoutEmail() throws Exception {

        // given
        SignUpRequest request = SignUpRequest.builder()
                .username("테스트이름")
                .password("test1234!")
                .confirmPassword("test1234!")
                .phoneNumber("01012345678")
                .memberType(MemberType.CUSTOMER)
                .build();


        // when
        ResultActions resultActions = mockMvc.perform(
                post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("이메일을 입력해주세요."))
                .andDo(print());
    }

    @Test
    @DisplayName("회원가입을 할 때 비밀번호는 8~15자리의 영문, 숫자, 특수문자 조합이여야 한다.")
    void signUpWithInvalidPassword() throws Exception {

        // given
        SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .username("테스트이름")
                .password("test1234")
                .confirmPassword("test1234")
                .phoneNumber("010-1234-5678")
                .memberType(MemberType.CUSTOMER)
                .build();


        // when
        ResultActions resultActions = mockMvc.perform(
                post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("비밀번호는 8~15자리의 영문, 숫자, 특수문자 조합이어야 합니다."))
                .andDo(print());
    }

    @Test
    @DisplayName("회원가입을 할 때 사용자 이름은 2자 이상 50자 이하로 입력해야 한다.")
    void signUpWithInvalidUserName() throws Exception {

        // given
        SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .username("123451234512345123451234512345123451234512345123451") // 51자
                .password("test1234!")
                .confirmPassword("test1234!")
                .phoneNumber("010-1234-5678")
                .memberType(MemberType.CUSTOMER)
                .build();


        // when
        ResultActions resultActions = mockMvc.perform(
                post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("사용자 이름은 2자 이상 50자 이하로 입력해주세요."))
                .andDo(print());
    }

    @Test
    @DisplayName("로그인을 한다.")
    void loginSuccess() throws Exception {

        // given
        LoginRequest request = new LoginRequest("test@example.com", "test1234!");

        String accessToken = "mockedJwtToken";
        LoginResponse response = new LoginResponse(accessToken);

        given(authService.login(any(LoginRequest.class))).willReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value(accessToken))
                .andDo(print());

        // docs
//        resultActions.andDo(document("auth-login",
//                preprocessRequest(prettyPrint()),
//                preprocessResponse(prettyPrint()),
//                resource(ResourceSnippetParameters.builder()
//                        .summary("로그인 API")
//                        .description("사용자가 이메일/비밀번호를 이용하여 로그인을 수행합니다. 로그인 성공 시 JWT AccessToken을 반환합니다.")
//                        .tag("Auth")
//                        .requestSchema(Schema.schema("Auth.LoginRequest"))
//                        .responseSchema(Schema.schema("Auth.LoginResponse"))
//                        .requestFields(
//                                fieldWithPath("email").description("사용자 이메일 (형식: test@example.com)"),
//                                fieldWithPath("password").description("비밀번호 (8~15자의 영문, 숫자, 특수문자 조합)")
//                        )
//                        .responseFields(
//                                fieldWithPath("data.accessToken").description("JWT Access Token")
//                        )
//                        .build()
//                )
//        ));
    }

    @Test
    @DisplayName("로그인을 할 때 이메일은 필수값이다.")
    void loginWithInvalidEmail() throws Exception {

        // given
        LoginRequest invalidRequestDto = new LoginRequest("", "test1234!");

        // when
        ResultActions resultActions = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequestDto))
        );

        // then (결과는 이래야 한다)
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("이메일을 입력해주세요."))
                .andDo(print());
    }

    @Test
    @DisplayName("로그아웃에 성공한다.")
    @WithMockUser
    void logoutSuccess() throws Exception {

        // given
        String testAuthorizationHeader = "Bearer testAccessToken";
        LogoutRequest requestDto = new LogoutRequest("testFcmToken");

        // authService.logout()은 void를 반환
        doNothing().when(authService).logout(anyString(), any(AuthMember.class), any(LogoutRequest.class));

        // when
        ResultActions resultActions = mockMvc.perform(
                post("/api/v1/auth/logout") // 요청 URL
                        .header("Authorization", testAuthorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto))
        );

        // then
        resultActions
                .andExpect(status().isNoContent())
                .andDo(print());

        // docs
//        resultActions.andDo(document("auth-logout",
//                preprocessRequest(prettyPrint()),
//                preprocessResponse(prettyPrint()),
//                resource(ResourceSnippetParameters.builder()
//                        .summary("로그아웃 API")
//                        .description("사용자가 로그아웃을 수행합니다. 내부적으로 JWT Token과 FcmToken을 만료처리합니다.")
//                        .tag("Auth")
//                        .requestSchema(Schema.schema("Auth.LogoutRequest"))
//                        .requestFields(
//                                fieldWithPath("fcmToken").description("사용자의 FcmToken")
//                        )
//                        .build()
//                )
//        ));
    }

    @Test
    @DisplayName("회원 탈퇴를 한다.")
    @WithMockUser
    void withdrawSuccess() throws Exception {

        // given
        String testAuthorizationHeader = "Bearer testToken";
        WithdrawRequest requestDto = new WithdrawRequest("testFcmToken");

        doNothing().when(authService).withdraw(anyString(), any(AuthMember.class), any(WithdrawRequest.class));

        // when
        ResultActions resultActions = mockMvc.perform(
                delete("/api/v1/auth/withdraw")
                        .header("Authorization", testAuthorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto))
        );

        // then
        resultActions
                .andExpect(status().isNoContent())
                .andDo(print());

        // docs
//        resultActions.andDo(document("auth-withdraw",
//                preprocessRequest(prettyPrint()),
//                preprocessResponse(prettyPrint()),
//                resource(ResourceSnippetParameters.builder()
//                        .summary("회원 탈퇴 API")
//                        .description("사용자가 회원 탈퇴를 수행합니다. 사용자의 정보를 Soft Delete하고, JWT Token과 FcmToken을 만료처리합니다.")
//                        .tag("Auth")
//                        .requestSchema(Schema.schema("Auth.WithdrawRequest"))
//                        .requestFields(
//                                fieldWithPath("fcmToken").description("사용자의 FcmToken")
//                        )
//                        .build()
//                )
//        ));
    }
}
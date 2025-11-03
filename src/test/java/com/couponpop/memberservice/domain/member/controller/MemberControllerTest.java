package com.couponpop.memberservice.domain.member.controller;


import com.couponpop.memberservice.domain.auth.exception.AuthErrorCode;
import com.couponpop.memberservice.domain.member.dto.request.MemberProfileUpdateRequest;
import com.couponpop.memberservice.domain.member.dto.response.MemberProfileResponse;
import com.couponpop.memberservice.domain.member.enums.MemberType;
import com.couponpop.memberservice.domain.member.service.MemberService;
import com.couponpop.memberservice.global.exception.GlobalException;
import com.couponpop.security.dto.AuthMember;
import com.couponpop.security.token.JwtAuthFilter;
import com.couponpop.security.token.JwtAuthenticationToken;
import com.couponpop.security.token.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureRestDocs
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        AuthMember authMember = AuthMember.of(1L, "테스트이름", MemberType.CUSTOMER.name());
        Authentication authenticationToken = new JwtAuthenticationToken(authMember);
        context.setAuthentication(authenticationToken);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("사용자가 자신의 프로필을 조회한다.")
    void getMyProfileSuccess() throws Exception {

        // given
        Long mockMemberId = 1L;
        MemberProfileResponse mockResponse = new MemberProfileResponse(
                mockMemberId,
                "테스트이름",
                "test@example.com",
                "01012345678",
                MemberType.CUSTOMER
        );
        String mockJwtToken = "Bearer mockedJwtToken";

        given(memberService.getMemberProfile(mockMemberId)).willReturn(mockResponse);

        // when
        ResultActions resultActions = mockMvc.perform(get("/api/v1/members/me")
                .contentType(MediaType.APPLICATION_JSON));

        // then
        resultActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("테스트이름"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.memberType").value("CUSTOMER"))
                .andDo(print());

        // docs
//        resultActions.andDo(document("member-getMyProfile",
//                preprocessRequest(prettyPrint()),
//                preprocessResponse(prettyPrint()),
//                resource(
//                        ResourceSnippetParameters.builder()
//                                .summary("내 프로필 조회 API")
//                                .description("사용자가 자신의 프로필 조회를 수행합니다. 프로필 정보를 반환받습니다.")
//                                .tag("Member")
//                                .responseSchema(Schema.schema("Auth.MemberProfileResponse"))
//                                .responseFields(
//                                        fieldWithPath("data.id").type(JsonFieldType.NUMBER)
//                                                .description("회원 고유 ID"),
//                                        fieldWithPath("data.username").type(JsonFieldType.STRING)
//                                                .description("사용자 이름"),
//                                        fieldWithPath("data.email").type(JsonFieldType.STRING)
//                                                .description("사용자 이메일"),
//                                        fieldWithPath("data.phoneNumber").type(JsonFieldType.STRING)
//                                                .description("전화번호"),
//                                        fieldWithPath("data.memberType").type(JsonFieldType.STRING)
//                                                .description("회원 유형 (CUSTOMER / OWNER)")
//                                )
//                                .build()
//                )
//        ));
    }

    @Test
    @DisplayName("사용자가 자신의 프로필을 수정한다.")
    void updateProfileSuccess() throws Exception {

        // given
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest(
                "테스트이름", "qwer1234!", "qwer1234!", "01012345678"
        );

        MemberProfileResponse response = new MemberProfileResponse(
                1L, "테스트이름", "test@example.com", "01012345678", MemberType.CUSTOMER
        );

        given(memberService.updateMemberProfile(Mockito.eq(1L), Mockito.any())).willReturn(response);

        AuthMember authMember = AuthMember.of(1L, "테스트이름", MemberType.CUSTOMER.name());
        Authentication authentication = new JwtAuthenticationToken(authMember);

        // when & then
        ResultActions resultActions = mockMvc.perform(put("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("테스트이름"))
                .andExpect(jsonPath("$.data.phoneNumber").value("01012345678"))
                .andDo(print());

        // docs
//        resultActions.andDo(document("member-updateProfile",
//                preprocessRequest(prettyPrint()),
//                preprocessResponse(prettyPrint()),
//                resource(
//                        ResourceSnippetParameters.builder()
//                                .summary("내 프로필 수정 API")
//                                .description("사용자가 자신의 프로필 수정을 수행합니다. 수정된 프로필 정보를 반환받습니다.")
//                                .tag("Member")
//                                .requestSchema(Schema.schema("Auth.MemberProfileUpdateRequest"))
//                                .responseSchema(Schema.schema("Auth.MemberProfileResponse"))
//                                .requestFields(
//                                        fieldWithPath("username").optional()
//                                                .description("사용자 이름 (2~50자, 한글/영문/숫자 가능)"),
//                                        fieldWithPath("password").optional()
//                                                .description("새 비밀번호 (8~15자, 영문+숫자+특수문자 조합)"),
//                                        fieldWithPath("passwordConfirm").optional()
//                                                .description("비밀번호 확인"),
//                                        fieldWithPath("phoneNumber").optional()
//                                                .description("전화번호 (예: 01012345678)")
//                                )
//                                .responseFields(
//                                        fieldWithPath("data.id").type(JsonFieldType.NUMBER)
//                                                .description("회원 고유 ID"),
//                                        fieldWithPath("data.username").type(JsonFieldType.STRING)
//                                                .description("사용자 이름"),
//                                        fieldWithPath("data.email").type(JsonFieldType.STRING)
//                                                .description("사용자 이메일"),
//                                        fieldWithPath("data.phoneNumber").type(JsonFieldType.STRING)
//                                                .description("전화번호"),
//                                        fieldWithPath("data.memberType").type(JsonFieldType.STRING)
//                                                .description("회원 유형 (CUSTOMER / OWNER)")
//                                )
//                                .build()
//                )
//        ));
    }

    @Test
    @DisplayName("비밀번호와 비밀번호 확인이 다르면 프로필 수정에 실패한다.")
    void updateProfileWithPasswordMismatch() throws Exception {

        // given
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest(
                "테스트이름", "qwer1234!", "qwer1234@", "01099999999"
        );

        willThrow(new GlobalException(AuthErrorCode.PASSWORDS_NOT_MATCH)).given(memberService).updateMemberProfile(Mockito.eq(1L), Mockito.any());

        AuthMember authMember = AuthMember.of(1L, "테스트이름", MemberType.CUSTOMER.name());
        Authentication authentication = new JwtAuthenticationToken(authMember);

        // when & then
        mockMvc.perform(put("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORDS_NOT_MATCH"))
                .andDo(print());
    }
}
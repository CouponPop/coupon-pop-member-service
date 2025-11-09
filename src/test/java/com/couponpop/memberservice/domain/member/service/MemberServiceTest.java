package com.couponpop.memberservice.domain.member.service;


import com.couponpop.memberservice.global.exception.GlobalException;
import com.couponpop.memberservice.domain.auth.exception.AuthErrorCode;
import com.couponpop.memberservice.domain.member.dto.request.MemberProfileUpdateRequest;
import com.couponpop.memberservice.domain.member.dto.response.MemberProfileResponse;
import com.couponpop.memberservice.domain.member.entity.Member;
import com.couponpop.memberservice.domain.member.enums.MemberType;
import com.couponpop.memberservice.domain.member.exception.MemberErrorCode;
import com.couponpop.memberservice.domain.member.repository.MemberRepository;
import com.couponpop.memberservice.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    private Member mockMember;

    @BeforeEach
    void setUp() {
        mockMember = TestUtils.createEntity(Member.class, Map.of(
                "id", 1L,
                "username", "기존이름",
                "email", "test@example.com",
                "password", "기존비밀번호",
                "phoneNumber", "01099999999",
                "memberType", MemberType.CUSTOMER));
    }

    @Test
    @DisplayName("회원의 고유식별자를 통해 회원 프로필을 조회한다.")
    void getMemberProfileSuccess() {

        // given
        Long mockMemberId = 1L;
        Member mockMember = TestUtils.createEntity(Member.class, Map.of(
                "id", mockMemberId,
                "username", "테스트이름",
                "email", "test@example.com",
                "phoneNumber", "01012345678",
                "memberType", MemberType.CUSTOMER));

        given(memberRepository.findById(mockMemberId)).willReturn(Optional.of(mockMember));

        // when
        MemberProfileResponse response = memberService.getMemberProfile(mockMemberId);

        // then
        assertThat(response.id()).isEqualTo(mockMemberId);
        assertThat(response.username()).isEqualTo("테스트이름");
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.phoneNumber()).isEqualTo("01012345678");
        assertThat(response.memberType()).isEqualTo(MemberType.CUSTOMER);
    }

    @Test
    @DisplayName("회원의 고유식별자로 회원 프로필 조회 시, 회원을 찾을 수 없으면 예외가 발생한다.")
    void getMemberProfileFailureMemberNotFound() {

        // given
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberService.getMemberProfile(999L))
                .isInstanceOf(GlobalException.class)
                .hasMessage(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }


    @Test
    @DisplayName("비밀번호를 제외한 프로필 정보를 받아, 회원 프로필을 수정한다.")
    void updateProfileSuccessWithoutPassword() {

        // given
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest("새이름", "", "", "01012345678");
        given(memberRepository.findById(1L)).willReturn(Optional.of(mockMember));

        // when
        MemberProfileResponse response = memberService.updateMemberProfile(1L, request);

        // then
        assertThat(response.username()).isEqualTo(request.username());
        assertThat(response.phoneNumber()).isEqualTo(request.phoneNumber());
        assertThat(mockMember.getPassword()).isEqualTo("기존비밀번호");
    }

    @Test
    @DisplayName("비밀번호를 폼한 프로필 정보를 입력 받아, 회원 프로필을 수정한다.")
    void updateProfileSuccessWithPassword() {

        // given
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest(
                "새로운이름", "새로운비밀번호1234!", "새로운비밀번호1234!", "01012345678"
        );

        given(memberRepository.findById(1L)).willReturn(Optional.of(mockMember));
        given(passwordEncoder.encode("새로운비밀번호1234!")).willReturn("새로운인코딩된비밀번호");

        // when
        MemberProfileResponse response = memberService.updateMemberProfile(1L, request);

        // then
        assertThat(mockMember.getPassword()).isEqualTo("새로운인코딩된비밀번호");
        assertThat(response.username()).isEqualTo("새로운이름");
    }

    @Test
    @DisplayName("비밀번호와 비밀번호 확인이 다르면 비밀번호 불일치 예외가 발생한다.")
    void updateProfileFailurePasswordsNotMatch() {

        // given
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest(
                "새로운이름", "새로운비밀번호1234!", "다른비밀번호1234!", "01012345678"
        );

        given(memberRepository.findById(1L)).willReturn(Optional.of(mockMember));

        // when & then
        GlobalException exception = assertThrows(GlobalException.class, () -> {
            memberService.updateMemberProfile(1L, request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.PASSWORDS_NOT_MATCH);
    }

    @Test
    @DisplayName("존재하지 않는 회원 ID으로 프로필 수정을 요청하면 예외가 발생한다.")
    void updateProfileFailureMemberNotFound() {

        // given
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest(
                "새로운이름", "새로운비밀번호1234!", "새로운비밀번호1234!", "01012345678"
        );

        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        GlobalException exception = assertThrows(GlobalException.class, () -> {
            memberService.updateMemberProfile(999L, request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }
}
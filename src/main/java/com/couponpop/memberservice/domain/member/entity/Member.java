package com.couponpop.memberservice.domain.member.entity;

import com.couponpop.memberservice.domain.member.dto.request.MemberProfileUpdateRequest;
import com.couponpop.memberservice.domain.member.enums.MemberType;
import com.couponpop.memberservice.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 30)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberType memberType;

    private LocalDateTime deletedAt;

    @Builder(access = AccessLevel.PROTECTED)
    private Member(String email, String username, String password, String phoneNumber, MemberType memberType) {
        this.email = email;
        this.username = username;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.memberType = memberType;
    }

    public static Member signUp(String email, String username, String password, String phoneNumber, MemberType memberType) {
        return Member.builder()
                .email(email)
                .username(username)
                .password(password)
                .phoneNumber(phoneNumber)
                .memberType(memberType)
                .build();
    }

    public void updateProfile(MemberProfileUpdateRequest request, String encodedPassword) {

        updateIfPresent(request.username(), this::updateUsername);
        updateIfPresent(request.phoneNumber(), this::updatePhoneNumber);
        updateIfPresent(encodedPassword, this::updatePassword);
    }

    private void updateIfPresent(String newValue, Consumer<String> updater) {
        if (StringUtils.hasText(newValue)) {
            updater.accept(newValue);
        }
    }

    private void updateUsername(String username) {
        this.username = username;
    }

    private void updatePhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    private void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
    }
}

package com.couponpop.memberservice.domain.member.enums;

public enum MemberType {

    ADMIN,
    OWNER,
    CUSTOMER;

    public String roleName() {
        return "ROLE_" + this.name();
    }
}

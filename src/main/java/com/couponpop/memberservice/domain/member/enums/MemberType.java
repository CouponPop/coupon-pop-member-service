package com.couponpop.memberservice.domain.member.enums;

public enum MemberType {

    OWNER,
    CUSTOMER;

    public String roleName() {
        return "ROLE_" + this.name();
    }
}

/* V3: members 테이블의 member_type ENUM에 'ADMIN' 추가 */
ALTER TABLE members
    MODIFY COLUMN member_type ENUM('ADMIN', 'OWNER', 'CUSTOMER') NOT NULL COMMENT '회원 유형';
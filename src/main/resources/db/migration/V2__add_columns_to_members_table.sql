ALTER TABLE members
    ADD COLUMN member_type   ENUM('OWNER', 'CUSTOMER') NOT NULL COMMENT '회원 유형',
    ADD COLUMN email         VARCHAR(255)              NOT NULL UNIQUE COMMENT '이메일 주소',
    ADD COLUMN username      VARCHAR(50)               NOT NULL COMMENT '사용자 이름',
    ADD COLUMN password      VARCHAR(255)              NOT NULL COMMENT '비밀번호',
    ADD COLUMN phone_number  VARCHAR(30)               NOT NULL COMMENT '전화번호',
    ADD COLUMN created_at    DATETIME                  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입일',
    ADD COLUMN updated_at    DATETIME                  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    ADD COLUMN deleted_at    DATETIME                  NULL COMMENT '삭제일';
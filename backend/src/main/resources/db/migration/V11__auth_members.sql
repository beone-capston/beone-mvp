-- 회원 인증 U2: 회원·이메일 인증 토큰·메일 요청 제한(D-24, docs/api/auth.md).
-- 회원은 시연 사용자(users)와 별도 테이블이다. 시간 값은 Asia/Seoul 기준 시각이다.
-- 버전 번호 V11은 다른 작업선과 나눈 임시 번호다(V10은 다른 작업선 대기). 병합 직전 관리자가 순서를 정한다(D-23).

-- 회원. ID는 'usr-' + 무작위 32자 16진수(관리자 판단, 결정 28). 이메일은 정규화(앞뒤 공백 제거·소문자)한 값이다.
-- 비밀번호는 Argon2id 해시 문자열만 저장한다. 이메일 인증 전이면 email_verified_at이 NULL이다.
CREATE TABLE members (
    member_id         VARCHAR(36)  NOT NULL,
    email             VARCHAR(254) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    email_verified_at DATETIME     NULL,
    created_at        DATETIME     NOT NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT uk_members_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 메일로 보내는 1회용 토큰. 원문은 저장하지 않고 SHA-256 해시(16진수 64자)만 저장한다.
-- 목적(purpose)이 다른 토큰은 받지 않는다. 사용하거나 재발급하면 행을 지운다.
CREATE TABLE auth_tokens (
    auth_token_id BIGINT      NOT NULL AUTO_INCREMENT,
    member_id     VARCHAR(36) NOT NULL,
    purpose       VARCHAR(30) NOT NULL,
    token_hash    CHAR(64)    NOT NULL,
    created_at    DATETIME    NOT NULL,
    expires_at    DATETIME    NOT NULL,
    PRIMARY KEY (auth_token_id),
    CONSTRAINT uk_auth_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_tokens_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE,
    INDEX ix_auth_tokens_member_purpose (member_id, purpose)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 같은 주소의 메일 요청 간격(60초) 기록. 계정이 없는 주소도 세므로 이메일 원문 대신 SHA-256 해시만 저장한다.
-- last_requested_at이 NULL인 행은 같은 트랜잭션이 잠금을 잡으려고 방금 만든 행이며, 요청을 접수하면 커밋 전에 시각을 채운다.
CREATE TABLE auth_mail_throttles (
    email_hash        CHAR(64) NOT NULL,
    last_requested_at DATETIME NULL,
    PRIMARY KEY (email_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 회원 로그인 세션과 로그인 실패 제한(D-24, docs/api/auth.md U3).
-- 버전 번호 V13은 다른 작업선과 나눈 임시 번호다(V12는 CODEF C3 예정). 병합 직전 관리자가 순서를 정한다(D-23).
-- 시간 값은 Asia/Seoul 기준 시각이다.

-- 회원 세션은 기존 auth_sessions 행에 회원 ID를 붙인다. 회원 ID가 없으면 익명 CSRF 세션이다.
-- 회원 세션은 절대 만료(로그인 뒤 30일)가 반드시 있다. 회원을 지우면 그 회원의 세션도 지운다.
ALTER TABLE auth_sessions
    ADD COLUMN member_id VARCHAR(36) NULL AFTER session_hash,
    ADD CONSTRAINT fk_auth_sessions_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE,
    ADD INDEX idx_auth_sessions_member_last_used (member_id, last_used_at),
    ADD CONSTRAINT ck_auth_sessions_member_absolute CHECK (member_id IS NULL OR absolute_expires_at IS NOT NULL);

-- 같은 이메일의 로그인 잠금 상태. 이메일 원문 대신 정규화 이메일의 SHA-256 해시를 키로 쓴다.
-- locked_until이 NULL이거나 지났으면 잠기지 않은 상태다. 행은 INSERT ... ON DUPLICATE KEY UPDATE로 만들거나 배타 잠금한다.
CREATE TABLE auth_login_throttles (
    email_hash   CHAR(64) NOT NULL,
    locked_until DATETIME NULL,
    PRIMARY KEY (email_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 최근 15분의 로그인 실패 기록. 잠그거나 로그인에 성공하면 그 이메일의 기록을 지운다.
CREATE TABLE auth_login_failures (
    auth_login_failure_id BIGINT   NOT NULL AUTO_INCREMENT,
    email_hash            CHAR(64) NOT NULL,
    failed_at             DATETIME NOT NULL,
    PRIMARY KEY (auth_login_failure_id),
    INDEX idx_auth_login_failures_email_failed (email_hash, failed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

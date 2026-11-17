-- 회원 인증 서버 세션(D-24, docs/api/auth.md). MySQL에 저장하고 Redis는 쓰지 않는다.
-- U1에서는 CSRF 토큰 조회가 발급하는 익명 세션만 저장한다. 회원 세션 연결(회원 ID 열)은 로그인 단위에서 추가한다.
-- 세션 ID 원문은 저장하지 않고 SHA-256 해시(16진수 64자)만 저장한다. 시간 값은 Asia/Seoul 기준 시각이다.
-- 버전 번호 V9는 다른 작업선과 나눈 임시 번호다. 병합 직전 관리자가 순서를 정한다(D-23).
CREATE TABLE auth_sessions (
    auth_session_id     BIGINT   NOT NULL AUTO_INCREMENT,
    session_hash        CHAR(64) NOT NULL,
    created_at          DATETIME NOT NULL,
    last_used_at        DATETIME NOT NULL,
    idle_expires_at     DATETIME NOT NULL,
    absolute_expires_at DATETIME NOT NULL,
    PRIMARY KEY (auth_session_id),
    CONSTRAINT uk_auth_sessions_session_hash UNIQUE (session_hash),
    -- 미사용 만료는 절대 만료를 넘지 않는다.
    CONSTRAINT ck_auth_sessions_idle_within_absolute CHECK (idle_expires_at <= absolute_expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

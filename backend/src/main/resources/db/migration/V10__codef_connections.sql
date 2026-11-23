-- CODEF 계정 연결(docs/api/codef.md, D-09·D-14 원칙 9). 사용자당 한 행이고 user_id로 나눠 저장한다.
-- 행이 없으면 연결이 없는 것(NOT_CONNECTED)이다. connectedId는 응답·로그에 내보내지 않는다. 카드사 아이디·비밀번호는 저장하지 않는다.
-- connectedId는 AES-256-GCM 암호문(v1: 접두어 + Base64)으로만 저장한다(2026-11-18 사용자 직접 결정 P5). 키가 없으면 연결하지 않고
-- 평문으로 대신 저장하지 않는다. 연결 해제는 이 행을 지운다(P4). 시간 값은 Asia/Seoul 기준 시각이다.
-- 버전 번호 V10은 다른 작업선과 나눈 임시 번호다. 병합 직전 관리자가 순서를 정한다(D-23).
CREATE TABLE codef_connections (
    user_id        BIGINT       NOT NULL,
    organization   VARCHAR(8)   NOT NULL,
    connected_id_ciphertext VARCHAR(512) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    connected_at   DATETIME     NOT NULL,
    last_synced_at DATETIME     NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_codef_connections_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    -- MVP는 KB국민카드만 지원한다(docs/api/codef.md organization).
    CONSTRAINT ck_codef_connections_organization CHECK (organization = '0301'),
    -- NOT_CONNECTED는 행이 없는 것으로 나타내므로 저장하지 않는다.
    CONSTRAINT ck_codef_connections_ciphertext CHECK (connected_id_ciphertext LIKE 'v1:%'),
    CONSTRAINT ck_codef_connections_status CHECK (status IN ('CONNECTED', 'RECONNECT_REQUIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

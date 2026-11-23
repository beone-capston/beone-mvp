-- 시연 사용자(D-09). 사용자 데이터는 user_id로 나눠 저장한다.
-- 시드 값은 docs/api/cards.md의 GET /api/v1/demo-users 응답 예시에 있는 값만 쓴다.
CREATE TABLE users (
    user_id        BIGINT       NOT NULL AUTO_INCREMENT,
    demo_user      VARCHAR(32)  NOT NULL,
    display_name   VARCHAR(50)  NOT NULL,
    portfolio_type VARCHAR(16)  NOT NULL,
    read_only      BOOLEAN      NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_users_demo_user UNIQUE (demo_user)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO users (demo_user, display_name, portfolio_type, read_only) VALUES
    ('MY_CARDS', '내 카드', 'REAL', FALSE),
    ('VIRTUAL_PORTFOLIO', '가상 포트폴리오', 'SYNTHETIC', TRUE);

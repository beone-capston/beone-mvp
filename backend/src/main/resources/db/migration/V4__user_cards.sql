-- 보유 카드(docs/api/cards.md, D-15·D-19·D-23). 사용자 데이터는 user_id로 나눠 저장한다(D-09).
-- 카드 상품 테이블은 없다. card_product_id는 규칙 JSON의 상품 ID 문자열(선행 0 보존)이다(D-23 12항).
-- 열거 값은 MySQL ENUM이 아닌 VARCHAR에 이름으로 저장한다. 해제는 released_at을 채우고 행을 지우지 않는다.
-- 시간 값은 Asia/Seoul 기준 시각이다.
CREATE TABLE user_cards (
    user_card_id                 BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                      BIGINT      NOT NULL,
    display_name                 VARCHAR(30) NOT NULL,
    holding_source               VARCHAR(16) NOT NULL,
    card_product_id              VARCHAR(16) NULL,
    mapping_status               VARCHAR(16) NULL,
    confirmation_method          VARCHAR(32) NULL,
    confirmed_at                 DATETIME    NULL,
    excluded_from_recommendation BOOLEAN     NOT NULL,
    registered_at                DATETIME    NOT NULL,
    released_at                  DATETIME    NULL,
    -- 같은 사용자·같은 상품 중복 금지(D-23 2항): 해제하지 않은 USER_CONFIRMED·SYNTHETIC 카드만 상품 ID를 갖는다.
    -- MySQL 유니크 인덱스는 NULL을 여러 개 허용하므로 UNMAPPED·NOT_SUPPORTED·해제 카드는 검사하지 않는다.
    active_product_id            VARCHAR(16) GENERATED ALWAYS AS (
        CASE
            WHEN released_at IS NULL AND (mapping_status = 'USER_CONFIRMED' OR holding_source = 'SYNTHETIC')
                THEN card_product_id
        END
    ) VIRTUAL,
    PRIMARY KEY (user_card_id),
    KEY idx_user_cards_user_id (user_id),
    CONSTRAINT uk_user_cards_active_product UNIQUE (user_id, active_product_id),
    CONSTRAINT fk_user_cards_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    -- 가상 포트폴리오 카드는 매핑이 없고 상품 ID를 직접 갖는다(D-15). 그 밖의 카드는 매핑 상태가 있다.
    CONSTRAINT ck_user_cards_source_mapping CHECK (
        (holding_source = 'SYNTHETIC' AND mapping_status IS NULL AND card_product_id IS NOT NULL)
        OR (holding_source <> 'SYNTHETIC' AND mapping_status IS NOT NULL)
    ),
    -- 상품 ID는 USER_CONFIRMED와 SYNTHETIC만 갖는다. CANDIDATE의 cardProductId는 null이다(docs/api/cards.md).
    CONSTRAINT ck_user_cards_product_by_status CHECK (
        card_product_id IS NULL OR holding_source = 'SYNTHETIC' OR mapping_status = 'USER_CONFIRMED'
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 가상 포트폴리오 시드: 규칙 카드 4종 전부(결정 27 A2-1, 사용자 결정). 표시 이름은 카드 상품 조회의 cardName과 같다
-- (SupportedCardProducts, docs/card-benefit-research.md 1절). 등록 시각은 이 마이그레이션을 적용한 서울 시각이다.
INSERT INTO user_cards (user_id, display_name, holding_source, card_product_id, mapping_status, confirmation_method,
                        confirmed_at, excluded_from_recommendation, registered_at)
SELECT u.user_id, seed.display_name, 'SYNTHETIC', seed.card_product_id, NULL, NULL, NULL, FALSE,
       CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00')
FROM users u
         JOIN (SELECT 1 AS seq, '01664' AS card_product_id, '노리 체크카드' AS display_name
               UNION ALL SELECT 2, '01914', '첵첵 체크카드'
               UNION ALL SELECT 3, '09174', '청춘대로 톡톡카드'
               UNION ALL SELECT 4, '09271', '올라운드 티타늄카드') seed
WHERE u.demo_user = 'VIRTUAL_PORTFOLIO'
ORDER BY seed.seq;

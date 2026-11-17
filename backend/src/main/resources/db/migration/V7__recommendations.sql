-- 추천 계산 결과 저장(docs/api/recommendations.md, D-14 원칙 5). 사용자 데이터는 user_id로 나눠 저장한다(D-09).
-- 조회는 다시 계산하지 않고 계산 때의 응답 본문(document_json)을 그대로 돌려준다. 시간 값은 Asia/Seoul 기준 시각이다.
CREATE TABLE recommendations (
    recommendation_id BIGINT     NOT NULL AUTO_INCREMENT,
    user_id           BIGINT     NOT NULL,
    order_id          BIGINT     NOT NULL,
    order_month       CHAR(7)    NOT NULL,
    calculated_at     DATETIME   NOT NULL,
    -- 본문에 추천 ID가 들어가므로 행을 넣어 번호를 받은 뒤 같은 트랜잭션에서 채운다. 커밋된 행은 항상 값이 있다.
    document_json     MEDIUMTEXT NULL,
    PRIMARY KEY (recommendation_id),
    KEY idx_recommendations_user_id (user_id),
    KEY idx_recommendations_order_id (order_id),
    CONSTRAINT fk_recommendations_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_recommendations_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 계산 때 엔진에 넘긴 보유 카드의 비교 대상 여부와 월간 상태 스냅샷 시각. 조회 때 현재 값과 비교해 무효화를 판정한다(결정 27 B2-4).
-- 가상 포트폴리오 카드는 매핑 대상이 아니라 mapping_status가 NULL이다. 월간 상태를 입력하지 않은 카드는 monthly_snapshot_at이 NULL이다.
CREATE TABLE recommendation_card_bases (
    card_basis_id         BIGINT      NOT NULL AUTO_INCREMENT,
    recommendation_id     BIGINT      NOT NULL,
    user_card_id          BIGINT      NOT NULL,
    mapping_status        VARCHAR(16) NULL,
    rule_card_product_id  VARCHAR(16) NULL,
    excluded_by_user      BOOLEAN     NOT NULL,
    monthly_snapshot_at   DATETIME    NULL,
    PRIMARY KEY (card_basis_id),
    CONSTRAINT uk_recommendation_card UNIQUE (recommendation_id, user_card_id),
    CONSTRAINT fk_card_bases_recommendation FOREIGN KEY (recommendation_id)
        REFERENCES recommendations (recommendation_id) ON DELETE CASCADE,
    CONSTRAINT fk_card_bases_user_card FOREIGN KEY (user_card_id) REFERENCES user_cards (user_card_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 주문과 예정 소비(docs/api/orders.md). 사용자 데이터는 user_id로 나눠 저장한다(D-09).
-- 시드는 넣지 않는다. 열거 값은 MySQL ENUM이 아닌 VARCHAR에 이름으로 저장한다.
CREATE TABLE orders (
    order_id       BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    amount_won     BIGINT      NOT NULL,
    order_date     DATE        NOT NULL,
    order_time     TIME        NULL,
    merchant_name  VARCHAR(50) NULL,
    merchant_class VARCHAR(64) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    created_at     DATETIME    NOT NULL,
    PRIMARY KEY (order_id),
    KEY idx_orders_user_id (user_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT ck_orders_amount_won CHECK (amount_won >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- PUT으로 통째로 교체한다. planned_spend_id 오름차순이 요청 items 순서다(결정 27).
CREATE TABLE planned_spends (
    planned_spend_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    planned_date     DATE        NOT NULL,
    planned_time     TIME        NULL,
    amount_won       BIGINT      NOT NULL,
    merchant_class   VARCHAR(64) NOT NULL,
    payment_method   VARCHAR(32) NOT NULL,
    merchant_name    VARCHAR(50) NULL,
    PRIMARY KEY (planned_spend_id),
    KEY idx_planned_spends_user_date (user_id, planned_date),
    CONSTRAINT fk_planned_spends_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT ck_planned_spends_amount_won CHECK (amount_won >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

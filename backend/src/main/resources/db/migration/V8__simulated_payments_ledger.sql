-- 모의 승인·취소와 자체 모의 원장, 멱등 키(docs/api/mock-payments.md, docs/api/ledger.md, docs/api.md 중복 방지).
-- 사용자 데이터는 user_id로 나눠 저장한다(D-09). 시간 값은 Asia/Seoul 기준 시각이다. 열거 값은 VARCHAR에 이름으로 저장한다.
-- 실제 카드 결제가 아니며 카드번호·승인번호 같은 실물 결제 정보는 두지 않는다.

-- 주문의 모의 승인 결제 ID(pay-…). 승인 전·실패만 있으면 NULL이다. 주문 상태 PAID·CANCELLED도 이 작업에서 생긴다.
ALTER TABLE orders ADD COLUMN simulated_payment_id VARCHAR(32) NULL;

-- 모의 결제 한 건. FAILED도 저장한다(D-23-4). 응답 중 승인 때 정해진 부분은 document_json에 그대로 두고,
-- 상태·취소 합계는 열로 바꿔 가며 조회 때 합친다. 주문·보유 카드 행을 지우면(테스트 정리) 함께 지워진다.
CREATE TABLE simulated_payments (
    simulated_payment_id     BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                  BIGINT        NOT NULL,
    order_id                 BIGINT        NOT NULL,
    user_card_id             BIGINT        NOT NULL,
    card_product_id          VARCHAR(16)   NULL,
    status                   VARCHAR(24)   NOT NULL,
    amount_won               BIGINT        NOT NULL,
    cancelled_won            BIGINT        NOT NULL,
    approved_at              DATETIME      NOT NULL,
    approval_ledger_id       BIGINT        NULL,
    -- 승인 카드에서 혜택 기여가 확정 0원이 아닌 서비스 ID의 JSON 배열. 취소 영향(BENEFIT)을 만들 때 쓴다.
    benefit_service_ids      VARCHAR(1024) NOT NULL,
    -- 본문에 결제 ID가 들어가므로 행을 넣어 번호를 받은 뒤 같은 트랜잭션에서 채운다. 커밋된 행은 항상 값이 있다(V7과 같은 방식).
    document_json            MEDIUMTEXT    NULL,
    PRIMARY KEY (simulated_payment_id),
    KEY idx_simulated_payments_user_id (user_id),
    KEY idx_simulated_payments_order_id (order_id),
    CONSTRAINT fk_simulated_payments_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_simulated_payments_order FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE,
    CONSTRAINT fk_simulated_payments_user_card FOREIGN KEY (user_card_id)
        REFERENCES user_cards (user_card_id) ON DELETE CASCADE,
    CONSTRAINT ck_simulated_payments_amount_won CHECK (amount_won >= 1),
    CONSTRAINT ck_simulated_payments_cancelled_won CHECK (cancelled_won >= 0 AND cancelled_won <= amount_won)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 모의 취소 한 건(부분취소 여러 번 가능, D-17). 원장 취소 행 ID를 함께 둔다.
CREATE TABLE payment_cancellations (
    cancellation_id      BIGINT   NOT NULL AUTO_INCREMENT,
    simulated_payment_id BIGINT   NOT NULL,
    amount_won           BIGINT   NOT NULL,
    cancelled_at         DATETIME NOT NULL,
    ledger_id            BIGINT   NOT NULL,
    PRIMARY KEY (cancellation_id),
    KEY idx_payment_cancellations_payment (simulated_payment_id),
    CONSTRAINT fk_payment_cancellations_payment FOREIGN KEY (simulated_payment_id)
        REFERENCES simulated_payments (simulated_payment_id) ON DELETE CASCADE,
    CONSTRAINT ck_payment_cancellations_amount_won CHECK (amount_won >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 자체 모의 원장(source = SIMULATION). 2차 CODEF 원장 행은 출처를 구분해 같은 형식으로 더한다(D-16·D-17).
-- 승인은 양수, 취소는 음수이며 행 종류는 entry_type으로 판단한다(#45). 원장에서 월간 상태를 자동 산출하지 않는다(D-16).
CREATE TABLE ledger_transactions (
    ledger_id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT      NOT NULL,
    user_card_id            BIGINT      NOT NULL,
    source                  VARCHAR(16) NOT NULL,
    entry_type              VARCHAR(16) NOT NULL,
    amount_won              BIGINT      NOT NULL,
    transaction_date        DATE        NOT NULL,
    transaction_time        TIME        NULL,
    merchant_class          VARCHAR(64) NOT NULL,
    payment_method          VARCHAR(32) NOT NULL,
    transaction_nature      VARCHAR(16) NULL,
    merchant_name           VARCHAR(50) NULL,
    original_ledger_id      BIGINT      NULL,
    simulated_payment_id    BIGINT      NULL,
    attribution_month       CHAR(7)     NOT NULL,
    attribution_status      VARCHAR(24) NOT NULL,
    recorded_at             DATETIME    NOT NULL,
    PRIMARY KEY (ledger_id),
    KEY idx_ledger_transactions_user_date (user_id, transaction_date),
    KEY idx_ledger_transactions_payment (simulated_payment_id),
    CONSTRAINT fk_ledger_transactions_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_ledger_transactions_user_card FOREIGN KEY (user_card_id)
        REFERENCES user_cards (user_card_id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_transactions_payment FOREIGN KEY (simulated_payment_id)
        REFERENCES simulated_payments (simulated_payment_id) ON DELETE CASCADE,
    CONSTRAINT ck_ledger_transactions_sign CHECK ((entry_type = 'APPROVAL' AND amount_won >= 1)
        OR (entry_type = 'CANCELLATION' AND amount_won <= -1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 승인 행이 한도 버킷마다 더하는 사용액(엔진 BucketUsageEffect, D-18 K1). 모르면 deduction_won이 NULL이다(0으로 저장하지 않음).
-- 스냅샷 뒤 사용액 합산(B2f)의 원본이다. 이 작업은 저장만 한다.
CREATE TABLE ledger_usage_effects (
    usage_effect_id BIGINT      NOT NULL AUTO_INCREMENT,
    ledger_id       BIGINT      NOT NULL,
    limit_bucket_id VARCHAR(64) NOT NULL,
    deduction_won   BIGINT      NULL,
    PRIMARY KEY (usage_effect_id),
    CONSTRAINT uk_ledger_usage_effect UNIQUE (ledger_id, limit_bucket_id),
    CONSTRAINT fk_ledger_usage_effects_ledger FOREIGN KEY (ledger_id)
        REFERENCES ledger_transactions (ledger_id) ON DELETE CASCADE,
    CONSTRAINT ck_ledger_usage_effects_deduction CHECK (deduction_won IS NULL OR deduction_won >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 모의 승인·취소의 Idempotency-Key. 사용자별로 저장하고 24시간 동안 같은 지문의 결과를 돌려준다(docs/api.md 중복 방지).
-- 지문은 사용자·메서드·경로·본문의 SHA-256 16진수다. 성공(201) 응답 본문만 저장한다.
CREATE TABLE idempotency_keys (
    idempotency_key_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    idempotency_key    VARCHAR(255) NOT NULL,
    fingerprint        CHAR(64)     NOT NULL,
    response_json      MEDIUMTEXT   NOT NULL,
    created_at         DATETIME     NOT NULL,
    PRIMARY KEY (idempotency_key_id),
    CONSTRAINT uk_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_keys_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 카드 월간 상태 수동 입력(docs/api/monthly-states.md, D-16·D-17·D-18 K1). 사용자 데이터는 user_id로 나눠 저장한다(D-09).
-- (보유 카드, 월)마다 한 행이며 PUT이 그 달의 입력 전체를 교체한다. 입력하지 않은 값은 NULL(응답 UNKNOWN)이고 0과 구분한다.
-- 기준 기간이 카드의 실적·한도 기간과 맞는지는 저장할 때 판단하지 않는다(D-22). 시간 값은 Asia/Seoul 기준 시각이다.
-- 가상 포트폴리오 월간 상태 시드는 넣지 않는다(결정 27 A3).
CREATE TABLE monthly_states (
    monthly_state_id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                     BIGINT      NOT NULL,
    user_card_id                BIGINT      NOT NULL,
    state_month                 CHAR(7)     NOT NULL,
    -- 저장할 때의 규칙 상품 ID. 매핑이 바뀌면 버킷 사용액·택1을 미입력으로 보여 주는 기준이다(결정 27 A3-7).
    card_product_id             VARCHAR(16) NULL,
    snapshot_at                 DATETIME    NOT NULL,
    previous_month_won          BIGINT      NULL,
    previous_month_from         DATE        NULL,
    previous_month_to           DATE        NULL,
    other_buckets_used_won      BIGINT      NULL,
    other_buckets_from          DATE        NULL,
    other_buckets_to            DATE        NULL,
    registered_on               DATE        NULL,
    no_service_selection        BOOLEAN     NOT NULL,
    no_selection_effective_from DATE        NULL,
    PRIMARY KEY (monthly_state_id),
    CONSTRAINT uk_monthly_states_card_month UNIQUE (user_card_id, state_month),
    KEY idx_monthly_states_user_id (user_id),
    CONSTRAINT fk_monthly_states_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_monthly_states_user_card FOREIGN KEY (user_card_id) REFERENCES user_cards (user_card_id),
    -- 금액과 기준 기간은 함께 있거나 함께 없다. 전월 실적은 음수를 받는다(D-17).
    CONSTRAINT ck_monthly_states_previous_month CHECK (
        (previous_month_won IS NULL AND previous_month_from IS NULL AND previous_month_to IS NULL)
        OR (previous_month_won IS NOT NULL AND previous_month_from IS NOT NULL AND previous_month_to IS NOT NULL
            AND previous_month_from <= previous_month_to)
    ),
    CONSTRAINT ck_monthly_states_other_buckets CHECK (
        (other_buckets_used_won IS NULL AND other_buckets_from IS NULL AND other_buckets_to IS NULL)
        OR (other_buckets_used_won >= 0 AND other_buckets_from IS NOT NULL AND other_buckets_to IS NOT NULL
            AND other_buckets_from <= other_buckets_to)
    ),
    CONSTRAINT ck_monthly_states_no_selection CHECK (
        no_service_selection OR no_selection_effective_from IS NULL
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 한도 묶음별 사용액. seq는 요청 순서다(결정 27 A3-6).
CREATE TABLE monthly_state_bucket_usages (
    bucket_usage_id  BIGINT      NOT NULL AUTO_INCREMENT,
    monthly_state_id BIGINT      NOT NULL,
    seq              INT         NOT NULL,
    limit_bucket_id  VARCHAR(64) NOT NULL,
    used_won         BIGINT      NOT NULL,
    period_from      DATE        NOT NULL,
    period_to        DATE        NOT NULL,
    PRIMARY KEY (bucket_usage_id),
    CONSTRAINT uk_monthly_state_bucket UNIQUE (monthly_state_id, limit_bucket_id),
    CONSTRAINT fk_bucket_usages_state FOREIGN KEY (monthly_state_id) REFERENCES monthly_states (monthly_state_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_bucket_usages_value CHECK (used_won >= 0 AND period_from <= period_to)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 고른 택1 서비스. 적용일이 없으면 NULL(응답 usable=false). seq는 요청 순서다.
CREATE TABLE monthly_state_service_selections (
    service_selection_id BIGINT      NOT NULL AUTO_INCREMENT,
    monthly_state_id     BIGINT      NOT NULL,
    seq                  INT         NOT NULL,
    service_id           VARCHAR(64) NOT NULL,
    effective_from       DATE        NULL,
    PRIMARY KEY (service_selection_id),
    CONSTRAINT uk_monthly_state_service UNIQUE (monthly_state_id, service_id),
    CONSTRAINT fk_service_selections_state FOREIGN KEY (monthly_state_id) REFERENCES monthly_states (monthly_state_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

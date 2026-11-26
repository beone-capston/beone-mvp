-- 전월 실적을 CODEF 실적 참고값을 보고 확인·입력했을 때 참고한 값(D-16, 사용자 결정 2026-11-27, 이슈 #281).
-- 값은 사용자가 정한 previous_month_won(출처 MANUAL)이고, 아래 열은 그때 참고한 CODEF 조회값의 스냅샷이다. 계산에 쓰지 않는다.
-- previous_month_codef_fetched_at이 있으면 참고 입력이다. 참고값 행이 지워져도 스냅샷은 남도록 참고값 ID만 NULL로 바꾼다.
-- previous_month_codef_won은 참고값 금액을 해석하지 못했으면 NULL이다(0으로 바꾸지 않음).
ALTER TABLE monthly_states
    ADD COLUMN previous_month_codef_reference_id BIGINT   NULL AFTER previous_month_to,
    ADD COLUMN previous_month_codef_sync_id      BIGINT   NULL AFTER previous_month_codef_reference_id,
    ADD COLUMN previous_month_codef_won          BIGINT   NULL AFTER previous_month_codef_sync_id,
    ADD COLUMN previous_month_codef_from         DATE     NULL AFTER previous_month_codef_won,
    ADD COLUMN previous_month_codef_to           DATE     NULL AFTER previous_month_codef_from,
    ADD COLUMN previous_month_codef_fetched_at   DATETIME NULL AFTER previous_month_codef_to,
    ADD CONSTRAINT fk_monthly_states_codef_reference FOREIGN KEY (previous_month_codef_reference_id)
        REFERENCES codef_performance_references (codef_performance_reference_id) ON DELETE SET NULL,
    -- 참고 스냅샷은 전월 실적 값이 있을 때만 있고, 있으면 동기화 ID·기준 기간을 함께 갖는다.
    ADD CONSTRAINT ck_monthly_states_codef_snapshot CHECK (
        (previous_month_codef_fetched_at IS NULL AND previous_month_codef_sync_id IS NULL
            AND previous_month_codef_won IS NULL AND previous_month_codef_from IS NULL
            AND previous_month_codef_to IS NULL)
        OR (previous_month_codef_fetched_at IS NOT NULL AND previous_month_won IS NOT NULL
            AND previous_month_codef_sync_id IS NOT NULL AND previous_month_codef_from IS NOT NULL
            AND previous_month_codef_to IS NOT NULL AND previous_month_codef_from <= previous_month_codef_to)
    );

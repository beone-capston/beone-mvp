-- CODEF 승인내역의 기간 완전성과 승인 합계 추정값(INFERRED) 참고 기록(사용자 결정 2026-11-27 T4, 이슈 #289, D-16·D-17).
-- 금액·승인번호·카드번호 원문은 새 열·표에 두지 않는다. 불완전 행은 여전히 저장하지 않고 카드·월·사유별 건수만 남긴다.

-- 승인내역 조회·판정을 마쳤는지. TRUE면 그 동기화 기간의 승인 행을 판정했다(불완전 행은 아래 표에 건수로 남음).
-- FALSE는 승인 조회 실패(부분 실패, HMAC 키 없음 포함). NULL은 V18 전 동기화라 알 수 없다(완전성 판단에 쓰지 않음).
ALTER TABLE codef_syncs
    ADD COLUMN approvals_judged BOOLEAN NULL AFTER synced_at;

-- 동기화 한 번에서 저장하지 않은 승인 행(불완전 행)의 카드·사용월·사유별 건수.
-- user_card_id가 NULL이면 보유 카드에 붙지 않은 행이고, used_month가 NULL이면 사용일자를 해석하지 못해 그 동기화 기간 전체에 걸린다.
CREATE TABLE codef_sync_incomplete_marks (
    codef_sync_incomplete_mark_id BIGINT      NOT NULL AUTO_INCREMENT,
    sync_id                       BIGINT      NOT NULL,
    user_id                       BIGINT      NOT NULL,
    user_card_id                  BIGINT      NULL,
    used_month                    CHAR(7)     NULL,
    reason                        VARCHAR(40) NOT NULL,
    row_count                     INT         NOT NULL,
    PRIMARY KEY (codef_sync_incomplete_mark_id),
    KEY idx_codef_sync_incomplete_marks_user (user_id, sync_id),
    CONSTRAINT fk_codef_sync_incomplete_marks_sync FOREIGN KEY (sync_id) REFERENCES codef_syncs (sync_id),
    CONSTRAINT fk_codef_sync_incomplete_marks_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_codef_sync_incomplete_marks_user_card FOREIGN KEY (user_card_id)
        REFERENCES user_cards (user_card_id),
    CONSTRAINT ck_codef_sync_incomplete_marks_month CHECK (
        used_month IS NULL OR REGEXP_LIKE(used_month, '^[0-9]{4}-(0[1-9]|1[0-2])$', 'c')),
    CONSTRAINT ck_codef_sync_incomplete_marks_reason CHECK (reason IN ('PARTIAL_CANCELLATION_SUSPECTED',
        'NO_MATCHING_APPROVAL', 'MULTIPLE_MATCHING_APPROVALS', 'UNDETERMINED_ROW', 'DUPLICATE_KEY_AMBIGUOUS')),
    CONSTRAINT ck_codef_sync_incomplete_marks_count CHECK (row_count > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 전월 실적을 CODEF 승인 합계 추정값을 보고 확인·입력했을 때 참고한 값. 값은 previous_month_won(출처 MANUAL)이고 계산에 쓰지 않는다.
-- 동기화 ID는 산출 근거로 남기는 값이라 외래키를 두지 않는다(동기화 행이 없어져도 기록은 남는다).
ALTER TABLE monthly_states
    ADD COLUMN previous_month_inferred_won         BIGINT   NULL AFTER previous_month_codef_fetched_at,
    ADD COLUMN previous_month_inferred_sync_id     BIGINT   NULL AFTER previous_month_inferred_won,
    ADD COLUMN previous_month_inferred_computed_at DATETIME NULL AFTER previous_month_inferred_sync_id,
    ADD CONSTRAINT ck_monthly_states_inferred_snapshot CHECK (
        (previous_month_inferred_won IS NULL AND previous_month_inferred_sync_id IS NULL
            AND previous_month_inferred_computed_at IS NULL)
        OR (previous_month_inferred_won IS NOT NULL AND previous_month_inferred_sync_id IS NOT NULL
            AND previous_month_inferred_computed_at IS NOT NULL AND previous_month_won IS NOT NULL)
    );

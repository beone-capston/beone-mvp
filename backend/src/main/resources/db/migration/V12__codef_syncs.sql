-- CODEF 조회·동기화(docs/api/codef.md POST /api/v1/me/codef/syncs, D-09·D-15·D-16·D-17). 사용자 데이터는 user_id로 나눠 저장한다.
-- CODEF 승인 행은 이 codef 전용 테이블에만 둔다. ledger_transactions와 원장 조회는 바꾸지 않는다(결정 22 R8, 관리자 판단 K2).
-- 승인번호 원문은 저장하지 않고 HMAC 지문만 둔다(결정 20 R2, 2026-11-18 사용자 직접 결정 P6). 불완전 행은 저장하지 않으며 재조회 때 다시 판정한다.
-- 카드명은 CODEF 응답 문자열 그대로 비교하므로(결정 R1) 대소문자·악센트를 구분하는 utf8mb4_bin으로 둔다. 시간 값은 Asia/Seoul 기준 시각이다.
-- 버전 번호 V12는 다른 작업선과 나눈 임시 번호다(V11은 인증 U2 예정). 병합 직전 관리자가 순서를 정한다(D-23).

-- 성공(200)한 동기화 한 번. 외부 표현은 sync- 접두어 + 번호다.
CREATE TABLE codef_syncs (
    sync_id     BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    period_from DATE     NOT NULL,
    period_to   DATE     NOT NULL,
    synced_at   DATETIME NOT NULL,
    PRIMARY KEY (sync_id),
    KEY idx_codef_syncs_user_id (user_id),
    CONSTRAINT fk_codef_syncs_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT ck_codef_syncs_period CHECK (period_from <= period_to)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- CODEF 보유카드로 만든 보유 카드(user_cards, holding_source = CODEF)의 대조 정보(관리자 판단 P8·Q1).
-- 재동기화 때 같은 사용자·같은 카드명·같은 발급일자(둘 다 없음도 같은 값)인 해제하지 않은 카드가 정확히 하나면 같은 카드로 본다.
CREATE TABLE codef_held_cards (
    user_card_id  BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    card_name     VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    issue_date    VARCHAR(64)  COLLATE utf8mb4_bin NULL,
    first_sync_id BIGINT       NOT NULL,
    PRIMARY KEY (user_card_id),
    KEY idx_codef_held_cards_user_name (user_id, card_name),
    CONSTRAINT fk_codef_held_cards_user_card FOREIGN KEY (user_card_id) REFERENCES user_cards (user_card_id),
    CONSTRAINT fk_codef_held_cards_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_codef_held_cards_sync FOREIGN KEY (first_sync_id) REFERENCES codef_syncs (sync_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 받아들인 CODEF 승인 행(D-17 형태: 정상, Type1 전액 취소, 짝지은 Type2 취소). 중복 판단 키에 유니크 제약을 건다(결정 20 R2).
-- 취소를 담은 행의 귀속월은 확인 필요이며 저장하지 않는다. 원장 완전 선언은 하지 않는다(결정 22 R7).
CREATE TABLE codef_approval_rows (
    codef_approval_row_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    user_card_id          BIGINT       NOT NULL,
    sync_id               BIGINT       NOT NULL,
    card_name             VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    approval_fingerprint  VARCHAR(64)  COLLATE utf8mb4_bin NOT NULL,
    used_date             DATE         NOT NULL,
    used_time             TIME         NOT NULL,
    signed_amount         BIGINT       NOT NULL,
    row_kind              VARCHAR(32)  NOT NULL,
    recorded_at           DATETIME     NOT NULL,
    kind_changed_at       DATETIME     NULL,
    PRIMARY KEY (codef_approval_row_id),
    CONSTRAINT uk_codef_approval_rows_key
        UNIQUE (user_id, card_name, approval_fingerprint, used_date, used_time, signed_amount),
    CONSTRAINT fk_codef_approval_rows_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_codef_approval_rows_user_card FOREIGN KEY (user_card_id) REFERENCES user_cards (user_card_id),
    CONSTRAINT fk_codef_approval_rows_sync FOREIGN KEY (sync_id) REFERENCES codef_syncs (sync_id),
    -- HMAC-SHA256 지문의 소문자 16진수 표현만 받는다. 승인번호 원문이 들어가지 않게 막는 마지막 방어선이다.
    CONSTRAINT ck_codef_approval_rows_fingerprint CHECK (REGEXP_LIKE(approval_fingerprint, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT ck_codef_approval_rows_kind CHECK (row_kind IN ('APPROVAL', 'APPROVAL_FULLY_CANCELED', 'CANCELLATION'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 카드사 조회 실적(참고, D-16). 계산에 쓰지 않고 monthly_states에 넣지 않는다. 동기화마다 이력으로 쌓는다.
-- 확인하지 못한 기간·금액은 NULL이며 0원·빈 기간으로 바꾸지 않는다. 보유 카드에 붙지 않은 실적은 user_card_id가 NULL이다.
CREATE TABLE codef_performance_references (
    codef_performance_reference_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                        BIGINT       NOT NULL,
    user_card_id                   BIGINT       NULL,
    sync_id                        BIGINT       NOT NULL,
    card_name                      VARCHAR(255) COLLATE utf8mb4_bin NULL,
    period_from                    DATE         NULL,
    period_to                      DATE         NULL,
    current_use_won                BIGINT       NULL,
    fetched_at                     DATETIME     NOT NULL,
    PRIMARY KEY (codef_performance_reference_id),
    KEY idx_codef_performance_references_user_id (user_id),
    CONSTRAINT fk_codef_performance_references_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_codef_performance_references_user_card FOREIGN KEY (user_card_id)
        REFERENCES user_cards (user_card_id),
    CONSTRAINT fk_codef_performance_references_sync FOREIGN KEY (sync_id) REFERENCES codef_syncs (sync_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

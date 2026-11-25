-- 결제 전 설정(docs/api/preferences.md, D-12·D-15·D-23). 사용자당 한 행이며 user_id로 나눠 저장한다(D-09).
-- 행이 없으면 설정한 적이 없는 것이다. 조회 API는 저장된 기본값이 아니라 미설정 표시(AUTO_RECOMMEND, 대표 카드 null,
-- updatedAt null)를 돌려준다. 열거 값은 MySQL ENUM이 아닌 VARCHAR에 이름으로 저장한다. 시간 값은 Asia/Seoul 기준 시각이다.
-- 버전 번호 V6은 다른 작업선과 나눈 임시 번호다. 병합 직전 관리자가 순서를 정한다(D-23).
CREATE TABLE payment_preferences (
    user_id                     BIGINT      NOT NULL,
    selection_mode              VARCHAR(32) NOT NULL,
    representative_user_card_id BIGINT      NULL,
    updated_at                  DATETIME    NOT NULL,
    PRIMARY KEY (user_id),
    KEY idx_payment_preferences_representative (representative_user_card_id),
    CONSTRAINT fk_payment_preferences_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    -- 대표 카드가 같은 사용자의 해제하지 않은 적격 카드인지는 서비스가 확인한다. DB는 존재하는 카드만 막는다.
    CONSTRAINT fk_payment_preferences_representative FOREIGN KEY (representative_user_card_id)
        REFERENCES user_cards (user_card_id),
    CONSTRAINT ck_payment_preferences_mode CHECK (selection_mode IN ('AUTO_RECOMMEND', 'FIXED_REPRESENTATIVE')),
    -- 대표 카드 고정 모드에는 대표 카드가 있어야 한다(docs/api/preferences.md 조합 규칙).
    CONSTRAINT ck_payment_preferences_fixed_card CHECK (
        selection_mode <> 'FIXED_REPRESENTATIVE' OR representative_user_card_id IS NOT NULL
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

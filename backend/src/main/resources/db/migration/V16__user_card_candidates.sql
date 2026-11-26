-- CODEF 카드명 사전 후보(D-15 2026-11-26 변경, 이슈 #268).
-- candidate_card_product_id: CODEF 카드명이 이름 사전과 정확히 일치할 때 서버가 채우는 후보 상품 ID. CANDIDATE에서만 값이 있다.
--   후보는 확정이 아니므로 card_product_id·active_product_id(같은 상품 중복 금지)에 들어가지 않는다.
-- mapping_changed_by_user: 사용자가 매핑 변경 API로 매핑을 한 번이라도 바꿨는지. 재동기화가 사용자가 고른 UNMAPPED를
--   후보로 되돌리지 않게 한다. 이 열이 생기기 전의 변경 이력은 알 수 없으므로 기존 행은 FALSE다.
ALTER TABLE user_cards
    ADD COLUMN candidate_card_product_id VARCHAR(16) NULL AFTER confirmed_at,
    ADD COLUMN mapping_changed_by_user   BOOLEAN     NOT NULL DEFAULT FALSE AFTER candidate_card_product_id,
    -- 후보 상품은 CODEF 카드의 CANDIDATE만 갖고, CANDIDATE는 반드시 후보 상품을 갖는다(docs/api/cards.md).
    ADD CONSTRAINT ck_user_cards_candidate CHECK (
        (candidate_card_product_id IS NULL AND (mapping_status IS NULL OR mapping_status <> 'CANDIDATE'))
        OR (candidate_card_product_id IS NOT NULL AND mapping_status = 'CANDIDATE' AND holding_source = 'CODEF')
    );

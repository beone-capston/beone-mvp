-- CODEF 호출 총량(LP-2, N14, 결정 10·39-4). 비공개 검증 기간 창마다 API별 누적 호출 수를 둔다.
-- 서버를 다시 띄워도 누적이 남도록 DB에 두며, 사용자 데이터·원장과 섞지 않는다(R8). 값은 시도 수이며 실패해도 되돌리지 않는다.
-- window_start는 설정한 창 시작 시각(UTC)이다. 창을 새로 정하면 새 행으로 다시 센다.
CREATE TABLE codef_call_budgets (
    api          VARCHAR(32) NOT NULL,
    window_start DATETIME(3) NOT NULL,
    used         INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (api, window_start),
    CONSTRAINT ck_codef_call_budgets_used CHECK (used >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

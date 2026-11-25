# #141 업무·인증 API 구현 상태 문구 정합(S1A R1~R8)

브랜치 `docs/141-api-status-sync`, 시작 기준 `origin/develop` `7c192d2`.

## 2026-11-18 · 구현 상태 문구 정합 · PR(`develop` 대상, 병합 전)
- 근거: 관리측 1단계 계약 감사(S1A) 문서 변경안 R1~R8. 병합 기록 #103~#140과 코드(`@Profile`, 인증 오류 코드, `LedgerQueryService`)를 대조해 확정된 사실만 적었습니다. 정책 변경은 없습니다.
- 한 일:
  - [`docs/api/README.md`](../../api/README.md): 상태 항목을 "28개 로컬·테스트 범위 구현, 배포 전, CODEF 가짜 게이트웨이, `source-selections` 제한 구현(항상 409·저장 없음)"으로, 엔진 입구 항목을 "추천·모의 승인이 부름"으로, 인증 9개를 "모두 구현, 가짜 메일, 회원 세션과 업무 API 미연결(D-24)"로 바꿨습니다.
  - `docs/api/` 개별 문서 9개 5행의 "모든 API는 구현 전입니다"를 README 상태 항목 안내로 바꿨습니다.
  - [`docs/api.md`](../../api.md): 구현된 인증 코드 6개와 `FORBIDDEN` 미사용, 업무 API 공통 규약 머리말, 「구현 범위」의 업무 API·기능별 오류 코드를 '구현됨'으로 옮겼습니다.
  - [`docs/domains/ledger.md`](../../domains/ledger.md): `CODEF` 완전성 설명을 #131 뒤의 R8 미연결 상태로 정정했습니다.
- 하지 않은 일(정책 결정 뒤 별도): 추천 무효화 사유 설명(`recommendations.md`), 재계산 비교 기준(`mock-payments.md`), 원장 `coverage` 설명(`ledger.md` 응답 필드), CODEF 연결 상태 조회의 가상 포트폴리오 문구(R9).
- 코드·테스트·설정 변경 없음.

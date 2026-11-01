# #43 CODEF 상품 응답 전송 단계 해석

브랜치 `feat/43-codef-product-transport`, 시작 기준 `origin/develop` `d9ae633`.

## 2026-11-01 · 전송 단계 해석 · 완료(PR 전)
- 한 일: `codef` 도메인에 `CodefProductResponseReader`와 `CodefProductResponse`를 추가했습니다. HTTP 상태 코드와 응답 본문을 받아 200은 UTF-8 URL 디코딩 뒤 기존 `CodefCardResponseParser`로 해석하고, 401은 인증 오류, 그 밖은 서버·전송 오류로 나눕니다. 기존 해석기는 바꾸지 않았습니다.
- 근거: 공식 SDK `codef-io/easycodef-java-v2`(MIT) 커밋 `721f5661a52da3bcee8f24c86c04557e1e073e9a`의 `ResponseHandler`·`EasyCodefConnector`. 공식 SDK 구현에서 확인한 동작이며, 공식 명세 문서가 아닙니다.
- 서비스에서 달라진 점: 실제 HTTP 클라이언트가 붙으면 받은 상태와 본문을 이 단계에 넘겨 인증 오류·서버 오류·본문 해석 결과를 구분할 수 있습니다. 실제 호출·토큰·재시도는 아직 없습니다.
- 지키는 조건: HTTP 상태 구분과 결과 코드 분류를 섞지 않습니다. 200이 아니면 본문을 읽지 않으며, 원문을 결과·예외에 남기지 않습니다. 잘못된 퍼센트 인코딩은 형식 오류이고, `+`가 공백이 되는 동작은 SDK와 같게 고정했습니다.
- 결정 기록: SDK는 401·그 밖의 상태에서 본문을 예외에 싣고 잘못된 퍼센트 인코딩에서 처리되지 않은 예외를 내지만, 여기서는 본문을 버리고 형식 오류로 돌려줍니다. 100~599 밖의 상태와 null 본문은 호출 오류로 거부합니다.
- 검증: `./gradlew test`·`webTest`·`integrationTest`·`build`(각 `--rerun-tasks`) 결과는 PR에 기록합니다. 신규 테스트는 합성 응답이며 실제 응답 검증이 아닙니다.
- 남은 제한: Content-Type·시간 초과·재시도, `CF-12872` 의미, 실제 호출·토큰(키 필요), 요청 쪽(금지 입력 포함), D-04 상태값 변환, 기관별 필수조건, 취소 상세, 오류 전체 목록.

## 2026-11-01 · `develop` 병합 · 완료
- PR #44 병합, 병합 커밋 `2c1fe48`.

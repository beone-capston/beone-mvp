# TEMP-WEBVIEW-FIXTURE Android WebView 임시 관측 화면

기존 debug APK의 시작 주소 `http://localhost:5173/`에 올릴 **임시 관측 전용** 화면입니다. 이슈 [#73](https://github.com/beone-capston/beone-mvp/issues/73).

- 실제 프런트 화면이 아닙니다. 이 화면의 결과를 실제 프런트 연동 성공으로 기록하지 않습니다.
- 모든 POST 차단을 입증하지 않습니다.
- 앱 소스, Gradle, Manifest, release 패키징, `WEB_START_URL`을 바꾸지 않습니다. Android 빌드에도 포함되지 않습니다(`settings.gradle.kts`는 `:app`만 포함).
- `frontend/`의 파일과 의존성, CDN을 쓰지 않습니다. 브리지와 업무 기능은 없습니다.
- 합성 값 `synthetic`과 실행 ID만 씁니다. 실제 카드번호, 계정, 개인정보를 넣지 않습니다.
- `docs/android/webview-shell.md`의 W6 콘솔 절차를 대체하지 않습니다. 이 문서는 fixture 절차만 설명합니다. 관측 결과의 정본은 [`docs/android/webview-shell.md`의 확인 기록](../../../docs/android/webview-shell.md#확인-기록-2026-11-06-fixture)입니다.

## 준비

```bash
npm ci --ignore-scripts
npm test
npm run build
npm run check:dist
```

`npm test`는 포트를 열지 않는 단위 검사입니다. 브라우저·WebView 동작이나 실제 HTTP 통신을 확인하지 않습니다.

## 실행 (승인 후, 터미널 2개)

기록 경로는 이 폴더 밖의 절대 경로여야 합니다.

```bash
npm run serve:fixture -- --log <절대 경로>/fixture.jsonl
npm run serve:receiver -- --log <절대 경로>/receiver.jsonl
```

| 서버 | 주소 | 역할 |
| --- | --- | --- |
| 화면 서버 | `127.0.0.1:5173` | `dist/` 제공, 같은 origin POST·시도·CSP 위반 기록 |
| 수신기 | `127.0.0.1:18080` | 다른 origin으로 간 합성 POST 기록 |

- loopback에만 바인딩합니다. 포트가 사용 중이면 다른 프로세스를 건드리지 않고 실패합니다.
- 본문은 1KB까지만 받습니다. 기록은 시각·서버·메서드·경로·상태·결과·실행 ID·사례·본문 길이뿐이며, CSP 위반은 지시문과 차단 origin을 더합니다.
- 기기 연결은 승인된 대상 하나를 `adb -t <transport_id>`로 지정해 실행합니다. 먼저 `adb -t <transport_id> reverse --list`에서 `tcp:5173`·`tcp:18080` 매핑만 확인합니다. 없으면 `adb -t <transport_id> reverse tcp:5173 tcp:5173`, `adb -t <transport_id> reverse tcp:18080 tcp:18080`으로 추가하고 포트별로 이번에 추가했는지 관측 폴더에 기록합니다. 같은 매핑이 있으면 "기존"으로 기록하고 재사용합니다. 다른 목적지로 연결돼 있으면 덮어쓰지 않고 중단합니다. transport_id·일련번호·IP는 기록하지 않습니다.

## 종료

30분이 지나면 자동 종료하며, 자동 종료 뒤 다시 시작하지 않습니다. 기록 파일을 열거나 쓰지 못하면 그 서버는 표준 오류에 `기록 실패`를 남기고 스스로 멈춘 뒤 종료 코드 1로 끝납니다. 먼저 끝낼 때는 다음을 실행합니다.

1. 두 터미널에서 Ctrl+C
2. 이번에 추가한 매핑만 `adb -t <transport_id> reverse --remove tcp:<포트>`로 제거합니다. 기존 매핑은 그대로 둡니다(`--remove-all`은 쓰지 않습니다).
3. `lsof -nP -iTCP:5173 -sTCP:LISTEN`, `lsof -nP -iTCP:18080 -sTCP:LISTEN` 출력이 비었는지 확인

## 관측 항목

모든 기록에 설치본 APK의 SHA-256, 기기 모델, Android 버전, `navigator.userAgent`를 적습니다. 일련번호·IP·페어링 정보는 적지 않습니다. 판정 용어는 `docs/android/webview-shell.md`를 따릅니다.

| 항목 | 관측 | 관측상 통과 기준 |
| --- | --- | --- |
| W1 | 세로 화면의 표식, TOP-EDGE, BOTTOM-EDGE | 표식이 보이고 두 끝 표시가 시스템 바·컷아웃에 가리지 않으며 앱 안내 문구가 없음 |
| W2 | page-2로 이동해 기록을 만든 뒤 회전 전후 주소, `history.length`, 뒤로 가기 | 주소와 기록 수가 같고 뒤로 가기가 이전 페이지로 감 |
| W2 기록만 | JS 값(`window.__w2`, 카운터), 화면 서버의 GET 횟수 | 판정에 쓰지 않음 |
| W5(debug) | `chrome://inspect/#devices` | `com.beone.app`의 WebView가 fixture 주소로 보임 |

연결 실패로 화면이 뜨지 않으면 실패가 아니라 관측 불충분입니다.

## W6 절차

기기에서 앱을 연 뒤 **새 실행 ID**를 한 번 누르고 화면의 전체 UUID를 기록합니다. 회차가 끝날 때까지 실행 ID를 바꾸지 않습니다. 아래 순서로 실행하고, 각 기록 파일에서 PC C0 run과 기기 실행 ID를 따로 찾습니다.

| 단계 | 조작 | 기대 기록 |
| --- | --- | --- |
| C0 | PC에서 아래 `curl` 2개 | 화면 서버와 수신기에 각각 `case=C0`, `outcome=received`, `status=200` |
| C1 | 화면의 C1 버튼 (WebView 하위 요청) | 수신기에 기기 실행 ID의 `case=C1`, `received`, 200 |
| A | 같은 origin 폼 POST, 서버 페이지의 "첫 페이지로"로 복귀 | 화면 서버에 기기 실행 ID의 `case=A` `attempt` 204와 `received` 200 |
| B | 다른 origin 메인 프레임 폼 POST | 화면 서버에 기기 실행 ID의 `case=B` `attempt` 204. 수신기에는 기대상 없음 |
| C2 | B 뒤 화면이 fixture 첫 페이지 그대로면 그대로, 다른 화면이면 뒤로 가기 1회로 복귀. 같은 전체 UUID를 확인한 뒤 C2 버튼 | 수신기에 기기 실행 ID의 `case=C2`, `received`, 200 |
| D | 하위 요청 버튼 (C1·C2와 같은 요청의 반복 대조) | 수신기에 기기 실행 ID의 `case=D`, `received`, 200. 화면 유지는 사용자 관측으로 따로 기록 |

```bash
RUN="pc-c0-$(date -u +%Y%m%dT%H%M%SZ)"
curl -i -X POST -H 'Content-Type: application/x-www-form-urlencoded' --data "run=$RUN&case=C0&sample=synthetic" http://127.0.0.1:5173/w6/same
curl -i -X POST -H 'Content-Type: application/x-www-form-urlencoded' --data "run=$RUN&case=C0&sample=synthetic" http://127.0.0.1:18080/w6/receive
curl -sI http://127.0.0.1:5173/ | grep -i content-security-policy
```

마지막 명령의 출력이 비어 있어야 합니다(화면 서버 응답에 CSP 없음).

PC C0 run은 관측마다 새로 만들며, 두 `curl`에 같은 `$RUN`을 씁니다. 기기 실행 ID(전체 UUID)와 섞지 않습니다. B 뒤 첫 화면으로 돌아오지 못하거나 실행 ID가 바뀌면 그 회차는 관측 불충분으로 끝냅니다. 앱을 다시 띄워 만든 새 실행 ID의 C2를 이전 회차에 이어 붙이지 않습니다.

| 결과 | 조건 |
| --- | --- |
| 관측상 통과 (B) | 두 서버의 C0 `received` 200, 같은 기기 실행 ID의 C1·C2 `received` 200, A `attempt` 204·`received` 200, B `attempt` 204가 모두 있음. 수신기 종료 뒤 최종 로그에서 B 없음, 그 실행 ID의 `csp-violation` 없음, 설치본 해시 일치, 관측 중 대상·매핑 변경 없음. 결과는 "B `attempt` 기록 시각부터 수신기 종료 시각까지 이 수신기에 B가 기록되지 않음"으로 적습니다 |
| 실패 (B) | 수신기에 B가 도착 |
| 관측 불충분 | 위 비교군 중 하나라도 없음, B `attempt` 없음, B 뒤 복귀 실패나 실행 ID 변경, 그 실행 ID의 `csp-violation` 있음, 해시 불일치, 관측 중 서버 종료(`기록 실패` 포함)나 대상·매핑 변경 |
| 관측상 통과 (A) | 같은 실행 ID의 A `attempt` 204와 `received` 200, 앱 안내 문구 없음(사용자 관측) |
| D (반복 대조) | 수신기에 D `received` 200. C1·C2와 같은 요청이므로 독립 증거로 쓰지 않습니다 |

**해석 한계**

- 수신 기록 없음은 **네트워크 전송 0의 증명이 아닙니다.** 이 수신기에 기록되지 않았다는 관측입니다.
- HTTP 응답 성공은 기록이 디스크에 남았다는 보장이 아닙니다. `기록 실패`가 난 회차의 미수신 결과는 증거로 쓰지 않습니다.
- B 미수신은 수신기를 종료한 뒤 최종 로그로 판단하고, 관측 구간(B `attempt` 기록 시각부터 수신기 종료 시각까지)을 함께 적습니다. 마지막 요청 시각을 종료 시각으로 대신하지 않습니다.
- B 직후의 WebView 오류 화면이나 앱 안내 문구는 차단의 증거가 아닙니다. 앱 안내는 신뢰하지 않는 메인 프레임 주소가 시작될 때 표시되므로(`RestrictedWebViewClient.kt`의 `onPageStarted`) 실제 전송 여부와 상관없이 나타날 수 있습니다.
- 안내 문구나 위반 수 0만으로 차단 주체를 단정하지 않습니다. 이 절차에는 이전 APK 비교를 넣지 않았으므로, 결과를 네이티브 인터셉터의 효과로 단정하지 않습니다.
- D 도착은 차단 실패가 아니라 네이티브가 막지 않는 경로의 확인입니다.
- 리디렉션 뒤 POST, iframe 안 POST, 공개 HTTPS, 다른 기기·WebView 버전은 범위 밖입니다.
- 이 화면은 CSP를 두지 않습니다. 실제 프런트의 CSP 효과를 확인하지 않습니다.

## 제거

이 폴더(`android/tools/webview-fixture/`)만 삭제하면 됩니다. Gradle과 Manifest에 연결 지점이 없어 정리할 항목이 없습니다. 삭제는 별도 승인을 받습니다. 실제 프런트 연동은 기존 `WEB_START_URL` 경로를 씁니다.

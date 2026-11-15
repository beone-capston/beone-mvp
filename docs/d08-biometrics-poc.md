# Android 생체인증·기기 PIN 실기기 PoC 결과 (D-08 근거)

- 시험일: 2026-10-08
- 관련 요구사항: [PRD §4.3 단일 가상카드 활성화](PRD.md), [FR-06 React·Android 가상카드 UI](PRD.md), [결정 D-08](decisions.md), [이슈 #11](https://github.com/yim0327/beone-mvp/issues/11)
- 상태: 이 문서의 결과를 근거로 **D-08의 시연 기기·인증 정책·앱 복귀 정책을 확정**했다(2026-10-08). BeONE 앱의 인증 기능이 구현됐다는 뜻은 아니며, 아래 [남은 검증](#4-남은-검증)은 본 앱 구현 후 FR-06 실기기 검증에서 확인한다.
- 범위: 저장소 밖의 일회성 PoC 앱(`com.beone.biometricspoc`, BeONE Android와 같은 AGP 9.1.1·minSdk 30·targetSdk 36, `androidx.biometric` 1.1.0)으로 시험했다. 생체 데이터·PIN·계정 정보는 다루거나 기록하지 않았다.

## 1. 시험 기기

| 항목 | 값 |
| --- | --- |
| 모델 | Samsung SM-S942N |
| OS | Android 16 (API 36), One UI 8.5 |
| 보안 패치 | 2026-08-05 |
| 빌드 | BP4A.251205.006.S942NKSS4AZHA |
| 화면 잠금 | PIN |
| 등록 생체 | 지문. 얼굴은 정책 비교를 위해 시험 중 등록했다. |
| WebView | Android System WebView 154.0.8037.57 |

시스템(`dumpsys biometric`)이 보고한 센서 등급:

| 수단 | strength | Android 등급 |
| --- | --- | --- |
| 지문 | 15 | `BIOMETRIC_STRONG` (Class 3) |
| 얼굴 | 255 | `BIOMETRIC_WEAK` (Class 2) |

`canAuthenticate`는 `BIOMETRIC_STRONG`, `BIOMETRIC_WEAK`, `DEVICE_CREDENTIAL`과 두 조합 모두 `SUCCESS`였다.

## 2. 인증 정책 후보 비교

| 정책 | Authenticators | 실기기 결과 |
| --- | --- | --- |
| A | `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` | 지문 성공. 인증 창 안에 PIN 전환 버튼이 있다. 등록되지 않은 손가락 5회 불일치 → 잠김 → 같은 창에서 PIN으로 진행해 성공. 얼굴을 등록해도 얼굴로는 인증되지 않았다. |
| B | `BIOMETRIC_WEAK \| DEVICE_CREDENTIAL` | 얼굴이 인증 대상에 포함됐다(시스템 세션 `StrengthRequested: 255`, 얼굴 센서 eligible). 기본 화면은 지문이며, 사용자가 얼굴 인식으로 직접 전환한 뒤 인식 후 "확인"을 눌러야 했다. |
| C | `BIOMETRIC_STRONG` → 별도 `DEVICE_CREDENTIAL` | 지문 성공. "기기 PIN 사용" 버튼 → 별도 PIN 화면에서 성공·취소 모두 정상 처리. 프롬프트가 두 단계이고 앱 코드가 대체 경로를 직접 관리해야 한다. |
| D | `DEVICE_CREDENTIAL` | 지문 선택지 없이 PIN 화면만 표시되고 성공. 생체 불가 상황의 기준선으로만 시험했다. |

## 3. 가상카드 활성화 흐름 (PRD §4.3)

정책 A로 네이티브 화면과 WebView 화면에서 시험했다.

| 시나리오 | 결과 |
| --- | --- |
| 화면 진입 시 인증 요청 | 네이티브·WebView 모두 진입 시 자동으로 인증 창이 표시됐다. |
| 인증 성공 → 60초 활성화 | 결제 버튼이 활성화됐다. 활성 중 결제 요청은 네이티브 상태로 다시 판정해 허용됐다. |
| 60초 만료 | 인증 후 60초에 `EXPIRED`로 비활성화되고 재인증 안내가 표시됐다. |
| 취소 | `ERROR_USER_CANCELED`로 비활성화되고 재인증 안내가 표시됐다. |
| 지문 불일치 | 인증 창이 유지되며 불일치 횟수가 기록됐다. 5회 후 잠김 → PIN 대체 성공(정책 A). |
| 재인증 | 만료·취소 후 다시 인증하면 60초 활성화가 다시 시작됐다. |
| 앱 이탈 후 복귀 (타이머 유지 방식) | 남은 47.6초에서 나갔다가 20초 뒤 복귀 → 27.9초. 시간이 계속 흐른다. |
| 앱 이탈 후 복귀 (즉시 잠금 방식) | 홈으로 나갔다 복귀하면 비활성화되고 "앱을 벗어나 비활성화" 안내가 표시됐다. |
| WebView 상태 전달 | `addWebMessageListener` 브리지로 성공·취소·불일치·결제 판정·잠금 상태가 웹 화면에 전달되고, 복귀 시 네이티브 상태와 동기화됐다. |

인증 결과의 인증 수단(`BIOMETRIC`/`DEVICE_CREDENTIAL`)도 함께 전달됐다. 이 인증은 앱 내 가상 결제 화면을 여는 트리거이며, 서버의 결제 판단에 사용하지 않는다(PRD FR-06).

## 4. 남은 검증

실기기에서 시험하지 않은 항목이다. 본 앱 구현 후 FR-06 실기기 검증에서 확인한다.

- 생체를 모두 삭제한 상태에서 기기 PIN으로 진행되는지(PRD §4.3-2). 정책 A에서 잠김 후 PIN 대체는 확인했다.
- 화면 잠금이 없을 때 기기 인증 설정 안내를 표시하고 활성화하지 않는지(PRD §4.3-5). 에뮬레이터에서만 확인했으며 최종 확인으로 사용하지 않는다.

## 5. 구현 참고 (결정 아님)

PoC에서 확인한 구현상 주의점이다. BeONE Android 구현 시 참고하며, 별도 결정 없이 확정된 설계로 보지 않는다.

- 60초 상태는 네이티브가 `SystemClock.elapsedRealtime` 기준으로 관리하고, 웹 화면의 카운트다운은 표시용으로만 사용한다.
- 인증 실패·취소가 발생하면 활성 중이어도 즉시 비활성화한다(PRD §4.3-4). 활성 중 재인증에 성공하면 60초를 다시 시작한다.
- 인증 진행 중 화면이 재생성되면 결과가 유실될 수 있다. PoC는 가상카드 화면에 `configChanges`를 지정해 회전·폴드·테마 변경 시 재생성을 막았고, 이런 변경은 앱 이탈로 보지 않았다.
- WebView는 자체 padding을 적용하지 않는다. edge-to-edge(targetSdk 35+)에서는 상위 컨테이너에 시스템 바 inset을 줘야 한다. 에뮬레이터에서는 드러나지 않고 실기기에서 발견됐다.
- WebView 브리지는 앱 에셋 origin(`https://appassets.androidplatform.net`)으로 제한한 `addWebMessageListener`와 `WebViewAssetLoader`를 사용했다.
- `androidx.biometric`의 최신 stable은 1.1.0이다(2026-10-08 기준, 1.4.x는 alpha).

# Android 기기 인증과 60초 활성 상태

가상카드 화면을 쓰기 전에 기기 인증(지문 또는 기기 PIN)을 받고, 성공하면 60초 동안 `가상카드 사용 준비` 상태를 유지하는 Android 네이티브 기반입니다. 근거는 [PRD §4.3·FR-06](../PRD.md), [결정 D-08](../decisions.md#d-08-확정-정책), [실기기 PoC](../d08-biometrics-poc.md)입니다.

> 이 인증은 **앱 안의 가상 결제 화면을 여는 트리거**입니다. 서버 로그인, 카드 소유 증명, 카드사 결제 인증, 실제 금융거래 승인이 아니며 그렇게 사용하지 않습니다.

## 현재 구현 범위

| 구분 | 상태 |
| --- | --- |
| `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` 단일 프롬프트 호출 | 구현 |
| 성공·지문 불일치·실패·취소·설정 필요·사용 불가 구분 | 구현, 단위 테스트 |
| 60초 활성화, 만료, 재인증 시 새 60초, 실패·취소 즉시 비활성화 | 구현, 단위 테스트 |
| 앱 이탈 즉시 비활성화, 화면 구성 변경은 이탈로 보지 않음 | 구현, 단위 테스트 |
| 인증 요청 하나만 진행(인증 중·정리 중·정지 중에는 새 요청을 라이브러리에 보내지 않음), 늦게 도착한 결과·앱 이탈 뒤 결과 무시 | **검증용 구현**, 단위 테스트(가짜 authenticator). 실기기에서 요청 하나만 보내는 동작은 관측상 통과(SM-S942N 한 대, 사용자 관측·debug 로그) — [아래 결정](#구현-중-정한-작은-결정) 참고 |
| 앱 이탈로 취소한 요청의 최종 결과가 올 때까지 `정리 중` 표시, 결과는 버리고 그 뒤에만 새 인증 허용 | **검증용 구현**, 단위 테스트. 실기기에서는 관측 못 함(관측한 이탈마다 시스템 취소가 먼저 와서 정리 중에 들어가지 않음) |
| 응답이 없을 때 `정지`(인증 중 대기 시간·정리 중 대기 시간 초과). 활성화·자동 재인증 없음 | **검증용 구현**, 단위 테스트. 인증 중 대기 시간 초과는 실기기에서 사용자 관측(로그 보강 전 APK), 정리 중 대기 시간 초과는 관측 못 함. 기본값은 인증 중 120초, 정리 중 10초이며 **근거 없는 제안**입니다. 정지 안내는 "앱을 완전히 종료한 뒤 다시 열어 주세요"이고, 이 복구가 실제로 되는지는 아직 관측하지 않았습니다 |
| debug 전용 실기기 점검 화면 | 구현 (release 미포함) |
| debug 전용 인증 상태 관측 로그(`BeoneAuthTrace`) | 구현, 단위 테스트. release에서는 기록하지 않음 — [관측 기록](#관측-기록-debug-전용) |
| 실제 기기에서의 동작 확인 | 1~15번 통과(2026-10-29, SM-S942N Android 17)는 단일 요청·정리 대기 구조를 넣기 **전** 코드(`0b1cc3f`, Android 코드는 `4374d43`과 같음) 기준입니다. 단일 요청·정리 대기 구조를 넣은 코드(`08ee320`에 커밋됨)는 2026-10-29~30에 같은 기기에서 R1~R4·R6~R9와 1~6·8~11·13번(15번은 R1로 대응)을 관측상 통과했습니다(로그 보강 전 APK는 사용자 관측만, 로그 보강 후 APK는 사용자 관측과 debug 로그 대조). R5·R10(정리 중)은 관측 못 함, 7·12·14 단독·16·17번은 **미실행** — [실기기 확인 목록](device-checklist.md#확인-기록-2026-10-2930-지금-코드-커밋-전) |
| WebView 화면 연결, 웹에 상태 전달(브리지) | **미구현** — 계약은 D-21로 정해짐, 구현은 #24·#26 병합 뒤 |

## 용어

- **기기 인증:** Android가 제공하는 지문(생체) 또는 화면 잠금 PIN·패턴·비밀번호(기기 자격 증명, `DEVICE_CREDENTIAL`) 확인입니다. 앱은 생체 정보나 PIN을 직접 받지 않고 성공 여부만 받습니다.
- **단조 시계:** 기기 부팅 후 흐른 시간(`SystemClock.elapsedRealtime`)입니다. 사용자가 기기 시각을 바꿔도 영향을 받지 않아 60초 계산에 씁니다.
- **화면 구성 변경:** 회전, 폴드 펼침·접힘, 다크 모드 전환처럼 Activity가 다시 만들어지지만 앱을 벗어난 것은 아닌 경우입니다.
- **요청 단계:** 앱이 보낸 인증 요청의 진행 상태입니다. `대기`(새 요청 가능), `인증 중`(라이브러리에 요청을 전달하고 결과 대기), `정리 중`(앱 이탈로 취소를 요청하고 그 요청의 최종 결과 대기), `정지`(응답 대기 시간을 넘김. 여전히 그 요청의 최종 결과를 기다리며 새 요청을 받지 않음)가 있습니다.
- **최종 결과:** 인증 창이 닫히는 결과(성공·실패·취소 등)입니다. 지문 불일치는 창이 열린 채 다시 시도할 수 있어 최종 결과가 아닙니다.

## 패키지 구조

`android/app/src/main/java/com/beone/app/auth/`

| 파일 | 역할 |
| --- | --- |
| [AuthOutcome.kt](../../android/app/src/main/java/com/beone/app/auth/AuthOutcome.kt) | 인증 결과 종류와 인증 수단(`BIOMETRIC`·`DEVICE_CREDENTIAL`) |
| [VirtualCardActivation.kt](../../android/app/src/main/java/com/beone/app/auth/VirtualCardActivation.kt) | 60초 활성 상태 계산. 어느 요청의 결과인지는 판단하지 않고 넘겨받은 결과만 반영합니다. Android에 의존하지 않는 순수 로직 |
| [DeviceAuthenticator.kt](../../android/app/src/main/java/com/beone/app/auth/DeviceAuthenticator.kt) | 인증 요청을 라이브러리에 전달하는 경계(인터페이스)와 전달 여부(`AuthRequestDispatch`). 테스트에서는 가짜 구현으로 바꿉니다 |
| [BiometricDeviceAuthenticator.kt](../../android/app/src/main/java/com/beone/app/auth/BiometricDeviceAuthenticator.kt) | `androidx.biometric`의 `BiometricPrompt`를 쓰는 실제 구현. 라이브러리가 조용히 무시하는 호출 조건을 먼저 걸러 냅니다 |
| [AuthRequestGate.kt](../../android/app/src/main/java/com/beone/app/auth/AuthRequestGate.kt) | 요청 단계(대기·인증 중·정리 중·정지) 관리, 도착한 결과의 적용·버림 판정, 응답 대기 시간(`AuthResponseTimeouts`). Android에 의존하지 않는 순수 로직 |
| [BiometricResultMapper.kt](../../android/app/src/main/java/com/beone/app/auth/BiometricResultMapper.kt) | `BiometricManager`·`BiometricPrompt` 코드 → `AuthOutcome` 변환 |
| [VirtualCardActivationViewModel.kt](../../android/app/src/main/java/com/beone/app/auth/VirtualCardActivationViewModel.kt) | 화면 재생성 동안 활성 상태와 요청 단계 유지, 게이트 판정에 따라 결과 적용, 화면 정지의 이탈 여부 판정, 라이브러리 호출 중 재진입 차단 |
| [AuthTrace.kt](../../android/app/src/main/java/com/beone/app/auth/AuthTrace.kt) | 관측 기록의 형식과 인터페이스(`AuthTraceSink`·`AuthTraceEvent`·`AuthTracer`). 기본값은 무동작이며 Android에 의존하지 않습니다 — [관측 기록](#관측-기록-debug-전용) |

debug 전용: [DeviceAuthCheckActivity.kt](../../android/app/src/debug/java/com/beone/app/auth/DeviceAuthCheckActivity.kt) — 실기기 확인용 점검 화면입니다. 제품 화면이 아니며 `src/debug`에만 있어 release APK에 들어가지 않습니다. [DebugAuthTrace.kt](../../android/app/src/debug/java/com/beone/app/auth/DebugAuthTrace.kt) — 관측 기록을 logcat으로 내보내는 debug 전용 구현입니다.

## 처리 흐름

```text
화면 진입 또는 [기기 인증] 버튼(대기 단계에서만 켜짐)
  → ViewModel.requestAuthentication(authenticator)
      ├─ 대기가 아님(인증 중·정리 중·정지) 또는 라이브러리 호출 중 → 거절(라이브러리를 부르지 않음)
      ├─ canAuthenticate 결과가 설정 필요/사용 불가 → 창을 띄우지 않고 비활성 + 안내
      ├─ 단계를 인증 중으로 먼저 바꿈 → authenticator.authenticate()
      │    ├─ NOT_SENT(상태 저장됨·종료 중 등으로 호출하지 않음) → 대기로 되돌림
      │    └─ SENT_TO_LIBRARY(라이브러리 호출함. 창 표시 확인은 아님) → 인증 중 유지
  → 콜백 결과 → ViewModel.onAuthOutcome → AuthRequestGate.accept
      ├─ 인증 중: 결과 적용
      │    ├─ 성공 → 지금부터 60초 활성(남은 시간을 이어 붙이지 않음), 대기
      │    ├─ 지문 불일치 → 즉시 비활성, 인증 중 유지(창은 열린 채 재시도 가능)
      │    └─ 실패·취소·설정 필요·사용 불가 → 즉시 비활성 + 재인증/설정 안내, 대기
      ├─ 정리 중·정지: 버림. 최종 결과면 대기(자동 재인증 없음, 사용자가 다시 탭)
      └─ 대기: 버림(진행 중인 요청 없음)
시간 경과(화면이 상태를 읽을 때 계산)
  ├─ 인증 중 대기 시간 초과 → 정지, 남아 있던 활성도 비활성(NO_RESPONSE), 취소 요청 안 함
  └─ 정리 중 대기 시간 초과 → 정지
  (시간 경과만으로 대기로 돌아가지 않음)
Activity.onStop
  ├─ isChangingConfigurations = true(회전 등) → 단계·상태 유지
  └─ false(홈 이동, 다른 앱 전환 등) → 즉시 비활성(LEFT_APP)
       ├─ 인증 중 → 정리 중으로 먼저 바꾼 뒤 취소 요청
       ├─ 정지(취소 요청 전) → 취소 요청 1회
       └─ 정리 중·정지(취소 요청함)·대기 → 추가 취소 없음
```

실기기(SM-S942N, Android 17, 단일 요청·정리 대기 구조 도입 **전** 코드)에서 인증 창이 떠 있는 채로 홈으로 나가자 비활성화 이유가 `LEFT_APP`이 아니라 `AUTH_CANCELED`로 남았습니다. 시스템의 취소 결과가 `onStop`보다 먼저 처리된 것으로 봅니다(`onStop` 뒤 결과는 무시되므로). 비활성 상태, 인증 창 닫힘, 재인증 안내는 같습니다. 지금 코드의 debug 로그(2026-10-30, 같은 기기)에서는 이 순서를 직접 봤습니다. 시스템 취소 콜백이 `ON_PAUSE`보다 먼저 도착해 이탈 처리 시점의 단계가 이미 대기였습니다([이 기기 관측](#이-기기에서-관측한-콜백-순서-sm-s942n-android-17-2026-10-30)). 지금 코드에서 `onStop` 뒤 도착한 결과를 정리 중 단계에서 버리는 동작은 실기기에서 일어나지 않아 단위 테스트로만 확인했습니다([확인 목록](device-checklist.md) 15번, R1~R10).

결과 코드 분류(`BiometricResultMapper`):

| 결과 | 코드 |
| --- | --- |
| 취소 | `ERROR_USER_CANCELED`, `ERROR_CANCELED`, `ERROR_NEGATIVE_BUTTON` |
| 설정 필요 | `canAuthenticate`의 `BIOMETRIC_ERROR_NONE_ENROLLED`, `ERROR_NO_DEVICE_CREDENTIAL`, `ERROR_NO_BIOMETRICS` |
| 인증 시도 | `canAuthenticate`의 `BIOMETRIC_SUCCESS`, `BIOMETRIC_STATUS_UNKNOWN`(알 수 없으면 시도해 보고 콜백으로 판단) |
| 사용 불가 | 그 밖의 `canAuthenticate` 오류, `ERROR_HW_UNAVAILABLE`, `ERROR_HW_NOT_PRESENT`, `ERROR_SECURITY_UPDATE_REQUIRED` |
| 실패 | 잠김(`ERROR_LOCKOUT`·`_PERMANENT`), 시간 초과 등 나머지 오류 |

## 사용 예

```kotlin
// ViewModel은 응답 대기 시간을 주입받습니다. 기본값 120초·10초는 근거 없는 제안입니다.
val factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VirtualCardActivationViewModel(SystemClock::elapsedRealtime, AuthResponseTimeouts()) as T
}

// Activity.onCreate — 재생성마다 authenticator를 새로 만들어야 진행 중 결과를 이어 받습니다.
viewModel = ViewModelProvider(this, factory)[VirtualCardActivationViewModel::class.java]
authenticator = BiometricDeviceAuthenticator(this) { outcome -> viewModel.onAuthOutcome(outcome) }
viewModel.requestAuthentication(authenticator) // true는 라이브러리에 전달했다는 뜻이며 창 표시 확인이 아님

// Activity.onStop
viewModel.onScreenStopped(isChangingConfigurations, authenticator)

// 화면 갱신: 상태는 읽을 때 만료를 다시 계산합니다.
val active = viewModel.state is ActivationState.Active
val remaining = viewModel.remainingMillis()
val canRequest = viewModel.requestPhase == AuthRequestPhase.Idle // 인증 버튼 활성 여부
```

## 구현 중 정한 작은 결정

운영 규칙상 구현 세부 결정이며, 실기기 확인 결과에 따라 바뀔 수 있습니다.

- 60초 경계: 성공 시각 + 60,000ms가 되는 순간 만료입니다(59,999ms까지 활성).
- 지문 불일치 한 번도 "실패"로 보아 활성 중이면 즉시 비활성화합니다. 인증 창은 열려 있으므로 그대로 재시도해 성공하면 새 60초가 시작됩니다.
- 앱 이탈 시 진행 중인 인증 요청의 취소를 라이브러리에 요청하고(창 닫기 요청), 그 요청을 `정리 중`으로 둡니다. 이탈 뒤 늦게 도착한 성공이 복귀 시 활성화하지 않게 하기 위해서입니다. 취소 요청이 곧 취소 완료는 아니므로 그 요청의 최종 결과가 올 때까지 새 요청을 받지 않습니다(아래 단일 요청과 정리 대기). 기기 PIN 입력은 시스템 인증 창 안에서 진행돼 `onPause`·`onStop`이 생기지 않는 것을 실기기(SM-S942N, Android 17)에서 확인했습니다([확인 목록](device-checklist.md) 8번).
- 단일 요청과 정리 대기(검증용 구현. 요청 하나만 보내는 동작은 실기기 관측상 통과, 정리 중 경로는 실기기 관측 못 함): `BiometricPrompt` 콜백에는 어느 요청의 결과인지 나타내는 값이 없고, `androidx.biometric` 1.1.0은 Activity마다 콜백을 하나만 보관합니다. 그래서 요청을 식별하지 않고, 앱이 보낸 요청이 **동시에 하나**가 되도록 막은 뒤 "인증 중인 동안 도착한 결과 = 그 요청의 결과"로만 귀속합니다([AuthRequestGate](../../android/app/src/main/java/com/beone/app/auth/AuthRequestGate.kt)).
  - 앱 이탈로 취소를 요청한 뒤에는 `정리 중`으로 두고 그 요청의 최종 결과가 올 때까지 새 요청을 받지 않습니다. 이 동안 도착한 결과는 성공이어도 버립니다. 대기로 돌아가는 근거는 **앱이 받은 최종 결과뿐**입니다. `androidx.biometric` 1.1.0 소스에서 앱에 최종 결과를 보내는 경로와 다음 창 표시를 막는 내부 플래그를 푸는 경로가 함께 실행되는 것을 읽고 정한 근거이며, 라이브러리의 공개 약속이 아니고 다른 버전·기기에서 같다고 보장하지 않습니다.
  - 이전 방식(요청을 순서대로 기록해 결과를 가장 오래된 요청에 붙임)은 복귀 직후 빠르게 탭하면 라이브러리가 새 창을 띄우지 않은 채 앱이 인증 중으로 남는 회귀가 있어 채택하지 않았습니다(사용자 결정, 2026-10-29). 그 방식의 코드와 테스트는 커밋하지 않았고 이 구조로 대체했습니다.
  - 정리 중·정지 중에는 화면에 그 상태를 따로 표시하고 [기기 인증] 버튼을 끕니다. 최종 결과로 대기가 되어도 자동으로 다시 인증하지 않고 사용자가 다시 눌러야 합니다.
- 요청 전달과 실제 시작의 구분: `DeviceAuthenticator.authenticate()`의 `SENT_TO_LIBRARY`는 라이브러리 `authenticate`를 호출했다는 뜻이며, 인증 창이 실제로 떴다는 뜻이 아닙니다. 창 표시를 알려 주는 공개 API가 없습니다. 실제 구현은 라이브러리가 로그만 남기고 조용히 무시하는 조건(Activity 종료 중, `FragmentManager` 상태 저장됨 — `onStop` 뒤 포함)이면 호출하지 않고 `NOT_SENT`를 돌려줍니다. 또 호출 직전에 `FragmentManager.executePendingTransactions()`로 직전 인증 결과 뒤 예약만 된 라이브러리 내부 화면 조각 제거를 먼저 실행해, 곧 제거될 조각에서 새 창을 띄우지 않게 합니다. 이 호출이 예외를 던지는 조건(트랜잭션 실행 중 호출 등)이면 `NOT_SENT`로 둡니다. 그 밖에 라이브러리·시스템이 요청을 받고도 창을 띄우지 않는 경로는 앱이 감지할 수 없습니다.
- 응답이 없을 때: `인증 중`이나 `정리 중`이 주입받은 대기 시간을 넘기면 `정지`로 바꿉니다. 정지에서는 활성화하지 않고(인증 중이던 경우 남아 있던 활성도 `NO_RESPONSE`로 비활성), 새 요청을 받지 않으며, 자동 재인증·화면 자동 재실행·앱 내 취소 버튼은 두지 않습니다. 시간 경과는 그 요청이 끝났다는 증거가 아니므로 정지에서도 그 요청의 결과를 계속 기다리고, 늦게 도착한 최종 결과는 활성화 없이 버린 뒤 대기로 돌아갑니다. 인증 중 대기 시간이 끝나도 앱은 취소를 요청하지 않으며, 정지 중 앱을 벗어나면 그때 한 번 취소를 요청합니다.
  - 인증 중 대기 시간은 사용자가 창에서 PIN을 천천히 입력하는 정상 경우와 창이 뜨지 않은 경우를 구분할 근거가 없습니다. 값이 짧으면 정상 인증 결과도 버려집니다.
  - 대기 시간 기본값은 인증 중 120초, 정리 중 10초입니다(2026-11-04 사용자 결정, D-21). **근거 없는 제안이며 실기기 관측으로 조정합니다.** debug 점검 화면도 이 기본값을 씁니다. 2026-10-29~30 관측은 이전 실험값(인증 중 30초, 정리 중 5초) 기준입니다.
  - 정지하면 자동으로 다시 인증하지 않고 `NO_RESPONSE`와 "앱을 완전히 종료한 뒤 다시 열어 주세요"를 안내합니다. 인증 상태가 메모리에만 있어 프로세스 종료로 초기화된다는 코드 판단이며, 실제로 복구되는지는 아직 관측하지 않았습니다.
- 재진입: 상태 전이를 먼저 마친 뒤 `authenticate()`·`cancel()`을 부릅니다. 이 호출 안에서 결과가 동기로 도착해도 상태가 모순되지 않고, 호출 중 다시 들어온 인증 요청은 거절해 라이브러리 호출이 겹치지 않습니다(단위 테스트).
- 남은 한계(입증되지 않음):
  - 취소·정지한 요청의 최종 결과가 반드시 오는지, 언제 오는지 확인하지 않았습니다. 실기기 관측에서 앱이 취소를 요청한 적이 없어(시스템 취소가 먼저 옴) 이 경로는 아직 검증되지 않았습니다. 오지 않으면 정지가 계속되며, 같은 화면에서 다시 인증할 수 있는 상태로 돌아가는 방법은 입증되지 않았습니다. 화면을 닫고 다시 여는 것은 검증된 복구 방법이 아니고, 안내한 앱 재실행도 관측 전입니다.
  - 앞 요청의 최종 결과 직후 같은 화면에서 보낸 새 요청을 시스템 인증 서비스가 받아 창을 띄우는지는 라이브러리 소스로 입증되지 않았습니다. 이 기기에서는 성공 직후와 시스템 취소 직후 재요청에 창이 뜨는 것을 관측했지만 입증은 아닙니다. 앱 취소·정리 중을 거친 뒤의 재요청과, 이전 화면 인스턴스의 세션이 정리되지 않은 상태에서 새 인스턴스가 보낸 요청은 관측하지 못했습니다.
  - 시스템이 한 세션에서 최종 결과 뒤 이벤트를 더 보내면 다음 요청의 결과와 구분할 수 없습니다. 오류 뒤에는 프레임워크 문서가 "더 이상 동작 없음"을 말하지만 실제 준수는 확인하지 않았고, 성공 뒤에는 그런 문서 문구가 없습니다. 특히 이탈 없이 성공 직후 다시 요청한 경우가 해당합니다(한계 노출 단위 테스트 `limitationDuplicateSuccessOf…`). 실기기에서 성공 직후 재요청할 때 새 인증 없이 활성화되는 일은 재현되지 않았습니다. 다만 재요청 뒤 400ms 안팎에 성공 결과가 온 2건은 사용자 화면 관측으로는 정상이었지만, 로그로는 새 인증의 결과인지 앞 세션의 중복 성공인지 판별할 수 없습니다.
  - 인증 창이 실제로 뜨지 않은 `인증 중`은 감지할 수 없고, 대기 시간이 지나 정지로 바뀌는 것으로만 드러납니다.
  - 단위 테스트는 가짜 authenticator로 상태 기계 계약만 확인하며, 실제 `BiometricPrompt` 콜백·창 표시·OS 동작 확인을 대신하지 않습니다.
- 화면 재생성 처리는 PoC의 `configChanges` 방식 대신 ViewModel + `isChangingConfigurations`를 씁니다. WebView 화면에서 `configChanges`가 필요한지는 WebView 작업에서 따로 판단합니다.

## 관측 기록 (debug 전용)

화면 문구만으로는 요청 단계가 어떻게 바뀌었는지 알 수 없어서, debug 빌드에서만 단계 전이를 logcat에 한 줄씩 남깁니다. 기록은 관측용이며 인증 동작·단계 전이·호출 순서를 바꾸지 않습니다.

| 구성 | 위치 | 역할 |
| --- | --- | --- |
| `AuthTraceSink`, `AuthTraceEvent`, `AuthTracer` | [AuthTrace.kt](../../android/app/src/main/java/com/beone/app/auth/AuthTrace.kt) (main, Android 의존 없음) | 사건 한 건의 형식(`AuthTraceEvent`), 기록 대상 인터페이스(`AuthTraceSink`, 기본값 `None`은 아무것도 하지 않음), 시각·순번을 붙여 넘기는 `AuthTracer`. sink가 `None`이면 시계도 읽지 않고 바로 돌아가며, sink가 예외를 던져도 삼킵니다 |
| 기록 호출 | `AuthRequestGate`, `VirtualCardActivationViewModel`, `BiometricDeviceAuthenticator` | 생성자의 `trace` 인자(기본값 `AuthTracer.NONE`)로 받아, 전이가 끝난 뒤에만 기록합니다 |
| `DebugAuthTrace` | [DebugAuthTrace.kt](../../android/app/src/debug/java/com/beone/app/auth/DebugAuthTrace.kt) (debug 소스셋) | `Log.i("BeoneAuthTrace", …)`로 출력하는 유일한 구현. 화면이 다시 만들어져도 순번이 이어지도록 프로세스에 하나만 둡니다. 점검 화면이 이 tracer를 주입하고 Activity 수명주기도 기록합니다 |

- release에서 기록하지 않는 근거: logcat 출력 구현은 debug 소스셋에만 있고, main의 기본값은 무동작입니다. 인증 클래스를 만드는 곳도 debug 점검 화면뿐입니다. 2026-10-30 빌드에서는 release APK dex에 `BeoneAuthTrace`·`DebugAuthTrace` 문자열이 없는 것을 확인했습니다.
- 한 줄 형식: `t=<elapsedRealtime ms> seq=<순번> ev=<사건> from=<단계> to=<단계> req=<앱 내부 순번> <키=값>...`
- 주요 사건:
  - `LIFECYCLE`: onCreate 최초·재생성, onStart, onResume, onPause, onStop, onDestroy와 구성 변경 여부
  - 요청 쪽: `REQUEST_ATTEMPT`, `REQUEST_REJECTED`(호출 중 또는 대기 아님), `AVAILABILITY_BLOCKED`, `BEGIN`, `NOT_SENT`(종료 중·파괴·상태 저장·트랜잭션 예외), `DISPATCH`, `WITHDRAW_UNSENT`
  - 결과 쪽: `CALLBACK`(라이브러리 콜백 도착, 정수 코드만), `RESULT`(결과 종류, 최종 여부, 적용·버림, 전후 단계. 최종 결과로 대기로 돌아오면 `to=IDLE`)
  - 이탈·만료·활성: `APP_LEFT`, `CANCEL_REQUESTED`, `TIMEOUT`, `ACTIVATION`(활성 여부와 만료 시각)
- 넣지 않는 것: PIN·생체 정보, 결과 객체(`AuthenticationResult`·`CryptoObject`), 자유 형식 오류 문자열(`errString`), 사용자·기기 식별 정보, 토큰, 거래 정보. 정수 코드와 enum 이름만 씁니다.
- `req`는 로그를 맞춰 보기 위한 앱 내부 순번(관측용 상관관계 표식)입니다. 콜백에는 요청 식별자가 없으므로, 어떤 콜백이 그 요청의 것이라는 증거가 아닙니다.
- 콜백이 `authenticate()` 호출 안에서 바로 도착하면 `CALLBACK`·`RESULT` 줄이 `DISPATCH` 줄보다 먼저 찍힙니다(`DISPATCH`는 호출이 돌아온 뒤 기록).
- 단위 테스트([AuthTraceTest](../../android/app/src/test/java/com/beone/app/auth/AuthTraceTest.kt))는 기록하지 않을 때, 기록할 때, sink가 예외를 던질 때 상태·남은 시간·호출 수가 같은지, 그리고 대표 경로의 사건 순서를 확인합니다. 가짜 authenticator 기반이라 실제 콜백 순서를 입증하지 않습니다. `CALLBACK`·`NOT_SENT` 이유·수명주기 기록은 Android에 의존해 단위 테스트가 없습니다.

```kotlin
// debug 점검 화면: 같은 tracer를 ViewModel과 authenticator에 넘긴다. release 코드는 인자를 생략해 무동작 기본값을 쓴다.
VirtualCardActivationViewModel(SystemClock::elapsedRealtime, RESPONSE_TIMEOUTS, DebugAuthTrace.tracer)
BiometricDeviceAuthenticator(this, DebugAuthTrace.tracer) { outcome -> viewModel.onAuthOutcome(outcome) }
```

수집 명령과 실기기 로그 대조 결과는 [실기기 확인 목록](device-checklist.md#관측-로그-수집-방법-debug-전용)에 있습니다.

### 이 기기에서 관측한 콜백 순서 (SM-S942N, Android 17, 2026-10-30)

이 기기, 이 debug APK, 이 조작 범위에서만 관측한 결과입니다. 라이브러리나 OS가 약속하는 동작이 아니며, 다른 기기나 다른 이탈 방식으로 일반화하지 않습니다.

- 인증 창이 떠 있는 동안 홈·앱 전환·화면 끄기·회전 뒤 홈으로 이탈한 17회 모두, 시스템 취소 콜백(code 10 또는 5)이 앱의 `ON_PAUSE`보다 먼저 도착했습니다. 그래서 이탈 처리 시점의 단계는 이미 대기였고, 앱 취소 요청과 `정리 중` 진입은 0회였습니다.
- 알림창을 내렸을 때는 앱 이탈로 처리되지 않았고, 인증 창만 사용자 취소(code 10)로 닫혔습니다.
- 회전으로 화면이 다시 만들어진 20회 동안 새 인증 요청은 없었고, 인증 중 결과는 새 화면 인스턴스에서 적용됐습니다.
- 화면을 처음 열 때의 자동 요청은 `ON_START`·`ON_RESUME`보다 먼저 라이브러리에 전달됐고, 창은 정상적으로 떴습니다.

## 의존성

| 라이브러리 | 버전 | 출처 |
| --- | --- | --- |
| `androidx.biometric:biometric` | 1.1.0 (stable) | [Google Maven 메타데이터](https://dl.google.com/android/maven2/androidx/biometric/biometric/maven-metadata.xml), 2026-10-28 확인. 이후 버전은 1.2.0·1.4.0 alpha뿐 |

## 아직 하지 않은 것

- WebView에 React 화면 표시, 웹으로 인증 상태 전달. release 로딩 방식과 브리지 계약은 D-21로 정해졌고, 구현은 이 PR과 #26 병합 뒤에 합니다.
- 실제 기기 확인 중 16·17번(지문 없이 PIN만, 화면 잠금 없음). 기기 보안 설정을 바꿔야 해 [실기기 확인 목록](device-checklist.md)에 남아 있으며 단위 테스트·에뮬레이터 결과로 대신하지 않습니다.
- 지금 코드(단일 요청·정리 대기·정지)의 남은 실기기 확인: `정리 중` 진입 경로(R5·R10), 앱 취소 뒤 최종 결과 순서, 인증 창이 사라진 정지와 복구, 7·12·14(단독)번([확인 목록](device-checklist.md#관측-못-함미실행으로-남은-것)). 지금까지의 관측은 한 기기의 관측 결과이며 안전한 최종안이나 출시 가능 판정이 아닙니다.
- 화면이 RESUMED 상태일 때만 인증을 요청하는 조건은 두지 않고, 앱 복귀 때 자동 인증도 하지 않습니다(D-21). 이 기기 로그에서 최초 자동 요청은 `ON_START`·`ON_RESUME`보다 먼저 전달됐고 창은 정상적으로 떴습니다.
- 응답 대기 시간 값을 관측으로 조정하고, 정지 뒤 앱 재실행으로 복구되는지 확인.
- 계측 테스트(`androidTest`). 인증 창은 시스템 UI라 자동화가 제한적이어서 실기기 수동 확인으로 둡니다.

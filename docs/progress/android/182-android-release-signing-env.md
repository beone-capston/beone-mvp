# #182 release 서명 환경 변수 주입 골격

- 이슈: [#182](https://github.com/beone-capston/beone-mvp/issues/182)
- 브랜치: `chore/182-android-release-signing-env` (기준 `origin/develop` `0dab978`)
- 근거: 결정 38-9(release APK 서명은 환경 주입, 스토어 배포는 MVP 밖. 관리 기록이며 `docs/decisions.md`에는 아직 없음), G5 공개 구성 설계 4-2·4-3·4-5절과 5절 L6
- 성격: Gradle 설정과 문서만 바꿨습니다. 앱 소스·테스트 코드, debug 빌드 설정, 공개 계약은 바꾸지 않았습니다.

## 2026-11-24 · 골격 구현 · draft PR

### 한 일

- [`android/app/build.gradle.kts`](../../../android/app/build.gradle.kts): 환경 변수 `BEONE_RELEASE_STORE_FILE`·`BEONE_RELEASE_STORE_PASSWORD`·`BEONE_RELEASE_KEY_ALIAS`·`BEONE_RELEASE_KEY_PASSWORD`가 모두 있을 때만 release `signingConfig`를 만듭니다. 빈 값은 없는 것으로 봅니다. 없으면 지금처럼 서명 없는 release APK를 만듭니다. Gradle 속성 `beone.release.requireSigning=true`일 때만 누락을 구성 단계 실패로 만들고, 메시지에는 빠진 변수 이름만 적습니다. Kotlin DSL 형식은 그대로입니다.
- 사용법 문서: [README Android 절](../../../README.md#android), [`docs/android/webview-shell.md` release 서명 환경 변수](../../android/webview-shell.md#release-서명-환경-변수)

### 검증

`android/`에서 서명 환경 변수 없이 실행했습니다. 키 파일 생성·열람, 서명 빌드, 설치·adb·기기는 사용하지 않았습니다.

| 명령 | 결과 |
| --- | --- |
| `./gradlew assembleDebug testDebugUnitTest lintDebug` | 성공. debug 단위 테스트 174건 실패 0, lint 오류 0·경고 8(#171 기록과 같은 수) |
| `./gradlew assembleRelease testReleaseUnitTest lintRelease` | 성공. `app-release-unsigned.apk` 생성(APK 안 서명 파일 0개), release 단위 테스트 178건 실패 0, lint 오류 0·경고 8 |
| `./gradlew assembleRelease -Pbeone.release.requireSigning=true` | 의도한 실패. `beone.release.requireSigning=true 이지만 release 서명 환경 변수가 없습니다: BEONE_RELEASE_STORE_FILE, BEONE_RELEASE_STORE_PASSWORD, BEONE_RELEASE_KEY_ALIAS, BEONE_RELEASE_KEY_PASSWORD` |

### 남은 것

- **N8 결정·승인 대기:** 키 생성·보관 위치·담당·백업, versionCode 증가 규칙
- 승인 대기: 서명 경로 확인(키 필요), 서명 빌드 실행, APK 전달·설치·adb, release 실기기 확인
- `CLAUDE.md` 검증 명령에 release 줄 추가(설계 X12, 이 작업 범위 밖)

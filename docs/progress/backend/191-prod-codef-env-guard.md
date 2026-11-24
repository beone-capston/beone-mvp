# #191 prod에 CODEF 키·토큰 환경 변수가 있으면 기동 실패 (LP-4)

브랜치 `feat/191-prod-codef-env-guard`, 시작 기준 `origin/develop` `875611d`(#185 병합).

## 2026-11-25 · PublicProfileGuard에 CODEF 환경 변수 규칙 추가

- 근거: 결정 38-8(CODEF는 비공개 실연동 검증 뒤에만 회원과 연결, 공개 `prod`에서는 미등록, 공개 전환은 별도 승인). CODEF 빈·경로가 `prod`에서 미등록인 것은 #187이 테스트로 고정하지만, 공개 서버 환경에 키가 들어가는 것을 막는 장치는 없었습니다.
- 한 일
  - `PublicProfileGuard`에 규칙 하나를 더했습니다. `prod`가 켜져 있고 CODEF 키·토큰 환경 변수 `CODEF_CLIENT_ID`, `CODEF_CLIENT_SECRET`, `BEONE_CODEF_APPROVAL_HMAC_KEY`, `BEONE_CODEF_CONNECTED_ID_KEY` 중 하나라도 이름이 있으면(빈 값 포함) `IllegalStateException`으로 기동을 멈춥니다.
  - 대상 이름은 CODEF 코드가 직접 읽는 이름(`CodefApiKeys`, `EnvironmentApprovalFingerprintKeySource`, `EnvironmentConnectedIdKeySource`)입니다. `beone.codef.*` 주소·시간 제한은 비밀값이 아니어서 넣지 않았습니다.
  - 값은 읽지 않습니다(`Environment.containsProperty`로 이름만 확인). 메시지에는 이름만 넣습니다.
  - CODEF 동작·등록은 바꾸지 않았습니다.
- 테스트(`PublicProfileGuardTest`, 16 → 26): 네 변수 각각 prod에서 실패하고 메시지에 이름은 있고 가짜 값은 없음, 운영체제 환경 변수 형태(빈 값 포함)도 감지, prod + 변수 없음(비밀이 아닌 `beone.codef.product-host`만 있음)은 기동, `member`·`local`·`demo`는 변수가 있어도 영향 없음, 검사 이름이 CODEF 코드의 공개 상수와 같음. 가짜 값만 썼습니다.
- 문서: `docs/domains/codef.md` 제약 절에 2줄, `docs/architecture.md` `common.config` 책임 문장. **공개 전환이 승인되면 이 규칙을 걷어내야 합니다.**
- 하지 않은 것: CODEF 동작·등록 변경, 서버·compose 반영, frontend·Android.
- 참고
  - #187의 `docs/domains/codef.md` 「공개(prod) 범위」 절은 아직 `develop`에 없어 이 절 대신 기존 「제약과 후속 작업」 절에 적었습니다. #187 절의 "`PublicProfileGuard`도 프로필 조합만 봅니다" 문장은 이 PR 병합 뒤 사실과 달라지므로, 두 PR 중 나중에 병합되는 쪽의 동기화 때 맞춰야 합니다.
  - 일반 테스트 작업은 `CODEF_CLIENT_ID`·`CODEF_CLIENT_SECRET`만 테스트 환경에서 지웁니다(`build.gradle`). `BEONE_CODEF_*`를 내보낸 셸에서 테스트를 돌리면 prod 기동 테스트가 이 가드에 걸릴 수 있습니다. 빌드 설정은 바꾸지 않았습니다.
- 검증: `./gradlew test webTest --rerun-tasks` 통과(단위 1429, 웹 423, 실패·skip 0). `integrationTest`는 생략했습니다. 바뀐 것은 prod 전용 기동 가드뿐이고, `develop`에는 prod 통합 컨텍스트가 아직 없으며(#190에 있음), 기존 통합 테스트는 이 규칙이 적용되지 않는 `member`·`local`·`demo` 프로필이기 때문입니다.

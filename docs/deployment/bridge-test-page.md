# 브리지 시험 페이지 서버 적용 절차 (release 확인용, 임시)

[브리지 v1 실기기 확인 순서표](../android/device-checklist.md#브리지-v1-실기기-확인-순서표)의 release 항목 BR22~BR24를 위해, 시험 페이지(T, [`tools/bridge-test/`](../../tools/bridge-test/README.md))를 서버의 같은 origin(`https://beonepay.site`) 하위 경로에 **잠시** 올렸다가 지우는 절차입니다. 근거는 2026-11-17 사용자 결정(release 확인용 서버 절차를 준비하고 실제 적용은 사용자가 직접 함)입니다.

## 범위와 현재 상태

- **사용자 실행, 미검증.** 이 문서의 명령은 아직 실제 서버에서 실행·검증되지 않았습니다. 서버 접속과 모든 명령은 사용자가 직접 합니다.
- 하는 것: (a) T를 `/bridge-test/`에 배치, (b) 기존 정적 확인 화면에 임시 링크 추가, (c) BR24용 링크 확인, (d) 시험 뒤 링크·페이지 제거와 원상 복구 확인, (e) 기존 설정·화면 백업과 되돌리기.
- 하지 않는 것: nginx 설정 파일 수정·nginx 재시작이나 다시 읽기(reload)·컨테이너 재생성, 앱 Compose 프로젝트 `beone-app` 조작, 비용이 드는 자원, 보안그룹·DNS·인증서 변경. 하위 경로를 서빙하려면 nginx 설정을 바꿔야 한다고 판명되면 **멈추고** 따로 결정합니다(이 문서에는 설정 수정 절차가 없습니다).
- 서버의 실제 정적 파일 경로·nginx `location` 구성은 저장소에 기록이 없습니다. 아래에서 지어내지 않고 1절 "사전 대조(읽기만)"에서 확인합니다. [백엔드 배포 기반](backend-compose.md#구성)이 적은 서버 구성(`/opt/beone/compose.yaml`의 프로젝트 `beone`, 서비스 `nginx`, `/opt/beone/nginx/`)도 이 절차 전에 다시 확인합니다.
- release 앱은 `https://beonepay.site/`만 열고 주소창이 없습니다. 그래서 시작 화면(정적 확인 화면)에 T로 가는 링크를 잠시 둡니다. T와 링크는 같은 origin이라 앱의 탐색 정책에서 허용됩니다.

## 용어

- **정적 확인 화면**: 지금 `https://beonepay.site/`가 보여 주는 정적 HTML 화면입니다([D-25](../decisions.md#d-25-확정-정책), 사용자 전달). 이 절차에서는 그 파일을 `<정적 루트>/index.html`이라고 부릅니다.
- **정적 루트**: nginx가 `https://beonepay.site/`의 파일을 읽는 곳에 해당하는 **서버 호스트의 폴더**입니다. 1절에서 확인한 값을 셸 변수 `S`에 넣어 씁니다.
- **TS**: 이 회차의 백업 파일 이름에 붙이는 UTC 시각입니다. 새 셸을 열면 기록해 둔 값으로 다시 정의합니다.

## 0. 파일 준비 (PC)

서버에 올리는 파일은 `tools/bridge-test/`의 `index.html`, `frame.html`, `test.js`, `style.css` 네 개입니다(`README.md`는 올리지 않습니다). 올릴 커밋 SHA와 각 파일의 SHA-256을 PC에서 기록합니다.

```bash
git rev-parse HEAD
shasum -a 256 tools/bridge-test/index.html tools/bridge-test/frame.html tools/bridge-test/test.js tools/bridge-test/style.css
```

**전달 경로는 정하지 않았습니다.** [백엔드 배포 기반](backend-compose.md#범위와-현재-상태)에 적힌 대로 현재 접속 방식(Session Manager 셸)만으로는 파일을 옮길 수 없습니다. 네 파일은 작은 텍스트이므로, 비용이 들지 않는 방법(예: 셸에 내용을 붙여 넣어 `sudo tee`로 저장)을 사용자가 고릅니다. 어떤 방법이든 아래 절차는 네 파일이 서버의 업로드 폴더 `U`(예: 사용자 홈 아래 `bridge-test-upload`)에 있다고 가정하고, 3절에서 SHA-256을 대조합니다. 붙여 넣기에서 한글·이모지가 깨지면 대조가 실패하므로 그때는 멈춥니다.

## 1. 사전 대조 (읽기만)

어떤 파일도 바꾸지 않습니다. 결과가 "다르면" 칸에 해당하면 멈추고 확인합니다.

| 명령 | 기대 | 다르면 |
| --- | --- | --- |
| `sudo docker compose ls` | `beone`이 running(`beone-app`이 있으면 그 상태도 기록) | 멈추고 확인 |
| `curl -sI https://beonepay.site/ \| head -1` | `200` | 멈춤 |
| `curl -sI http://beonepay.site/ \| head -1` | `301` | 멈춤 |
| `curl -s -o /dev/null -w '%{http_code}\n' https://beonepay.site/bridge-test/` | `404`. `200`이면 아래 다음 줄로 본문을 확인 | 그 밖의 값이면 멈춤 |
| `curl -s https://beonepay.site/bridge-test/ \| grep -c BeoneBridge` | `0`(이미 T가 올라가 있지 않음. 200이었다면 정적 화면으로 되돌아가는 설정일 수 있으니 기록) | `1` 이상이면 이전 회차가 남은 것이므로 멈추고 9절로 정리 |
| `sudo grep -nE 'server_name\|listen\|location\|root\|alias\|try_files\|return' /opt/beone/nginx/conf.d/*.conf` | `beonepay.site` 443 블록의 `location /`가 `root`(또는 `alias`) 폴더의 파일을 서빙. 컨테이너 안의 그 경로를 기록 | `return`만 있거나 파일 서빙이 아니면 하위 경로에 T를 둘 수 없으므로 멈춤(설정 변경은 이 문서 범위 밖) |
| `sudo grep -nE 'volumes\|- .*:' /opt/beone/compose.yaml` | 위 컨테이너 경로에 연결된 **호스트 폴더**(디렉터리 바인드 마운트)를 찾음. 이 호스트 폴더가 정적 루트 | 정적 파일이 이미지 안에만 있거나(마운트 없음) 파일 하나만 마운트돼 있으면 멈춤 |
| `curl -sI https://beonepay.site/ \| grep -iE 'content-security-policy\|x-frame-options\|x-content-type-options'` | 값을 기록. T는 같은 origin의 스크립트·스타일만 쓰므로 `script-src 'self'`를 막는 CSP가 없어야 함 | 같은 origin 스크립트를 막는 CSP가 있으면 멈춤 |

정적 루트를 확인했으면 변수로 정의하고 확인합니다(값은 위 대조에서 얻은 호스트 경로이며 이 문서는 값을 정하지 않습니다).

```bash
S=<사전 대조로 확인한 정적 루트 호스트 경로>
sudo ls -la "$S"
sudo grep -c '</body>' "$S/index.html"
```

| 확인 | 기대 | 다르면 |
| --- | --- | --- |
| `ls` | `index.html`이 있고 `bridge-test`가 없음 | `bridge-test`가 있으면 멈추고 9절로 정리 |
| `grep -c '</body>'` | `1` | `0`이나 2 이상이면 4절의 자동 삽입을 쓰지 않고 멈춤 |

- `X-Frame-Options: DENY`나 같은 뜻의 CSP가 있으면 T 안의 같은 origin iframe이 비어 보일 수 있습니다. release 순서표(BR22~BR24)는 iframe을 쓰지 않으므로 기록만 합니다.

## 2. 백업 (e)

정적 확인 화면 파일과 기존 서비스 설정을 이 회차 이름으로 보존하고, 나중에 바뀌지 않았는지 대조할 SHA-256을 남깁니다. certbot 폴더의 개인키는 복사하지 않습니다.

```bash
TS=$(date -u +%Y%m%dT%H%M%SZ); echo "$TS"
sudo install -d -m 700 /opt/beone-backup
sudo tar -czf "/opt/beone-backup/pre-bridge-test-$TS.tgz" /opt/beone/compose.yaml /opt/beone/nginx/conf.d "$S/index.html"
sudo cp -p "$S/index.html" "/opt/beone-backup/index.html.pre-bridge-test-$TS"
sudo sh -c "sha256sum '$S/index.html' > '/opt/beone-backup/pre-bridge-test-$TS.index.sha256'"
sudo sh -c "sha256sum /opt/beone/compose.yaml /opt/beone/nginx/conf.d/* > '/opt/beone-backup/pre-bridge-test-$TS.config.sha256'"
sudo tar -tzf "/opt/beone-backup/pre-bridge-test-$TS.tgz"
sudo ls -l /opt/beone-backup
```

| 확인 | 기대 | 다르면 |
| --- | --- | --- |
| `echo "$TS"` | 시각 출력. 이 값을 기록 | — |
| `tar -tzf` | `compose.yaml`, `nginx/conf.d/…`, 정적 확인 화면 `index.html`이 목록에 있음 | 멈춤(다음 단계로 가지 않음) |
| `ls -l` | 위 tgz, `index.html.pre-bridge-test-<TS>`, 두 `.sha256` 파일이 있음 | 멈춤 |

## 3. T 배치 (a)

```bash
sudo sha256sum "$U/index.html" "$U/frame.html" "$U/test.js" "$U/style.css"
sudo install -d -m 755 "$S/bridge-test"
sudo install -m 644 "$U/index.html" "$U/frame.html" "$U/test.js" "$U/style.css" "$S/bridge-test/"
```

| 확인 | 기대 | 다르면 |
| --- | --- | --- |
| 업로드 파일 `sha256sum` | 0절에서 PC로 기록한 값과 네 파일 모두 같음 | 배치하지 않고 멈춤 |
| `curl -s -o /dev/null -w '%{http_code}\n' https://beonepay.site/bridge-test/` | `200` | 9절로 정리하고 멈춤(하위 경로 서빙에 설정 변경이 필요할 수 있음) |
| `curl -s https://beonepay.site/bridge-test/ \| grep -c BeoneBridge` | `1` 이상 | 9절로 정리하고 멈춤 |
| `curl -sI https://beonepay.site/bridge-test/test.js \| grep -i '^content-type'` | `javascript`가 들어간 형식 | `text/plain` 등이면 스크립트가 막힐 수 있으므로 9절로 정리하고 멈춤 |
| `curl -s -o /dev/null -w '%{http_code}\n' https://beonepay.site/bridge-test/frame.html` | `200` | 기록(release 순서표는 iframe을 쓰지 않음) |
| `curl -sI https://beonepay.site/ \| head -1` | `200`, 정적 확인 화면 그대로 | 9절로 정리하고 멈춤 |

## 4. 정적 확인 화면에 임시 링크 (b)

`index.html`의 `</body>` 바로 앞에 링크 한 줄을 넣습니다. 수정본을 백업 폴더에 먼저 만든 뒤 `cp`로 원래 파일에 덮어씁니다. `sed -i`는 파일을 새로 만들어 바꾸므로 쓰지 않습니다(파일 단위 마운트였다면 컨테이너가 새 파일을 보지 못할 수 있습니다).

```bash
sudo sh -c "sed 's#</body>#<p><a href=\"/bridge-test/\">브리지 시험(임시, 시험 뒤 제거)</a></p></body>#' '$S/index.html' > '/opt/beone-backup/index.html.with-link-$TS'"
sudo diff "/opt/beone-backup/index.html.pre-bridge-test-$TS" "/opt/beone-backup/index.html.with-link-$TS"
sudo cp "/opt/beone-backup/index.html.with-link-$TS" "$S/index.html"
curl -s https://beonepay.site/ | grep -c '/bridge-test/'
```

| 확인 | 기대 | 다르면 |
| --- | --- | --- |
| `diff` | `</body>`가 있던 한 줄만 바뀜 | `cp`하지 않고 멈춤 |
| `grep -c '/bridge-test/'` | `1` | 9절로 정리하고 멈춤 |
| `curl -sI https://beonepay.site/ \| head -1` | `200` | 9절로 정리하고 멈춤 |

## 5. BR24 링크 확인 (c)

BR24의 링크 `https://www.beonepay.site/`와 `http://beonepay.site/`는 T(`index.html`의 "BR24" 절)에 들어 있으므로 서버에서 따로 만들 것이 없습니다. 올라간 T에 있는지만 확인합니다.

```bash
curl -s https://beonepay.site/bridge-test/ | grep -cE 'href="https://www\.beonepay\.site/"|href="http://beonepay\.site/"'
```

기대: `2`. 다르면 9절로 정리하고 멈춥니다. 이 링크를 서버나 PC 브라우저에서 열어 보는 것은 시험이 아닙니다. 차단 여부는 release 앱에서 BR24로 확인합니다.

## 6. 시험 (release 앱)

release 앱을 열어 시작 화면의 "브리지 시험(임시)" 링크로 T에 들어간 뒤, 순서표 BR22~BR24를 진행합니다. 결과 기록은 순서표 "기록" 규칙을 따릅니다. 앱이 이전 화면을 캐시로 보여 주면 앱을 완전히 종료한 뒤 다시 열고, 그래도 링크가 없으면 그 사실을 기록합니다.

## 7. 제거와 원상 복구 (d)

시험이 끝나면 바로 되돌립니다. 링크를 먼저 없애고 페이지를 지웁니다. 폴더째 지우는 재귀 삭제는 쓰지 않고, 올린 파일 네 개를 이름으로 지운 뒤 빈 폴더를 `rmdir`로 지웁니다.

```bash
sudo cp "/opt/beone-backup/index.html.pre-bridge-test-$TS" "$S/index.html"
sudo sha256sum -c "/opt/beone-backup/pre-bridge-test-$TS.index.sha256"
sudo rm "$S/bridge-test/index.html" "$S/bridge-test/frame.html" "$S/bridge-test/test.js" "$S/bridge-test/style.css"
sudo rmdir "$S/bridge-test"
```

`rmdir`이 비어 있지 않다고 실패하면 다른 파일이 생긴 것이므로 `sudo ls -la "$S/bridge-test"`로 확인하고 멈춥니다(남은 파일을 일괄 삭제하지 않습니다).

## 8. 원상 복구 확인 (d)

| 명령 | 기대 | 다르면 |
| --- | --- | --- |
| `sudo sha256sum -c "/opt/beone-backup/pre-bridge-test-$TS.index.sha256"` | `OK` | 백업본으로 다시 `cp` 후 재확인, 그래도 다르면 멈춤 |
| `sudo sha256sum -c "/opt/beone-backup/pre-bridge-test-$TS.config.sha256"` | 모두 `OK`(이 절차는 설정을 바꾸지 않음) | 멈추고 9절 |
| `curl -s https://beonepay.site/ \| grep -c '/bridge-test/'` | `0` | 멈춤 |
| `curl -s -o /dev/null -w '%{http_code}\n' https://beonepay.site/bridge-test/` | 1절과 같은 값(보통 `404`) | 멈춤 |
| `curl -s https://beonepay.site/bridge-test/ \| grep -c BeoneBridge` | `0` | 멈춤 |
| `curl -sI https://beonepay.site/ \| head -1` · `curl -sI http://beonepay.site/ \| head -1` | `200` · `301` | 멈춤 |
| `sudo docker compose ls` | 1절과 같음 | 멈추고 확인 |
| release 앱 다시 열기 | 시작 화면에 임시 링크 없음 | 앱 캐시일 수 있으니 완전히 종료 후 재확인, 그래도 보이면 서버 응답부터 다시 확인 |

백업 폴더의 파일(`pre-bridge-test-<TS>.*`, `index.html.*-<TS>`)과 업로드 폴더 `U`는 이 절차에서 지우지 않습니다. 정리는 원상 복구를 확인한 뒤 따로 판단합니다.

## 9. 되돌리기 (e, 문제가 생겼을 때)

- **어느 단계에서든 멈추면** 지금까지 한 만큼만 되돌립니다. 4절을 했다면 7절의 첫 두 줄(정적 확인 화면 복구와 대조), 3절을 했다면 7절의 나머지(올린 파일 4개 삭제와 `rmdir`)를 하고 8절로 확인합니다.
- **설정 대조가 다를 때**(8절 `config.sha256` 실패): 이 절차는 설정 파일을 건드리지 않으므로, 다른 작업이 겹쳤을 수 있습니다. 바로 덮어쓰지 말고 백업을 별도 폴더에 꺼내 비교한 뒤 판단합니다. nginx를 다시 읽게 하거나 재시작하는 것은 이 문서 범위 밖이므로 따로 결정합니다.

```bash
sudo install -d -m 700 "/opt/beone-backup/compare-$TS"
sudo tar -xzf "/opt/beone-backup/pre-bridge-test-$TS.tgz" -C "/opt/beone-backup/compare-$TS"
sudo diff -r "/opt/beone-backup/compare-$TS/opt/beone/nginx/conf.d" /opt/beone/nginx/conf.d
sudo diff "/opt/beone-backup/compare-$TS/opt/beone/compose.yaml" /opt/beone/compose.yaml
```

- 이전 회차가 남아 있을 때(1절에서 `bridge-test`가 이미 있음): 그 회차의 TS로 7·8절을 먼저 마칩니다. 그 회차의 백업이 없으면 정적 확인 화면을 추정으로 고치지 말고 멈춥니다.

## 기록

회차마다 다음을 남깁니다: TS, 올린 커밋 SHA와 네 파일의 SHA-256, 1절의 응답 코드·헤더 기록, 각 단계의 기대 일치 여부, 시험 날짜, 7·8절 원상 복구 결과. 비밀값·개인키·`.env` 내용·기기 식별 정보는 적지 않습니다.

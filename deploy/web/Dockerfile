# 웹 배포 이미지: frontend 빌드 결과(dist)만 담는다. 실행하는 이미지가 아니다(결정 40 인터페이스 계약).
# 빌드 컨텍스트는 frontend/dist이다(.github/workflows/deploy.yml). 서버 배포 스크립트는 이 이미지에서
# /dist를 꺼내 릴리스 디렉터리에 두고 심볼릭 링크를 바꾼다. 웹 서버는 기존 공개 nginx가 맡는다.
# scratch 기반이라 셸·패키지가 없다. 비밀값은 넣지 않는다(frontend 빌드 결과에는 공개 정적 파일만 있다).
FROM scratch
LABEL org.opencontainers.image.title="beone-web" \
	org.opencontainers.image.source="https://github.com/beone-capston/beone-mvp" \
	org.opencontainers.image.description="BeONE frontend dist only (not runnable)"
COPY . /dist/
# /dist 위치는 서버 deploy.sh의 web dist 경로와 같아야 한다(결정 40 계약, 관리 확정).
# docker create가 명령 없는 이미지를 거부하므로 자리표시 명령만 둔다. 실행하지 않으며 실행해도 실패한다.
CMD ["/not-runnable"]

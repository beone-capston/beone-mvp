# syntax=docker/dockerfile:1
# 백엔드 배포 이미지.
# 빌드 단계는 빌드 장비의 플랫폼에서 실행해 Gradle을 에뮬레이션하지 않는다(Java 바이트코드는 플랫폼과 무관하다).
# 실행 단계만 대상 플랫폼으로 만든다. 서버는 linux/amd64이므로 deploy/scripts/build-image.sh가 --platform linux/amd64로 빌드한다.
# 비밀값은 ARG·ENV·파일로 넣지 않는다. DB 접속 정보는 실행할 때 환경변수로 받는다(deploy/compose.yaml).
# 소스 리비전 라벨은 빌드 스크립트가 커밋된 깨끗한 소스에서만 --label로 붙인다.

ARG JDK_IMAGE=eclipse-temurin:21-jdk-noble
ARG JRE_IMAGE=eclipse-temurin:21-jre-noble

FROM --platform=$BUILDPLATFORM ${JDK_IMAGE} AS build
WORKDIR /workspace

# 의존성 계층과 소스 계층을 나눠 소스만 바뀌었을 때 다시 받지 않게 한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet dependencies --configuration runtimeClasspath > /dev/null

# 실행 jar만 만든다. 테스트는 이미지 밖에서 ./gradlew test webTest integrationTest로 따로 실행한다.
COPY src/main src/main
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet bootJar \
	&& find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print > /tmp/jars \
	&& [ "$(wc -l < /tmp/jars)" -eq 1 ] \
	&& cp "$(cat /tmp/jars)" /workspace/app.jar

FROM ${JRE_IMAGE}
LABEL org.opencontainers.image.title="beone-api" \
	org.opencontainers.image.source="https://github.com/beone-capston/beone-mvp"

# root가 아닌 전용 사용자로 실행한다.
RUN groupadd --system beone && useradd --system --gid beone --no-create-home --shell /usr/sbin/nologin beone
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER beone

# 컨테이너 내부 포트 안내일 뿐 호스트에 공개하지 않는다.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

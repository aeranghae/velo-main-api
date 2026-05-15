FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# GitHub Actions가 빌드해서 넘겨준 JAR 파일 복사
COPY build/libs/main-api-*.jar app.jar

# [핵심] 쿠버네티스가 Secret으로 /app/config 폴더에 yml 파일들을 꼽아줄 것이므로, 해당 위치를 바라보게 설정
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/config/application.yml,file:/app/config/application-oauth.yml"]
package cloud.velo.main.util.template.initializer.framework;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.util.template.BaseTemplateInitializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Slf4j
@Component
public class SpringBootTemplateInitializer extends BaseTemplateInitializer {

    @Override
    public String getSupportedFramework() { return "spring-boot"; }

    @Override
    public void initTemplate(Path rootPath, ProjectCreateRequestDto dto) {
        String projectName = dto.getProjectName().toLowerCase();
        String basePath = "src/main/java/com/" + projectName;

        // 1. 디렉토리 구조 생성 (기존 폴더 구조에 gradle/wrapper 구역 추가)
        createDirectories(rootPath, List.of(
                basePath + "/controller",
                basePath + "/service",
                basePath + "/repository",
                basePath + "/domain",
                basePath + "/dto",
                basePath + "/config",
                basePath + "/exception",
                "src/main/resources/static",
                "src/test/java/com/" + projectName,
                "gradle/wrapper" // 🚨 Gradle Wrapper가 안착할 하위 디렉토리 선언
        ));

        String className = capitalize(projectName);

        // 2. 기존 텍스트 파일 생성 공정
        writeFile(rootPath.resolve(basePath + "/" + className + "Application.java"),
                applicationJava(projectName, className));
        writeFile(rootPath.resolve("src/main/resources/application.yml"),
                applicationYml(projectName));
        writeFile(rootPath.resolve("build.gradle"),
                buildGradle(projectName, dto.getLicense()));
        writeFile(rootPath.resolve("settings.gradle"),
                "rootProject.name = '" + projectName + "'\n");
        writeFile(rootPath.resolve(".gitignore"),
                springGitignore());
        writeFile(rootPath.resolve("README.md"),
                readme(projectName, dto.getLicense()));

        // 3. Gradle Wrapper 리소스 파일 수혈 및 도커 가드 권한 주입 공정 시작
        injectGradleWrapper(rootPath);
    }

    /**
     * 리소스 영역의 Gradle Wrapper 파일 세트를 유저 격리 스토리지로 복사하고 실행 권한을 부여합니다.
     */
    private void injectGradleWrapper(Path rootPath) {
        try {
            // ① gradlew 텍스트 스크립트 복사
            copyResourceFile("templates/gradle-wrapper/gradlew", rootPath.resolve("gradlew"));
            // ② gradlew.bat 윈도우용 스크립트 복사
            copyResourceFile("templates/gradle-wrapper/gradlew.bat", rootPath.resolve("gradlew.bat"));
            // ③ gradle-wrapper.properties 설정 주입
            copyResourceFile("templates/gradle-wrapper/gradle/wrapper/gradle-wrapper.properties",
                    rootPath.resolve("gradle/wrapper/gradle-wrapper.properties"));
            // ④ 핵심 바이너리 gradle-wrapper.jar 파일 카피
            copyResourceFile("templates/gradle-wrapper/gradle/wrapper/gradle-wrapper.jar",
                    rootPath.resolve("gradle/wrapper/gradle-wrapper.jar"));

            // ⚠️ [리눅스/도커 가드 조치]: 복사된 gradlew 파일에 실물 실행 권한 강제 부여
            File gradlewFile = rootPath.resolve("gradlew").toFile();
            if (gradlewFile.exists()) {
                // 첫 번째 인자 true(실행 가능), 두 번째 인자 false(모든 그룹/유저 권한 오픈 - rwxr-xr-x)
                boolean success = gradlewFile.setExecutable(true, false);
                if (success) {
                    log.info("[Automation] 샌드박스 컴파일용 gradlew 실행 권한(CHMOD +X) 부여 대성공");
                } else {
                    log.warn("[Automation] gradlew 권한 변경 실패 (권한 제한 환경 우려)");
                }
            }

        } catch (Exception e) {
            log.error("[Automation] Gradle Wrapper 자원 주입 도중 치명적 예외 발생", e);
            throw new RuntimeException("프로젝트 초기화 인프라 주입에 실패했습니다.", e);
        }
    }

    /**
     * JAR 내부 리소스에 갇혀있는 스트림을 뽑아내어 실물 NFS 경로에 파일로 이식합니다.
     */
    private void copyResourceFile(String resourcePath, Path targetPath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String applicationJava(String projectName, String className) {
        return """
                package com.%s;
                
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                
                @SpringBootApplication
                public class %sApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(%sApplication.class, args);
                    }
                }
                """.formatted(projectName, className, className);
    }

    private String applicationYml(String projectName) {
        return """
                spring:
                  application:
                    name: %s
                  datasource:
                    url: jdbc:mysql://localhost:3306/%s
                    username: root
                    password:
                    driver-class-name: com.mysql.cj.jdbc.Driver
                  jpa:
                    hibernate:
                      ddl-auto: update
                    show-sql: true
                    properties:
                      hibernate:
                        format_sql: true
                
                server:
                  port: 8080
                
                logging:
                  level:
                    com.%s: DEBUG
                """.formatted(projectName, projectName, projectName);
    }

    private String buildGradle(String projectName, String license) {
        return """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '4.0.6'
                    id 'io.spring.dependency-management' version '1.1.7'
                }
                
                group = 'com.%s'
                version = '0.0.1-SNAPSHOT'
                
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }
                
                repositories {
                    mavenCentral()
                }
                
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
                    implementation 'org.springframework.boot:spring-boot-starter-validation'
                    implementation 'org.springframework.boot:spring-boot-starter-security'
                    compileOnly 'org.projectlombok:lombok'
                    runtimeOnly 'com.mysql:mysql-connector-j'
                    annotationProcessor 'org.projectlombok:lombok'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                
                tasks.named('test') {
                    useJUnitPlatform()
                }
                """.formatted(projectName);
    }

    private String springGitignore() {
        return """
                HELP.md
                .gradle/
                build/
                !gradle/wrapper/gradle-wrapper.jar
                !**/src/main/**/build/
                !**/src/test/**/build/
                .idea/
                *.iws
                *.iml
                *.ipr
                out/
                .DS_Store
                *.env
                application-local.yml
                """;
    }

    private String readme(String projectName, String license) {
        return """
                # %s
                
                ## Getting Started
                
                ### Prerequisites
                - Java 21
                - MySQL
                
                ### Run
```bash
                ./gradlew bootRun
```
                
                ## License
                %s
                """.formatted(projectName, license);
    }
}
package cloud.velo.main.util.template.initializer.framework;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.util.template.BaseTemplateInitializer;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class SpringBootTemplateInitializer extends BaseTemplateInitializer {

    @Override
    public String getSupportedFramework() { return "spring-boot"; }

    @Override
    public void initTemplate(Path rootPath, ProjectCreateRequestDto dto) {
        String projectName = dto.getProjectName().toLowerCase();
        String basePath = "src/main/java/com/" + projectName;

        createDirectories(rootPath, List.of(
                basePath + "/controller",
                basePath + "/service",
                basePath + "/repository",
                basePath + "/domain",
                basePath + "/dto",
                basePath + "/config",
                basePath + "/exception",
                "src/main/resources/static",
                "src/test/java/com/" + projectName
        ));

        String className = capitalize(projectName);

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
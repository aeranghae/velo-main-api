package cloud.velo.main.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequestDto {
    private String projectName;      // 프로젝트 이름

    private String framework;        // React, Spring Boot 등 (*제거예정)
    private String language;         // Java, TypeScript, Python 등 (*제거예정)

    private String architecture_type; // FULL_STACK, CLIENT_SERVER

    private String fullstack_framework;
    private String backend_framework;
    private String frontend_framework;

    private String fullstack_language;
    private String backend_language;
    private String frontend_language;

    private String database;

    private String license;          // MIT, Apache 2.0 등
    private String model;            // 사용할 ai 모델
    private String prompt;           // 사용자의 요구사항 (상세 기능 설명)
}
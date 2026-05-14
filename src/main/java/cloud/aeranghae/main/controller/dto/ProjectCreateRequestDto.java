package cloud.aeranghae.main.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequestDto {
    private String projectName;      // 프로젝트 이름
    private String framework;        // React, Spring Boot 등
    private String language;         // Java, TypeScript, Python 등
    private String license;          // MIT, Apache 2.0 등
    private String model;            // 사용할 ai 모델
    private String prompt;           // 사용자의 요구사항 (상세 기능 설명)
}
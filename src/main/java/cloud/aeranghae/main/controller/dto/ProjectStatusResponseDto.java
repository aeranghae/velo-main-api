package cloud.aeranghae.main.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProjectStatusResponseDto {
    private String projectId;
    private String status;      // PENDING, PROCESSING, COMPLETED, ERROR
    private int progress;       // 0 ~ 100
    private String message;     // "코드 생성 중...", "파일 저장 중..." 등
}
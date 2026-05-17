package cloud.velo.main.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponseDto {
    private String projectName;   // 유저가 설정한 실제 프로젝트 이름 (화면 표시용)
    private String uuid;          // 서버와 통신할 때 사용할 고유 식별자 (물리 폴더명)
    private String framework;
    private String model;
    private String createdAt;     // 프로젝트 생성일
    private String lastModified;  // 마지막 파일 수정일
    private long size;            // 프로젝트 총 용량 (Bytes)
    private int fileCount;        // 프로젝트 내 총 파일 개수
}
package cloud.velo.main.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor // 💡 롬복 빌더가 정상 작동하기 위해 전제되는 생성자 어노테이션
public class ProjectLogResponseDto {
    private String uuid;

    // status 필드가 선언되어 있어야 Service 레이어의 빌더가 목적지를 찾습니다.
    private String status;             // GENERATING, COMPLETED, FAILED

    private String framework;
    private String previousLogs;       // 과거 누적 로그 합산 텍스트
    private String statusDescription;  // "AI 소스 코드 생성 중"
    private int progress;              // % 추가

}
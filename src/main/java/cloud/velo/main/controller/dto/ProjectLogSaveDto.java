package cloud.velo.main.controller.dto;

import lombok.Getter;
import lombok.Setter;

// FastAPI에서 받은 데이터 받는 용도
@Getter
@Setter
public class ProjectLogSaveDto {
    private String uuid; // 사용자의 아이디
    private String logLevel; // info / debug / error
    private String message;
    private String status;  // GENERATING, COMPLETED, FAILED
}
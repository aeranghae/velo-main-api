package cloud.velo.main.controller.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

// FastAPI에서 받은 데이터 받는 용도
@Getter
@Setter
public class ProjectLogSaveDto {
    private String uuid; // 사용자의 아이디
    private List<Map<String, Object>> logs; // 로그를 여러줄로 한번에 받음
    private String status;  // GENERATING, COMPLETED, FAILED
    private String logLevel;
    private String message;
    private boolean isActivityFeed;
}
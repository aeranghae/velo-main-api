package cloud.velo.main.controller;

import cloud.velo.main.controller.dto.ProjectLogSaveDto;
import cloud.velo.main.service.ProjectLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectLogController {

    // 스프링 컨테이너가 관리하는 진짜 객체 인스턴스를 소문자로 선언합니다.
    private final ProjectLogService projectLogService;

    /**
     * 3. FastAPI 워커 전용 로그 수신 웹훅 (POST)
     * 주소: POST /api/projects/webhook/logs
     */
    @PostMapping("/webhook/logs")
    public ResponseEntity<Void> receiveLogFromFastApi(@RequestBody ProjectLogSaveDto dto) {

        projectLogService.saveWorkerLog(dto);

        return ResponseEntity.ok().build();
    }
}
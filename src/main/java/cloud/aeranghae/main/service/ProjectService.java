package cloud.aeranghae.main.service;

import cloud.aeranghae.main.controller.dto.ProjectCreateRequestDto;
import cloud.aeranghae.main.controller.dto.ProjectStatusResponseDto;
import cloud.aeranghae.main.domain.User;
import cloud.aeranghae.main.repository.ProjectRepository;
import cloud.aeranghae.main.util.storage.DirectoryTreeBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DirectoryTreeBuilder directoryTreeBuilder;
    // private final RestTemplate restTemplate; // FastAPI 호출용

    @Value("${llm.server.url:http://localhost:8000}")
    private String llmServerUrl;

    @Value("${aeranghae.storage.path}")
    private String baseStoragePath;

    /**
     * 프로젝트 자동화 생성 프로세스 시작
     */
    @Async // 시간이 걸리는 작업이므로 비동기 처리
    public void initiateAutomation(User user, String projectId, ProjectCreateRequestDto details) {
        try {
            log.info("프로젝트 생성 자동화 시작: {}", details.getProjectName());

            //TODO: 프로젝트 트리구조를 받아서 같이 전달해줘야 함
            Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), projectId);
            String tree = directoryTreeBuilder.build(projectPath);

            // 1. FastAPI 서버에 전달할 데이터 구성
            Map<String, Object> llmRequest = new HashMap<>();
            llmRequest.put("prompt", details.getPrompt());
            llmRequest.put("framework", details.getFramework());
            llmRequest.put("language", details.getLanguage());
            llmRequest.put("license", details.getLicense());
            llmRequest.put("model", details.getModel());
            llmRequest.put("projectId", projectId);
            llmRequest.put("tree", tree);

            // 2. FastAPI 호출 (LLM을 통해 코드 구조 생성)
            //ResponseEntity<String> response = restTemplate.postForEntity(llmServerUrl + "/generate", llmRequest, String.class);

            // 3. 받은 데이터(JSON 등)를 바탕으로 파일 생성 로직 실행
            //createFileStructures(projectId, response.getBody());

            log.info("프로젝트 생성 자동화 완료: {}", projectId);
        } catch (Exception e) {
            // 오류가 발생하면 기존 작업을 종료하는 프로세스 혹은 재요청하는 프로세스 필요
            // 프로젝트 폴더삭제 등등
            // 혹은 호출 로그 기록 바탕으로 재요청 진행 (max 3회 실패시 프로젝트 파기)
            log.error("자동화 프로세스 중 오류 발생: {}", e.getMessage());
            // 필요한 경우 DB에 상태를 'ERROR'로 업데이트
        }
    }


    // 아래는 테스트용 메서드 입니다.

    // 실무에서는 Redis나 DB를 사용하지만, 테스트용으로 메모리에 상태를 저장해봅니다.
    private final Map<String, ProjectStatusResponseDto> statusCache = new ConcurrentHashMap<>();

    /**
     * 프로젝트 생성 상태 조회
     */
    public ProjectStatusResponseDto checkStatus(String projectId) {
        // 해당 ID의 상태가 없으면 초기 상태 반환
        return statusCache.getOrDefault(projectId,
                new ProjectStatusResponseDto(projectId, "PENDING", 0, "작업 대기 중"));
    }

    /**
     * 프로젝트 자동화 프로세스 (진행 상황 업데이트 추가 버전)
     */
    @Async
    public void TestInitiateAutomation(String projectId, ProjectCreateRequestDto details) {
        try {
            // 1. 시작 단계
            updateStatus(projectId, "PROCESSING", 10, "프로젝트 구조 설계 중...");
            // 과함께 상세 실시간 로그 확인

            // 2. FastAPI 호출 시뮬레이션
            Thread.sleep(2000); // 작업 시간 대기
            updateStatus(projectId, "PROCESSING", 50, "LLM 서버에서 소스 코드 생성 중...");
            // 과함께 상세 실시간 로그 확인

            // 3. 파일 쓰기 단계
            Thread.sleep(3000);
            updateStatus(projectId, "PROCESSING", 80, "서버 로컬 디렉토리에 파일 저장 중...");
            // 과함께 상세 실시간 로그 확인

            // 4. 완료
            updateStatus(projectId, "COMPLETED", 100, "프로젝트 생성이 완료되었습니다!");
            // 과함께 상세 실시간 로그 확인

        } catch (Exception e) {
            updateStatus(projectId, "ERROR", 0, "오류 발생: " + e.getMessage());
        }
    }

    // 상태 업데이트용 헬퍼 메소드
    private void updateStatus(String projectId, String status, int progress, String message) {
        statusCache.put(projectId, new ProjectStatusResponseDto(projectId, status, progress, message));
    }

}
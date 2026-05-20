package cloud.velo.main.docker.websocket;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.docker.service.DockerAgentService;
import cloud.velo.main.service.ProjectLogService;
import cloud.velo.main.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConnectionManager {

    private final DockerAgentService dockerAgentService;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;
    private final ProjectLogService projectLogService;

    @Value("${velo.storage.path}")
    private String baseStoragePath; // 주입받은 기본 경로 활용

    // ⭐️ 주입 문법 교정: 프로퍼티 값을 정상적으로 주입받기 위해 ${} 가드 추가
    @Value("${llm.server.ws:ws://localhost:8000}")
    private String serverUrl;

    // 현재 활성화된 프로젝트별 웹소켓 매니저 저장소 (키값을 uuid로 통일)
    private final Map<String, WebSocketConnectionManager> activeConnections = new ConcurrentHashMap<>();

    /**
     * 프로젝트 생성 시작 시 호출 (uuid를 마스터 키로 사용)
     */
    public void startProjectGeneration(String userId, String uuid, String email, String baseImage, ProjectCreateRequestDto requestDto) {
        // 1. 디렉토리 검증 및 세팅 (userdir/userid/uuid 구조 충실 반영)
        String hostPath = baseStoragePath + userId + "/" + uuid;
        File directory = new File(hostPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        log.info("[Manager] 프로젝트 초기 저장소 확인 완료. 경로: {}", hostPath);

        // 2. 이 세션(UUID)만을 전용으로 담당할 독립된 웹소켓 핸들러 객체 생성
        LlmAgentClient dynamicHandler = new LlmAgentClient(
                dockerAgentService,
                objectMapper,
                storageService,
                userId,
                uuid,
                email,
                baseImage,
                requestDto,
                projectLogService);

        // 3. ⭐️ 하드코딩 제거: 주입받은 serverUrl 변수를 뼈대로 동적 URL 구성
        // serverUrl 값 끝에 '/' 유무에 대비해 유연하게 붙도록 처리 가능 (예: serverUrl이 ws://localhost:8000 일 때)
        String cleanedUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        String fastapiWsUrl = String.format("%s/agent?uuid=%s", cleanedUrl, uuid);

        log.info("[Manager] FastAPI 에이전트 독립 소켓 연결 시도 URL: {}", fastapiWsUrl);

        // 4. 독립된 웹소켓 커넥션 풀 가동
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(client, dynamicHandler, fastapiWsUrl);

        // 관리 대장에 등록 후 연결 시작
        activeConnections.put(uuid, connectionManager);
        connectionManager.start();
    }

    /**
     * 작업이 완료되었을 때 소켓을 닫고 대장에서 제거
     */
    public void stopProjectGeneration(String uuid) {
        WebSocketConnectionManager manager = activeConnections.get(uuid);
        if (manager != null) {
            manager.stop();
            activeConnections.remove(uuid);
            log.info("[Manager] 프로젝트 세션 종료 및 웹소켓 반환 완료. UUID: {}", uuid);
        }
    }
}
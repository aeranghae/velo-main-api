package cloud.velo.main.client;

import cloud.velo.main.dto.request.ProjectCreateRequest;
import cloud.velo.main.event.ProjectDeleteVerificationEvent;
import cloud.velo.main.exception.ProjectActiveSessionException;
import cloud.velo.main.service.DockerAgentService;
import cloud.velo.main.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;

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
    private final ApplicationEventPublisher eventPublisher;

    @Value("${velo.storage.path}")
    private String baseStoragePath;

    @Value("${llm.server.ws:ws://localhost:8000}")
    private String serverUrl;

    private final Map<String, WebSocketConnectionManager> activeConnections = new ConcurrentHashMap<>();

    /**
     * 프로젝트 생성 시작 시 호출 (uuid를 마스터 키로 사용)
     */
    public void startProjectGeneration(String userId, String uuid, String email, String baseImage, ProjectCreateRequest requestDto) {
        File userDir = new File(baseStoragePath, userId);
        File baseDirFile = new File(userDir, uuid);

        // [인프라 방어 가드] 폴더가 없고 생성에도 실패했다면, 뒤에서 터질 추가적인 예외를 방지하기 위해 사전에 인프라 예외
        if (!baseDirFile.exists() && !baseDirFile.mkdirs()) {
            throw new IllegalStateException("NFS 파일 저장소 물리 디렉토리 생성에 실패했습니다. 경로: " + baseDirFile.getAbsolutePath());
        }

        log.info("[Manager] 프로젝트 초기 저장소 확인 완료. 경로: {}", baseDirFile.getAbsolutePath());

        // 독립 웹소켓 핸들러 객체 생성
        LlmAgentClient dynamicHandler = new LlmAgentClient(
                dockerAgentService,
                objectMapper,
                storageService,
                userId,
                uuid,
                email,
                baseImage,
                requestDto,
                eventPublisher);

        String cleanedUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        String fastapiWsUrl = String.format("%s/agent?uuid=%s", cleanedUrl, uuid);

        log.info("[Manager] FastAPI 에이전트 독립 소켓 연결 시도 URL: {}", fastapiWsUrl);

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(client, dynamicHandler, fastapiWsUrl);

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

    public boolean isProjectGenerating(String uuid) {
        return activeConnections.containsKey(uuid);
    }

    /**
     * 프로젝트 삭제 검증 이벤트 리스너
     */
    @EventListener
    public void verifyProjectNotActive(ProjectDeleteVerificationEvent event) {
        if (isProjectGenerating(event.uuid())) {
            if (event.isGenerating() != null) {
                event.isGenerating().set(true);
            } else {
                // 커스텀 예외 처리로 변경
                throw new ProjectActiveSessionException("현재 AI 자동화 공정이 진행 중인 프로젝트이므로 삭제할 수 없습니다. UUID: " + event.uuid());
            }
        }
    }
}
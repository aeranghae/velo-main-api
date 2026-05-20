package cloud.velo.main.docker.websocket;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.controller.dto.ProjectNodeResponse;
import cloud.velo.main.docker.dto.AiModelMessage;
import cloud.velo.main.docker.service.DockerAgentService;
import cloud.velo.main.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class LlmAgentClient extends TextWebSocketHandler {

    private final DockerAgentService dockerAgentService;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    // 각 소켓 연결마다 고유하게 유지될 프로젝트 컨텍스트 정보
    private final String userId;
    private final String uuid;
    private final String email;
    private final String baseImage;
    private final ProjectCreateRequestDto requestDto;

    // 무한 루프 폭주 방지를 위한 안전 장치 (최대 50턴 제한)
    private static final int MAX_TURN_LIMIT = 50;
    private int executionTurnCount = 0;

    // 이 세션(웹소켓 파이프라인) 동안 점진적 작업을 수행할 도커 컨테이너 고유 ID
    private String registeredContainerId;

    public LlmAgentClient(DockerAgentService dockerAgentService, ObjectMapper objectMapper, StorageService storageService,
                          String userId, String uuid, String email, String baseImage, ProjectCreateRequestDto requestDto) {
        this.dockerAgentService = dockerAgentService;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.userId = userId;
        this.uuid = uuid;
        this.email = email;
        this.baseImage = baseImage;
        this.requestDto = requestDto;
    }

    /**
     * FastAPI(LLM) 서버와 웹소켓 파이프라인이 최초로 연결되었을 때 실행
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("[소켓-{}] FastAPI 연결 수립 완료. 샌드박스 컨테이너를 선제 가동합니다. 이미지: {}", uuid, baseImage);
        this.registeredContainerId = dockerAgentService.startSandbox(userId, uuid, baseImage);

        List<ProjectNodeResponse> fileNodes = storageService.getProjectTree(email, uuid);

        Map<String, Object> initialPrompt = new HashMap<>();
        initialPrompt.put("type", "INIT");
        initialPrompt.put("projectName", requestDto.getProjectName());
        initialPrompt.put("framework", requestDto.getFramework());
        initialPrompt.put("language", requestDto.getLanguage());
        initialPrompt.put("license", requestDto.getLicense());
        initialPrompt.put("prompt", requestDto.getPrompt());
        initialPrompt.put("tree", fileNodes);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(initialPrompt)));
    }

    /**
     * 실시간으로 LLM이 내리는 점진적 명령(Action)들을 라우팅 처리
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 1. 안전 장치 가드 체크 (최대 실행 턴 수 초과 검증)
        executionTurnCount++;
        if (executionTurnCount > MAX_TURN_LIMIT) {
            log.error("[소켓-{}] 최대 실행 턴 수({})를 초과하여 자동화 공정을 강제 종료합니다.", uuid, MAX_TURN_LIMIT);

            // 공정 실패 알림 전송 후 소켓 종료
            AiModelMessage.Observation errorObs = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "MAX_TURN_LIMIT_EXCEEDED: 에이전트 루프가 너무 오래 지속되어 안전을 위해 강제 종료되었습니다.");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorObs)));

            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        String payload = message.getPayload();
        log.info("[소켓-{}] LLM 명령 수신 [Turn: {}/{}]", uuid, executionTurnCount, MAX_TURN_LIMIT);

        AiModelMessage.Action action = objectMapper.readValue(payload, AiModelMessage.Action.class);
        AiModelMessage.Observation observation;
        
        
        
        // 2. 도구(Tool) 매핑 분기문
        if ("WRITE_FILE".equals(action.getTool())) {
            try {
                dockerAgentService.writeFile(this.userId, this.uuid, action.getPath(), action.getContent());
                observation = new AiModelMessage.Observation("OBSERVATION", "SUCCESS", 0, "파일 작성 성공", "");
            } catch (Exception e) {
                log.error("[소켓-{}] 파일 작성 중 오류 발생", uuid, e);
                observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", e.getMessage());
            }
        }
        else if ("DELETE_FILE".equals(action.getTool())) {
            try {
                boolean isDeleted = dockerAgentService.deleteFile(this.userId, this.uuid, action.getPath());
                if (isDeleted) {
                    observation = new AiModelMessage.Observation("OBSERVATION", "SUCCESS", 0, "파일 삭제 성공", "");
                } else {
                    observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "파일 삭제 실패 (파일이 존재하지 않거나 잠겨있음)");
                }
            } catch (Exception e) {
                log.error("[소켓-{}] 파일 삭제 중 오류 발생", uuid, e);
                observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", e.getMessage());
            }
        }
        else if ("EXECUTE_CMD".equals(action.getTool())) {
            if (this.registeredContainerId == null) {
                observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "샌드박스 컨테이너가 가동 중이 아닙니다.");
            } else {
                observation = dockerAgentService.executeCommand(this.registeredContainerId, action.getCmd());
            }
        }
        else if ("FINISH".equals(action.getTool())) {
            log.info("[소켓-{}] FastAPI로부터 최종 공정 완료 신호(FINISH)를 수신했습니다.", uuid);

            // TODO: 프로젝트 테이블 상태 관리 객체를 호출해 프로젝트 상태를 'COMPLETED'로 변경하는 비즈니스 로직을 여기에 연동

            // 소켓을 정상 종료 상태로 완전히 닫아버립니다. (자동으로 afterConnectionClosed가 실행됨)
            session.close(CloseStatus.NORMAL);
            return;
        }
        else {
            observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "지원하지 않는 도구입니다.");
        }

        // 파일 관련 수정 후 색인 갱신
        storageService.indexProjectFiles(this.uuid);

        // 결과를 FastAPI 측으로 다시 전송하여 다음 행동을 결정하게 함
        String jsonResponse = objectMapper.writeValueAsString(observation);
        session.sendMessage(new TextMessage(jsonResponse));
    }

    /**
     * 모든 공정이 완수되어 세션이 정상 종료되거나, 통신 장애로 끊겼을 때 실행 (사후 정리)
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("[소켓-{}] 파이프라인 세션 닫힘 (Status: {}). 사용 중이던 샌드박스를 파괴합니다.", uuid, status);

        if (this.registeredContainerId != null) {
            // 자원 누수가 나지 않도록 켜두었던 컨테이너를 확실하게 중지 및 소멸
            dockerAgentService.stopSandbox(this.registeredContainerId);
            this.registeredContainerId = null;
        }
    }
}
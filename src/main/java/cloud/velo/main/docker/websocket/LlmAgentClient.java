package cloud.velo.main.docker.websocket;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.controller.dto.ProjectLogSaveDto;
import cloud.velo.main.controller.dto.ProjectNodeResponse;
import cloud.velo.main.docker.dto.AiModelMessage;
import cloud.velo.main.docker.service.DockerAgentService;
import cloud.velo.main.service.ProjectLogService;
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

    private final ProjectLogService projectLogService;

    private String finalProjectStatus = "FAILED";

    public LlmAgentClient(DockerAgentService dockerAgentService, ObjectMapper objectMapper, StorageService storageService,
                          String userId, String uuid, String email, String baseImage, ProjectCreateRequestDto requestDto, ProjectLogService projectLogService) {
        this.dockerAgentService = dockerAgentService;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.userId = userId;
        this.uuid = uuid;
        this.email = email;
        this.baseImage = baseImage;
        this.requestDto = requestDto;
        this.projectLogService = projectLogService;
    }

    //
    private void sendSystemLog(String logLevel, String message, String status) {
        ProjectLogSaveDto logDto = new ProjectLogSaveDto();
        logDto.setUuid(this.uuid);
        logDto.setLogLevel(logLevel);
        logDto.setMessage(message);
        logDto.setStatus(status);
        projectLogService.saveWorkerLog(logDto); // 즉시 Redis 적재 및 SSE 브로드캐스팅
    }

    /**
     * FastAPI(LLM) 서버와 웹소켓 파이프라인이 최초로 연결되었을 때 실행
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        log.info("[소켓-{}] FastAPI 연결 수립 완료. 샌드박스 컨테이너를 선제 가동합니다. 이미지: {}", uuid, baseImage);

        // 샌드 박스 가동 알림 로그 추가
        sendSystemLog("INFO", "AI 에이전트 연결 수립 완료. 격리 구역(Sandbox) 가동을 개시합니다.", "ANALYZING");

        this.registeredContainerId = dockerAgentService.startSandbox(userId, uuid, baseImage);

        // 로그 추가 샌드 박스 배정 완료 로그
        sendSystemLog("INFO", "[System] 격리 구역 배정 완료. 자율 코딩 루프를 개시합니다.", "GENERATING");

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

        // 로그 추가 루프 돌입 준비 완료 로그
        sendSystemLog("INFO", "요구사항 분석 가동 완료. AI 자율 소스 코드 빌드 루프를 시작합니다.", "GENERATING");
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

            // 상태변수 업데이트
            this.finalProjectStatus = "FAILED";

            // 로그 추가 공정 제한 횟수 위험 알림
            sendSystemLog("ERROR", "자율 공정 제한 횟수(" + MAX_TURN_LIMIT + "턴)를 초과하여 안전을 위해 시스템을 강제 종료합니다.", "GENERATING");

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
            // [메인 로그추가] 파싱 완료된 정당한 시점 로그 생성 시작
            sendSystemLog("INFO", "[Action] 소스 코드 파일 작성 중: " + action.getPath(), "GENERATING");

            try {
                dockerAgentService.writeFile(this.userId, this.uuid, action.getPath(), action.getContent());
                observation = new AiModelMessage.Observation("OBSERVATION", "SUCCESS", 0, "파일 작성 성공", "");

                // [세부 로그] 파일 작성 과정에서 도커 시스템이나 스크립트가 뱉은 상세 출력(stdout/stderr)이 있다면 연달아 준다
                if (observation.getStdout() != null && !observation.getStdout().isEmpty()) {
                    for (String line : observation.getStdout().split("\n")) {
                        sendSystemLog("DEBUG", line, "GENERATING");
                    }
                }

                // [마무리 알림] 파일이 정상적으로 다 써졌음을 알림
                sendSystemLog("INFO", "[Success] 파일 작성이 완료되었습니다: " + action.getPath(), "GENERATING");
            } catch (Exception e) {
                log.error("[소켓-{}] 파일 작성 중 오류 발생", uuid, e);
                observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", e.getMessage());

                // [에러 로그]
                sendSystemLog("ERROR", "[Fail] 파일 작성 실패: " + action.getPath() + " | Reason: " + e.getMessage(), "GENERATING");
            }
        }
        else if ("DELETE_FILE".equals(action.getTool())) {

            // [ 메인 로그 추ㅏㄱ] 파일 리팩토링 및 삭제
            sendSystemLog("WARN", "[Action] 에이전트 리팩토링 - 파일 제거 요청 수신: " + action.getPath(), "GENERATING");

            try {
                boolean isDeleted = dockerAgentService.deleteFile(this.userId, this.uuid, action.getPath());
                if (isDeleted) {
                    observation = new AiModelMessage.Observation("OBSERVATION", "SUCCESS", 0, "파일 삭제 성공", "");
                    // [마무리 알림 - 파일 삭제]
                    sendSystemLog("INFO", "[Success] 파일 삭제가 완료되었습니다: " + action.getPath(), "GENERATING");
                } else {
                    observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "파일 삭제 실패 (파일이 존재하지 않거나 잠겨있음)");
                   // [마무리 알림 - 삭제 실패]
                    sendSystemLog("ERROR", "[Fail] 파일 삭제 실패 (파일 없음/잠김): " + action.getPath(), "GENERATING");
                }
            } catch (Exception e) {
                log.error("[소켓-{}] 파일 삭제 중 오류 발생", uuid, e);
                observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", e.getMessage());
                // [에러 로그]
                sendSystemLog("ERROR", "[Fail] 파일 삭제 중 오류: " + action.getPath() + " | Reason: " + e.getMessage(), "GENERATING");
            }
        }
        else if ("EXECUTE_CMD".equals(action.getTool())) {

            // 로그 추가 어떤 쉘 명령어가 가동 되는지
            sendSystemLog("INFO", "[Container] 샌드박스 내부 명령어 실행: " + action.getCmd(), "GENERATING");
            if (this.registeredContainerId == null) {
                observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "샌드박스 컨테이너가 가동 중이 아닙니다.");
                // [에러 로그 - 실행 불가]
                sendSystemLog("ERROR", "[Fail] 샌드박스 미가동 상태로 명령어 실행 불가", "GENERATING");
            } else {
                observation = dockerAgentService.executeCommand(this.registeredContainerId, action.getCmd());
            }

            // [새부 로그] stdout - 도커의 표준 메시지 출력
            if (observation.getStdout() != null && !observation.getStdout().isEmpty()) {
                for (String line : observation.getStdout().split("\n")) {
                    sendSystemLog("DEBUG", line, "GENERATING");
                }
            } // [새부 로그]  stderr 도커에서의 에러, 경고 메시지 출력
            if (observation.getStderr() != null && !observation.getStderr().isEmpty()) {
                for (String line : observation.getStderr().split("\n")) {
                    sendSystemLog("ERROR", line, "GENERATING");
                }
            }

            // [마무리 알림]
            sendSystemLog("INFO", "[Success] 명령어 실행 완료: " + action.getCmd(), "GENERATING");
        }
        else if ("FINISH".equals(action.getTool())) {
            log.info("[소켓-{}] FastAPI로부터 최종 공정 완료 신호(FINISH)를 수신했습니다.", uuid);

            // 완료했다면 COMPLETED
            this.finalProjectStatus = "COMPLETED";

            // TODO: 프로젝트 테이블 상태 관리 객체를 호출해 프로젝트 상태를 'COMPLETED'로 변경하는 비즈니스 로직을 여기에 연동
            // 로그 추가(ProjectService가 DB Bulk insert 진행)
            sendSystemLog("INFO", "AI 자율 공정이 최종 완수되었습니다.", "COMPLETED");

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
        // 컨테이너 파괴 로그와 동시에 최종 상태를 얹어 일괄 덤프
        sendSystemLog("INFO", "안전 무결 격리 공간 반환 완료. 세션 마감.", this.finalProjectStatus);
    }
}
package cloud.velo.main.client;

import cloud.velo.main.dto.request.LlmAutomationInitRequest;
import cloud.velo.main.dto.request.ProjectCreateRequest;
import cloud.velo.main.dto.response.ProjectNodeResponse;
import cloud.velo.main.dto.common.AiModelMessage;
import cloud.velo.main.event.ProjectLogEvent;
import cloud.velo.main.service.DockerAgentService;
import cloud.velo.main.service.StorageService;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import cloud.velo.main.domain.ProjectStatus;
import java.io.IOException;
import java.util.List;

@Slf4j
public class LlmAgentClient extends TextWebSocketHandler {

    private final DockerAgentService dockerAgentService;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    private final String userId;
    private final String uuid;
    private final String email;
    private final String baseImage;
    private final ProjectCreateRequest requestDto;


    @Value("${llm.server.max-count:300}")
    private int maxTurnLimit;
    private int executionTurnCount = 0;

    private String registeredContainerId;
    private ProjectStatus finalProjectStatus = ProjectStatus.FAILED;
    private final ApplicationEventPublisher eventPublisher;

    public LlmAgentClient(DockerAgentService dockerAgentService, ObjectMapper objectMapper, StorageService storageService,
                          String userId, String uuid, String email, String baseImage, ProjectCreateRequest requestDto,
                          ApplicationEventPublisher eventPublisher) {
        this.dockerAgentService = dockerAgentService;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.userId = userId;
        this.uuid = uuid;
        this.email = email;
        this.baseImage = baseImage;
        this.requestDto = requestDto;
        this.eventPublisher = eventPublisher;
    }

    private void sendSystemLog(String logLevel, String message, ProjectStatus status, boolean isActivityFeed) {
        eventPublisher.publishEvent(new ProjectLogEvent(this.uuid, logLevel, message, status, isActivityFeed));
    }

    /**
     * FastAPI(LLM) 서버와 웹소켓 파이프라인이 최초로 연결되었을 때 실행
     */
    @Override
    public void afterConnectionEstablished(@Nonnull WebSocketSession session) throws IOException { // @Nonnull 주입 및 throws 수정
        session.setTextMessageSizeLimit(50 * 1024 * 1024);
        session.setBinaryMessageSizeLimit(50 * 1024 * 1024);

        log.info("[소켓-{}] FastAPI 연결 수립 완료. 샌드박스 컨테이너를 선제 가동합니다. 이미지: {}", uuid, baseImage);

        sendSystemLog("INFO", "AI 에이전트 연결 수립 완료. 격리 구역(Sandbox) 가동을 개시합니다.", ProjectStatus.INIT, true);
        this.registeredContainerId = dockerAgentService.startSandbox(userId, uuid, baseImage);

        sendSystemLog("INFO", "[System] 격리 구역 배정 완료. 자율 코딩 루프를 개시합니다.", ProjectStatus.INIT, true);
        sendSystemLog("INFO", "[System] 자율 공정 작업 공간 할당: 프로젝트 작업 디렉토리(/workspace) 생성을 완료했습니다.", ProjectStatus.PROVISIONING, true);
        sendSystemLog("INFO", "[System] 초기 기본 아키텍처 스켈레톤 및 프로토타입 컨텍스트 빌드를 준비합니다.", ProjectStatus.PROVISIONING, false);

        if (hasValue(requestDto.getLicense())) {
            sendSystemLog("INFO", String.format("[25%%] 오픈소스 정책 반영: 선택된 라이선스([%s]) 명세서 파일 생성을 자동 스케줄링합니다.", requestDto.getLicense()), ProjectStatus.CONFIGURING, false);
        } else {
            sendSystemLog("WARN", "[25%] 경고: 선택된 오픈소스 라이선스가 없습니다. 기본 보안 환경으로 설정을 지속합니다.", ProjectStatus.CONFIGURING, true);
        }

        try {
            LlmAutomationInitRequest initialPrompt = initialPromptBuilder();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(initialPrompt)));
            sendSystemLog("INFO", "요구사항 분석 가동 완료. AI 자율 소스 코드 빌드 루프를 시작합니다.", ProjectStatus.ANALYZING, true);

        } catch (IllegalArgumentException e) {
            log.error("[소켓-{}] 지원하지 않는 풀스택 구조 진입으로 파이프라인 정지", uuid);
            this.finalProjectStatus = ProjectStatus.FAILED;
            sendSystemLog("ERROR", "[Fail] 초기 빌드 실패: " + e.getMessage(), ProjectStatus.FAILED, true);
            session.close(CloseStatus.NORMAL);
        }
    }

    /**
     * 실시간으로 LLM이 내리는 점진적 명령(Action)들을 라우팅 처리
     */
    @Override
    public void handleTextMessage(@Nonnull WebSocketSession session, @Nonnull TextMessage message) throws IOException { // @Nonnull 주입 및 throws 수정
        executionTurnCount++;
        if (executionTurnCount > maxTurnLimit) {
            log.error("[소켓-{}] 최대 실행 턴 수({})를 초과하여 자동화 공정을 강제 종료합니다.", uuid, maxTurnLimit);
            this.finalProjectStatus = ProjectStatus.FAILED;

            sendSystemLog("ERROR", "자율 공정 제한 횟수(" + maxTurnLimit + "턴)를 초과하여 안전을 위해 시스템을 강제 종료합니다.", ProjectStatus.FAILED, true);

            AiModelMessage.Observation errorObs = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "MAX_TURN_LIMIT_EXCEEDED: 에이전트 루프가 너무 오래 지속되어 강제 종료되었습니다.");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorObs)));
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        String payload = message.getPayload();
        log.info("[소켓-{}] LLM 명령 수신 [Turn: {}/{}]", uuid, executionTurnCount, maxTurnLimit);

        AiModelMessage.Action action = objectMapper.readValue(payload, AiModelMessage.Action.class);
        AiModelMessage.Observation observation;

        // if-else if 연쇄를 모던한 자바 Switch 문으로 완벽 전향 청소!
        switch (action.getTool()) {
            case "WRITE_FILE" -> {
                sendSystemLog("INFO", "[Action] 소스 코드 파일 작성 중: " + action.getPath(), ProjectStatus.CODING, true);
                try {
                    dockerAgentService.writeFile(this.userId, this.uuid, action.getPath(), action.getContent());
                    observation = new AiModelMessage.Observation("OBSERVATION", "SUCCESS", 0, "파일 작성 성공", "");

                    if (observation.getStdout() != null && !observation.getStdout().isEmpty()) {
                        for (String line : observation.getStdout().split("\n")) {
                            sendSystemLog("DEBUG", line, ProjectStatus.CODING, false);
                        }
                    }
                    sendSystemLog("INFO", "[Success] 파일 작성이 완료되었습니다: " + action.getPath(), ProjectStatus.CODING, true);
                } catch (Exception e) {
                    log.error("[소켓-{}] 파일 작성 중 오류 발생", uuid, e);
                    observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "Sandbox 파일 시스템 쓰기 거부 에러");
                    sendSystemLog("ERROR", "[Fail] 파일 작성 실패: " + action.getPath() + " | Reason: " + e.getMessage(), ProjectStatus.CODING, true);
                }
            }
            case "DELETE_FILE" -> {
                sendSystemLog("WARN", "[Action] 에이전트 리팩토링 - 파일 제거 요청 수신: " + action.getPath(), ProjectStatus.CODING, true);
                try {
                    boolean isDeleted = dockerAgentService.deleteFile(this.userId, this.uuid, action.getPath());
                    if (isDeleted) {
                        observation = new AiModelMessage.Observation("OBSERVATION", "SUCCESS", 0, "파일 삭제 성공", "");
                        sendSystemLog("INFO", "[Success] 파일 삭제가 완료되었습니다: " + action.getPath(), ProjectStatus.CODING, true);
                    } else {
                        observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "파일 삭제 실패 (파일이 존재하지 않거나 잠겨있음)");
                        sendSystemLog("ERROR", "[Fail] 파일 삭제 실패 (파일 없음/잠김): " + action.getPath(), ProjectStatus.CODING, true);
                    }
                } catch (Exception e) {
                    log.error("[소켓-{}] 파일 삭제 중 오류 발생", uuid, e);
                    observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "Sandbox 파일 시스템 삭제 수행 실패");
                    sendSystemLog("ERROR", "[Fail] 파일 삭제 중 오류: " + action.getPath() + " | Reason: " + e.getMessage(), ProjectStatus.CODING, true);
                }
            }
            case "READ_FILE" -> {
                sendSystemLog("INFO", "[Action] 소스 코드 파일 조회 중: " + action.getPath(), ProjectStatus.CODING, false);
                try {
                    String fileContent = dockerAgentService.readFile(this.userId, this.uuid, action.getPath());
                    observation = new AiModelMessage.Observation("OBSERVATION", "SUCCESS", 0, fileContent, "");
                    sendSystemLog("INFO", "[Success] 파일 조회가 완료되었습니다: " + action.getPath(), ProjectStatus.CODING, true);
                } catch (Exception e) {
                    log.error("[소켓-{}] 파일 조회 중 예외 발생: {}", uuid, action.getPath(), e);
                    observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "요청한 파일 자원을 읽을 수 없습니다.");
                    sendSystemLog("ERROR", "[Fail] 파일 조회 실패: " + action.getPath() + " | Reason: " + e.getMessage(), ProjectStatus.CODING, true);
                }
            }
            case "EXECUTE_CMD" -> {
                sendSystemLog("INFO", "[Container] 샌드박스 내부 명령어 실행: " + action.getCmd(), ProjectStatus.EXECUTING, false);
                if (this.registeredContainerId == null) {
                    observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "샌드박스 컨테이너가 가동 중이 아닙니다.");
                    sendSystemLog("ERROR", "[Fail] 샌드박스 미가동 상태로 명령어 실행 불가", ProjectStatus.EXECUTING, false);
                } else {
                    observation = dockerAgentService.executeCommand(this.registeredContainerId, action.getCmd());
                }

                if (observation.getStdout() != null && !observation.getStdout().isEmpty()) {
                    for (String line : observation.getStdout().split("\n")) {
                        sendSystemLog("DEBUG", line, ProjectStatus.EXECUTING, false);
                    }
                }
                if (observation.getStderr() != null && !observation.getStderr().isEmpty()) {
                    for (String line : observation.getStderr().split("\n")) {
                        sendSystemLog("ERROR", line, ProjectStatus.EXECUTING, false);
                    }
                }
                sendSystemLog("INFO", "[Success] 명령어 실행 완료: " + action.getCmd(), ProjectStatus.EXECUTING, true);
            }
            case "FINISH" -> {
                log.info("[소켓-{}] FastAPI로부터 최종 공정 완료 신호(FINISH)를 수신했습니다.", uuid);
                this.finalProjectStatus = ProjectStatus.COMPLETED;
                sendSystemLog("INFO", "AI 자율 공정이 최종 완수되었습니다.", ProjectStatus.COMPLETED, false);
                session.close(CloseStatus.NORMAL);
                return;
            }
            default -> observation = new AiModelMessage.Observation("OBSERVATION", "ERROR", 1, "", "지원하지 않는 도구입니다.");
        }

        // (수정) 파일 시스템의 구조적 변경이 있을때만 인덱싱 수행
        if ("WRITE_FILE".equals(action.getTool()) || "DELETE_FILE".equals(action.getTool())) {
            storageService.indexProjectFiles(email, this.uuid);
        }
        String jsonResponse = objectMapper.writeValueAsString(observation);
        session.sendMessage(new TextMessage(jsonResponse));
    }

    /**
     * 모든 공정이 완수되어 세션이 정상 종료되거나, 통신 장애로 끊겼을 때 실행 (사후 정리)
     */
    @Override
    public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus status) { // @Nonnull 주입 및 throws 수정
        log.info("[소켓-{}] 파이프라인 세션 닫힘 (Status: {}). 사용 중이던 샌드박스를 파괴합니다.", uuid, status);

        if (this.registeredContainerId != null) {
            dockerAgentService.stopSandbox(this.registeredContainerId);
            this.registeredContainerId = null;
        }
        sendSystemLog("INFO", "안전 무결 격리 공간 반환 완료. 세션 마감.", this.finalProjectStatus, true);
    }

    private LlmAutomationInitRequest initialPromptBuilder() {
        String framework = "";
        String language = "";

        if ("FULL_STACK".equals(requestDto.getArchitecture_type())) {
            if (hasValue(requestDto.getFullstack_framework()) && !hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                framework =  requestDto.getFullstack_framework();
                language =  requestDto.getFullstack_language();
            }
            else if (!hasValue(requestDto.getFullstack_framework()) && hasValue(requestDto.getBackend_framework()) && hasValue(requestDto.getFrontend_framework())) {
                throw new IllegalArgumentException("복합 멀티-티어 풀스택 스택 빌드는 현재 준비 중인 기능입니다.");
            }
        }
        else if ("CLIENT_SERVER".equals(requestDto.getArchitecture_type())) {
            if (hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                framework =  requestDto.getBackend_framework();
                language =  requestDto.getBackend_language();
            }
            else if (!hasValue(requestDto.getBackend_framework()) && hasValue(requestDto.getFrontend_framework())) {
                framework =  requestDto.getFrontend_framework();
                language =  requestDto.getFrontend_language();
            }
        }

        List<ProjectNodeResponse> fileNodes = storageService.getProjectTree(email, uuid);

        // DTO로 구조 수정
        return LlmAutomationInitRequest.builder()
                .projectName(requestDto.getProjectName())
                .architectureType(requestDto.getArchitecture_type())
                .framework(framework)
                .language(language)
                .database(requestDto.getDatabase())
                .license(requestDto.getLicense())
                .prompt(requestDto.getPrompt())
                .tree(fileNodes)
                .build();
    }

    private boolean hasValue(String str) {
        return str != null && !str.trim().isEmpty();
    }
}
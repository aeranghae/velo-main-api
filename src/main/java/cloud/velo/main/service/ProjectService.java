package cloud.velo.main.service;

import cloud.velo.main.config.docker.DockerImageProperties;
import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.docker.websocket.AgentConnectionManager;
import cloud.velo.main.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final AgentConnectionManager agentConnectionManager; // 동적 웹소켓 매니저 주입
    private final DockerImageProperties dockerImageProperties;

    @Value("${llm.server.url:http://localhost:8000}")
    private String llmServerUrl;

    @Value("${velo.storage.path}")
    private String baseStoragePath;

    /**
     * 프로젝트 생성 완료 후 LLM 서버와 독립 세션을 맺고 도커 제어를 시작하는 비동기 트리거
     */
    @Async
    public void startAutomationProcess(User user, String uuid, ProjectCreateRequestDto requestDto) {
        log.info("[Automation] 프로젝트 자동화 공정 트리거 가동 시작. UUID: {}", uuid);

        // 1. 사용자가 선택한 프레임워크/언어 스택에 따라 가동할 도커 베이스 이미지 결정
        String baseImage = setDockerImage(requestDto);
        String email = user.getEmail();

        try {
            // 2. DB 기본키(PK) 값을 파일 경로 식별용 물리 명칭(userid)으로 매핑
            String userId = String.valueOf(user.getId());

            // 3. 동적 웹소켓 매니저를 통해 이 프로젝트 전용 파이프라인 개설 (projectId 인자 제거, uuid 중심 구조)
            agentConnectionManager.startProjectGeneration(userId, uuid, email, baseImage, requestDto);

            log.info("[Automation] 프로젝트 전용 LLM 웹소켓 파이프라인 개설 완료. 부모폴더(userid): {}, 세션키(uuid): {}", userId, uuid);
        } catch (Exception e) {
            log.error("[Automation] 자동화 파이프라인 웹소켓 연결 중 치명적 예외 발생. UUID: {}", uuid, e);
        }
    }

    /**
     * YML 설정 대장을 기반으로 런타임 도커 가드 이미지 동적 선택
     */
    private String determineBaseImage(String framework, String language) {
        // 1. 프레임워크 매핑 매치 시도
        String image = dockerImageProperties.findFrameworkImage(framework);
        if (image != null) {
            return image;
        }

        // 2. 프레임워크 매치 실패 시 언어 매핑 매치 시도
        image = dockerImageProperties.findLanguageImage(language);
        if (image != null) {
            return image;
        }

        // 3. 둘 다 매핑 대장에 없는 미지의 스택일 경우 백가드 기본 이미지 출격
        log.warn("[Automation] 매핑되는 도커 이미지를 찾지 못해 기본 리눅스 가드로 대체합니다. Framework: {}, Language: {}", framework, language);
        return dockerImageProperties.getDefaultImage();
    }

    /**
     * 분기별로 나누어서 도커 이미지 결정
     */
    private String setDockerImage(ProjectCreateRequestDto requestDto){
        //  분기 검증
        if ("FULL_STACK".equals(requestDto.getArchitecture_type())) {

            // 하나의 프레임워크에서 풀스택으로 구현하는 경우
            if (hasValue(requestDto.getFullstack_framework()) && !hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                log.info("[Automation] 선정된 프레임워크 출력: {}", requestDto.getFullstack_framework());
                return determineBaseImage(requestDto.getFullstack_framework(), requestDto.getFullstack_language());
            }
            // 백엔드와 프론트엔드의 프레임워크 가 분리되어 풀스택으로 구현하는 경우
            else if (!hasValue(requestDto.getFullstack_framework()) && hasValue(requestDto.getBackend_framework()) && hasValue(requestDto.getFrontend_framework())){
                // TODO: 풀스택 중 백엔드+프론트 복합인 경우는 아직 사용 불가
                return determineBaseImage("", ""); // 오류 방지를 위한 기본이미지 지정
            }
        }
        else if ("CLIENT_SERVER".equals(requestDto.getArchitecture_type())) {

            // 백엔드 프론트엔드 중 백엔드만 구현하는 경우
            if (hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                log.info("[Automation] 선정된 프레임워크 출력: {}", requestDto.getBackend_framework());
                return determineBaseImage(requestDto.getBackend_framework(), requestDto.getBackend_language());
            }
            // 백엔드 프론트 엔드 중 프론트엔드만 구현하는 경우
            else if (!hasValue(requestDto.getBackend_framework()) && hasValue(requestDto.getFrontend_framework())) {
                log.info("[Automation] 선정된 프레임워크 출력: {}", requestDto.getFrontend_framework());
                return determineBaseImage(requestDto.getFrontend_framework(), requestDto.getFrontend_language());
            }

        }
        return determineBaseImage("", ""); // 오류 방지를 위한 기본이미지 지정
    }

    /**
     * 문자열이 null이 아니고 비어있지 않은지(공백 제외) 체크하는 가벼운 유틸 메서드
     */
    private boolean hasValue(String str) {
        return str != null && !str.trim().isEmpty();
    }
}
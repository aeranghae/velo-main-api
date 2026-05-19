package cloud.velo.main.service;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.docker.websocket.AgentConnectionManager;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.ProjectRepository;
import cloud.velo.main.util.storage.DirectoryTreeBuilder;
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
        String baseImage = determineBaseImage(requestDto.getFramework(), requestDto.getLanguage());
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
     * LLM 에이전트가 실행 파일 검사/테스트 시 가동할 런타임 도커 가드 이미지 선택
     */
    private String determineBaseImage(String framework, String language) {
        // 1. 파이썬 계열 스택
        if ("fastapi".equalsIgnoreCase(framework) || "Python".equalsIgnoreCase(language)) {
            return "python:3.11-slim";
        }
        // 2. 자바스크립트 / 타입스크립트 생태계 전체 (React, Next.js, NestJS, Vue, Node.js)
        else if ("react".equalsIgnoreCase(framework) ||
                "Next.js".equalsIgnoreCase(framework) || "nextjs".equalsIgnoreCase(framework) ||
                "NestJS".equalsIgnoreCase(framework) || "nestjs".equalsIgnoreCase(framework) ||
                "vue".equalsIgnoreCase(framework) || "Vue.js".equalsIgnoreCase(framework) ||
                "Node".equalsIgnoreCase(language) || "Node.js".equalsIgnoreCase(language) ||
                "TypeScript".equalsIgnoreCase(language) || "JavaScript".equalsIgnoreCase(language)) {
            return "node:20-alpine";
        }
        // 3. 자바 계열 엔터프라이즈 스택
        else if ("spring-boot".equalsIgnoreCase(framework) || "spring".equalsIgnoreCase(framework) ||
                "Java".equalsIgnoreCase(language)) {
            return "openjdk:21-slim";
        }

        // 기본 범용 리눅스 환경 이미지 (매핑되지 않는 미지의 프레임워크 대비용 가드)
        return "ubuntu:22.04";
    }
}
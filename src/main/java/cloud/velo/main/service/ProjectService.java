package cloud.velo.main.service;

import cloud.velo.main.config.DockerImageProperties;
import cloud.velo.main.dto.request.ProjectCreateRequest;
import cloud.velo.main.dto.response.ProjectResponse;
import cloud.velo.main.domain.User;
import cloud.velo.main.client.AgentConnectionManager;
import cloud.velo.main.exception.UserNotFoundException;
import cloud.velo.main.exception.MaxProjectLimitException;
import cloud.velo.main.repository.ProjectRepository;
import cloud.velo.main.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final AgentConnectionManager agentConnectionManager;
    private final DockerImageProperties dockerImageProperties;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final StorageService storageService;

    @Value("${velo.project.maxcount}")
    private int maxProjectGenerateCount;

    /**
     * [컨트롤러 위임 통합 관문] 프로젝트 생성 검증부터 비동기 파이프라인 트리거까지
     */
    @Transactional
    public ProjectResponse generateAndAutomationPipeline(String email, ProjectCreateRequest requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("유저 정보를 찾을 수 없습니다. email: " + email));

        int projectCount = projectRepository.countByUser(user);
        if (projectCount >= maxProjectGenerateCount) {
            throw new MaxProjectLimitException("생성 가능한 최대 프로젝트 개수(" + maxProjectGenerateCount + "개)를 초과했습니다.");
        }

        ProjectResponse newProject = storageService.createAndIndexProject(email, requestDto);

        if (newProject == null) {
            throw new IllegalArgumentException("지원하지 않는 아키텍처 설정 혹은 프레임워크 조합입니다.");
        }

        if (user.getId() == 1) {
            this.startAutomationProcess(user, newProject.getUuid(), requestDto);
        }

        return newProject;
    }

    /**
     * 프로젝트 생성 완료 후 LLM 서버와 독립 세션을 맺고 도커 제어를 시작하는 비동기 트리거
     */
    @Async
    public void startAutomationProcess(User user, String uuid, ProjectCreateRequest requestDto) {
        log.info("[Automation] 프로젝트 자동화 공정 트리거 가동 시작. UUID: {}", uuid);

        String baseImage = setDockerImage(requestDto);
        String email = user.getEmail();

        try {
            String userId = String.valueOf(user.getId());
            agentConnectionManager.startProjectGeneration(userId, uuid, email, baseImage, requestDto);

            log.info("[Automation] 프로젝트 전용 LLM 웹소켓 파이프라인 개설 완료. 부모폴더(userid): {}, 세션키(uuid): {}", userId, uuid);
        } catch (Exception e) {
            log.error("[Automation] 자동화 파이프라인 웹소켓 연결 중 치명적 예외 발생. UUID: {}", uuid, e);
        }
    }

    private String determineBaseImage(String framework, String language) {
        // 유사 로그 중복 제거: 분기마다 찍던 로그를 이미지 결정 관문 상단에서 한 번만 찍도록 단일화
        log.info("[Automation] 도커 가드 이미지 선정 검크 가동 - Framework: {}, Language: {}", framework, language);

        String image = dockerImageProperties.findFrameworkImage(framework);
        if (image != null) {
            return image;
        }

        image = dockerImageProperties.findLanguageImage(language);
        if (image != null) {
            return image;
        }

        log.warn("[Automation] 매핑되는 도커 이미지를 찾지 못해 기본 리눅스 가드로 대체합니다. Framework: {}, Language: {}", framework, language);
        return dockerImageProperties.getDefaultImage();
    }

    private String setDockerImage(ProjectCreateRequest requestDto){
        if ("FULL_STACK".equals(requestDto.getArchitecture_type())) {
            if (hasValue(requestDto.getFullstack_framework()) && !hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                return determineBaseImage(requestDto.getFullstack_framework(), requestDto.getFullstack_language());
            }
            else if (!hasValue(requestDto.getFullstack_framework()) && hasValue(requestDto.getBackend_framework()) && hasValue(requestDto.getFrontend_framework())){
                return determineBaseImage("", "");
            }
        }
        else if ("CLIENT_SERVER".equals(requestDto.getArchitecture_type())) {
            if (hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                return determineBaseImage(requestDto.getBackend_framework(), requestDto.getBackend_language());
            }
            else if (!hasValue(requestDto.getBackend_framework()) && hasValue(requestDto.getFrontend_framework())) {
                return determineBaseImage(requestDto.getFrontend_framework(), requestDto.getFrontend_language());
            }
        }
        return determineBaseImage("", "");
    }

    private boolean hasValue(String str) {
        return str != null && !str.trim().isEmpty();
    }
}
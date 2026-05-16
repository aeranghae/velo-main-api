package cloud.aeranghae.main.service;

import cloud.aeranghae.main.controller.dto.ProjectCreateRequestDto;
import cloud.aeranghae.main.controller.dto.ProjectResponseDto;
import cloud.aeranghae.main.domain.AiModel;
import cloud.aeranghae.main.domain.Project;
import cloud.aeranghae.main.domain.User;
import cloud.aeranghae.main.repository.AiModelRepository;
import cloud.aeranghae.main.repository.ProjectRepository;
import cloud.aeranghae.main.util.template.TemplateInitializer;
import cloud.aeranghae.main.util.template.TemplateInitializerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final ProjectRepository projectRepository;
    private final AiModelRepository aiModelRepository;
    private final TemplateInitializerFactory factory;

    @Value("${aeranghae.storage.path}")
    private String baseStoragePath;

    /**
     * 사용자 루트 디렉토리 생성 (로그인 시 호출)
     * 경로: {baseStoragePath}/{userId}
     */
    public void createUserDirectory(String userIdentifier) {
        Path path = Paths.get(baseStoragePath, userIdentifier);
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path);
                log.info("사용자 루트 디렉토리 생성 완료: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("사용자 디렉토리 생성 실패: " + userIdentifier, e);
        }
    }

    /**
     * 새 프로젝트 생성 (DB 등록 + UUID 폴더 생성)
     */
    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id")
    public ProjectResponseDto createProject(User user, ProjectCreateRequestDto requestDto) {
        String uuid = UUID.randomUUID().toString();

        // 1. 사용할 모델 결정 로직
        AiModel targetModel = null;

        // 전달받은 projectModel 이름이 있는 경우 조회 시도
        if (requestDto.getModel() != null && !requestDto.getModel().trim().isEmpty()) {
            targetModel = aiModelRepository.findByModelNameAndIsActiveTrue(requestDto.getModel())
                    .orElse(null); // 이름으로 못 찾으면 일단 null
        }

        // projectModel이 없거나, DB에서 찾지 못한 경우 유저의 기본 모델 사용
        if (targetModel == null) {
            targetModel = user.getModel();
        }

        // [방어 코드] 만약 유저의 기본 모델조차 없다면 시스템 전체 기본 모델 조회
        if (targetModel == null) {
            targetModel = aiModelRepository.findByDefaultActiveTrue()
                    .orElseThrow(() -> new IllegalStateException("사용 가능한 AI 모델이 시스템에 존재하지 않습니다."));
        }

        // 1. DB에 프로젝트 메타데이터 저장
        Project project = projectRepository.save(Project.builder()
                .name(requestDto.getModel())
                .uuid(uuid)
                .user(user)
                .model(targetModel)
                .build());

        // TODO: 만약 프레임워크에맞는 값이 없는 경우 생성 실패 처리 및 생성된 파일 삭제
        // 2. 물리적 폴더 생성: {baseStoragePath}/{userId}/{uuid}
        Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);
        try {
            Files.createDirectories(projectPath);

            // 만약 프레임워크에맞는 값이 없는 경우 생성 실패 처리 및 생성된 파일 삭제
            // 프레임워크에 맞는 기본 디렉토리 구조 생성
            TemplateInitializer initializer = factory.getInitializer(requestDto.getFramework());
            initializer.initialize(projectPath, requestDto);

        } catch (IOException e) {
            throw new RuntimeException("프로젝트 폴더 생성 실패: " + uuid, e);
        }

        return convertToDto(project, projectPath);
    }

    /**
     * 사용자 프로젝트 상세 목록 조회 (DB 정보 + 물리적 통계)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public List<ProjectResponseDto> getUserProjectDetails(User user) {
        List<Project> projects = projectRepository.findByUserOrderByLastModifiedAtDesc(user);

        if (projects.isEmpty()) return Collections.emptyList();

        return projects.stream()
                .map(project -> {
                    Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), project.getUuid());
                    return convertToDto(project, projectPath);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 특정 경로의 전체 사용량 계산 (바이트 단위)
     */
    public long calculateDirectorySize(Path path) {
        if (Files.notExists(path)) return 0L;
        try (var paths = Files.walk(path)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        } catch (IOException e) {
            log.error("용량 계산 중 오류 발생: {}", path, e);
            return 0L;
        }
    }

    /**
     * 폴더 내 일반 파일 개수 카운트
     */
    private int getFileCount(Path path) {
        if (Files.notExists(path)) return 0;
        try (var stream = Files.walk(path)) {
            return (int) stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 엔티티와 물리 정보를 DTO로 변환
     */
    private ProjectResponseDto convertToDto(Project project, Path path) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        try {
            // 물리 폴더가 없을 경우에 대비한 방어 로직
            if (Files.notExists(path)) {
                return ProjectResponseDto.builder()
                        .projectName(project.getName())
                        .uuid(project.getUuid())
                        .model(project.getModel().getModelName())
                        .createdAt(project.getCreatedAt().format(formatter))
                        .lastModified("정보 없음")
                        .size(0L)
                        .fileCount(0)
                        .build();
            }

            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

            return ProjectResponseDto.builder()
                    .projectName(project.getName())
                    .uuid(project.getUuid())
                    .model(project.getModel().getModelName()) // 프로젝트 폴더 조회시 모델명은 객체가 아닌 모델 이름만 전달
                    .createdAt(project.getCreatedAt().format(formatter))
                    .lastModified(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()).format(formatter))
                    .size(calculateDirectorySize(path))
                    .fileCount(getFileCount(path))
                    .build();
        } catch (IOException e) {
            log.error("DTO 변환 중 파일 속성 읽기 실패: {}", project.getUuid());
            return null;
        }
    }


    // 1. 프로젝트 이름 변경 (논리적 변경)
    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id")
    public ProjectResponseDto updateProjectName(User user, String uuid, String newName) {
        Project project = projectRepository.findByUuid(uuid)
                .filter(p -> p.getUser().getId().equals(user.getId())) // 본인 확인
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없거나 권한이 없습니다."));

        project.updateName(newName); // 엔티티 내 비즈니스 로직 호출

        Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);
        return convertToDto(project, projectPath);
    }

    // 2. 프로젝트 삭제 (DB + 물리 폴더)
    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id")
    public void deleteProject(User user, String uuid) {
        Project project = projectRepository.findByUuid(uuid)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없거나 권한이 없습니다."));

        // DB 삭제
        projectRepository.delete(project);

        // 물리 폴더 삭제
        Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);
        deleteDirectoryRecursive(projectPath);
    }

    // 폴더 내부까지 재귀적으로 삭제하는 헬퍼 메서드
    private void deleteDirectoryRecursive(Path path) {
        if (Files.exists(path)) {
            try (var walk = Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder()) // 하위 파일부터 삭제
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            } catch (IOException e) {
                throw new RuntimeException("물리적 폴더 삭제 실패: " + path, e);
            }
        }
    }
}
package cloud.velo.main.service;

import cloud.velo.main.dto.response.*;
import cloud.velo.main.dto.request.ProjectCreateRequest;
import cloud.velo.main.domain.AiModel;
import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.ProjectNode;
import cloud.velo.main.domain.User;
import cloud.velo.main.event.ProjectDeleteVerificationEvent;
import cloud.velo.main.exception.UserNotFoundException;
import cloud.velo.main.exception.ProjectNotFoundException;
import cloud.velo.main.exception.ModelNotFoundException;
import cloud.velo.main.repository.AiModelRepository;
import cloud.velo.main.repository.ProjectRepository;
import cloud.velo.main.repository.UserRepository;
import cloud.velo.main.util.template.TemplateInitializer;
import cloud.velo.main.util.template.TemplateInitializerFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final ProjectRepository projectRepository;
    private final AiModelRepository aiModelRepository;
    private final UserRepository userRepository;
    private final ProjectLogService projectLogService;
    private final TemplateInitializerFactory factory;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${velo.storage.path}")
    private String baseStoragePath;
    private Path nfsRootPath;

    @Value("#{'${velo.ignore.directories:}'.split(',')}")
    private List<String> ignoreDirs;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @PostConstruct
    public void init() {
        this.nfsRootPath = Paths.get(baseStoragePath).normalize();
    }

    // 컨트롤러 위임용 통합 비즈니스 메서드 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Transactional
    public ProjectResponse createAndIndexProject(String email, ProjectCreateRequest requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("프로젝트를 생성할 사용자를 찾을 수 없습니다. email: " + email));

        if (requestDto.getProjectName() == null || requestDto.getProjectName().isBlank()) {
            throw new IllegalArgumentException("프로젝트 이름은 필수 항목입니다.");
        }

        ProjectResponse newProject = createProject(user, requestDto);
        if (newProject == null) {
            throw new IllegalArgumentException("올바르지 않은 아키텍처 설정 구조입니다.");
        }

        indexProjectFiles(newProject.getUuid());
        return newProject;
    }

    @Transactional(readOnly = true)
    public StorageUsageResponse getUserStorageUsage(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

        long usageBytes = getUserTotalStorageUsage(user);
        return new StorageUsageResponse(usageBytes);
    }

    @Transactional(readOnly = true)
    public ProjectDownloadPackResponse prepareProjectDownload(String email, String uuid) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new ProjectNotFoundException("존재하지 않는 프로젝트입니다. UUID: " + uuid));

        byte[] zipData = downloadProject(user, uuid);

        String zipName = project.getName().replaceAll("\\s+", "") + ".zip";
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(zipName, StandardCharsets.UTF_8)
                .build();

        return new ProjectDownloadPackResponse(zipData, contentDisposition);
    }

    // 이메일 래핑 기반 제어 메서드 계열 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    @Cacheable(value = "projectList", key = "#email", cacheManager = "cacheManager")
    @Transactional(readOnly = true)
    public List<ProjectResponse> getUserProjectDetailsByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

        // 내부 호출을 하더라도 상위 관문에서 이미 캐시 장부를 체크했으므로 안전합니다.
        return getUserProjectDetails(user);
    }

    @Transactional
    public ProjectResponse updateUserProjectDetailsByEmail(String uuid, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));
        return updateUserProjectDetails(uuid, user);
    }

    @Transactional
    public ProjectResponse updateProjectDescriptionByEmail(String email, String uuid, String description) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));
        return updateProjectDescription(user, uuid, description);
    }

    @Transactional
    public ProjectResponse updateProjectNameByEmail(String email, String uuid, String newName) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));
        return updateProjectName(user, uuid, newName);
    }

    @Transactional
    public ProjectDeleteResponse deleteProjectAndGetResponse(String email, String uuid) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));
        List<String> deletedUuids = deleteProject(user, uuid);
        return new ProjectDeleteResponse("프로젝트가 성공적으로 삭제되었습니다.", deletedUuids);
    }

    @Transactional
    public ProjectCleanResponse cleanAllProjectsByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));
        List<String> deletedUuids = deleteAllProjects(user);
        return new ProjectCleanResponse("프로젝트 일괄 삭제 처리가 완료되었습니다.", deletedUuids, deletedUuids.size());
    }

    // 비즈니스 코어 로직 레이어 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public void createUserDirectory(String userIdentifier) {
        Path path = Paths.get(baseStoragePath, userIdentifier);
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path);
                log.info("사용자 루트 디렉토리 생성 완료: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new IllegalStateException("사용자 디렉토리 시스템 생성 실패: " + userIdentifier, e);
        }
    }

    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public ProjectResponse createProject(User user, ProjectCreateRequest requestDto) {
        if ("FULL_STACK".equals(requestDto.getArchitecture_type())) {
            if (hasValue(requestDto.getFullstack_framework()) && !hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                return setFrameworkForAddProjectAndFiles(user, requestDto, requestDto.getFullstack_framework());
            }
        }
        else if ("CLIENT_SERVER".equals(requestDto.getArchitecture_type())) {
            if (hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                return setFrameworkForAddProjectAndFiles(user, requestDto, requestDto.getBackend_framework());
            }
            else if (!hasValue(requestDto.getBackend_framework()) && hasValue(requestDto.getFrontend_framework())) {
                return setFrameworkForAddProjectAndFiles(user, requestDto, requestDto.getFrontend_framework());
            }
        }
        return null;
    }

    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public ProjectResponse updateProjectDescription(User user, String uuid, String description) {
        Project project = projectRepository.findByUuidAndUser(uuid, user)
                .orElseThrow(() -> new ProjectNotFoundException("해당 프로젝트를 찾을 수 없거나 접근 권한이 없습니다. UUID: " + uuid));

        project.updateDescription(description);
        return convertToDto(project);
    }

    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public ProjectResponse updateProjectName(User user, String uuid, String newName) {
        Project project = projectRepository.findByUuid(uuid)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ProjectNotFoundException("프로젝트를 찾을 수 없거나 권한이 없습니다. UUID: " + uuid));

        project.updateName(newName);
        return convertToDto(project);
    }

    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public List<String> deleteProject(User user, String uuid) {
        eventPublisher.publishEvent(new ProjectDeleteVerificationEvent(uuid));

        Project project = projectRepository.findByUuid(uuid)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ProjectNotFoundException("프로젝트를 찾을 수 없거나 권한이 없습니다. UUID: " + uuid));

        projectLogService.deleteLogsByProjectId(project.getId());
        projectRepository.delete(project);

        Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);
        deleteDirectoryRecursive(projectPath);

        log.info("[StorageService] 프로젝트 단건 삭제 완료. UUID: {}", uuid);
        return List.of(uuid);
    }

    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public List<String> deleteAllProjects(User user) {
        List<Project> userProjects = projectRepository.findByUser(user);

        if (userProjects.isEmpty()) {
            return Collections.emptyList();
        }

        List<Project> deletableProjects = userProjects.stream()
                .filter(project -> {
                    try {
                        eventPublisher.publishEvent(new ProjectDeleteVerificationEvent(project.getUuid()));
                        return true;
                    } catch (IllegalStateException e) {
                        log.warn("[StorageService] 프로젝트 전체 삭제 중 제외됨 (생성 중 작업 존재) - UUID: {}", project.getUuid());
                        return false;
                    }
                })
                .toList();

        if (deletableProjects.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> deletedUuids = deletableProjects.stream()
                .map(Project::getUuid)
                .toList();

        List<Long> deletableProjectIds = deletableProjects.stream()
                .map(Project::getId)
                .toList();

        projectRepository.deleteAllByIdIn(deletableProjectIds);
        deletableProjects.forEach(project -> {
            Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), project.getUuid());
            deleteDirectoryRecursive(projectPath);
        });

        log.info("[StorageService] 사용자의 가용 프로젝트 일괄 삭제 완료. Count: {}", deletableProjectIds.size());
        return deletedUuids;
    }

    public byte[] downloadProject(User user, String uuid) {
        projectRepository.findByUuid(uuid)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ProjectNotFoundException("프로젝트를 찾을 수 없거나 권한이 없습니다. UUID: " + uuid));

        Path sourceDirPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);

        if (!Files.exists(sourceDirPath) || !Files.isDirectory(sourceDirPath)) {
            throw new ProjectNotFoundException("저장된 프로젝트 물리 디렉토리가 존재하지 않습니다.");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(outputStream);
             Stream<Path> walk = Files.walk(sourceDirPath)) {

            walk.filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        String zipEntryName = sourceDirPath.relativize(path).toString().replace("\\", "/");
                        try {
                            ZipEntry zipEntry = new ZipEntry(zipEntryName);
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException("파일 압축 공정 중 물리 에러 발생: " + path, e);
                        }
                    });

            zos.finish();
            return outputStream.toByteArray();

        } catch (UncheckedIOException | IOException e) {
            throw new IllegalStateException("프로젝트 자원 압축 패킹 공정 중 시스템 장애가 발생했습니다.", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getUserProjectDetails(User user) {
        List<Project> projects = projectRepository.findByUserOrderByLastModifiedAtDesc(user);
        if (projects.isEmpty()) return Collections.emptyList();

        return projects.stream()
                .map(this::convertToDto)
                .toList();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "projectTree", key = "#projectUuid", cacheManager = "cacheManager"),
            @CacheEvict(value = "projectList", key = "#result.user.id", cacheManager = "cacheManager")
    })
    public Project indexProjectFiles(String projectUuid) {
        // [주의] 만약 앞서 레포지토리에 fetch join을 걸어두었다면
        // JSONB 전환 후에는 일반 findByUuid(projectUuid)만 호출해도 N+1 없이 초고속으로 긁어옵니다.
        Project project = projectRepository.findByUuid(projectUuid)
                .orElseThrow(() -> new ProjectNotFoundException("해당 프로젝트 장부가 존재하지 않습니다: " + projectUuid));

        Path projectFolderPath = nfsRootPath
                .resolve(String.valueOf(project.getUser().getId()))
                .resolve(projectUuid)
                .normalize();

        try (Stream<Path> stream = Files.walk(projectFolderPath)) {

            List<Path> allPaths = stream
                    .filter(path -> !path.equals(projectFolderPath))
                    .filter(path -> {
                        String relativeStr = projectFolderPath.relativize(path).toString().replace("\\", "/");
                        String[] pathParts = relativeStr.split("/");

                        boolean isIgnored = ignoreDirs.stream().anyMatch(ignoreDir -> {
                            if (ignoreDir.isBlank()) return false;
                            for (String part : pathParts) {
                                if (part.equals(ignoreDir)) return true;
                            }
                            return false;
                        });

                        if (isIgnored) return false;

                        String fileName = path.getFileName().toString();
                        return !fileName.equals(".") && !fileName.equals("..");
                    })
                    .toList();

            List<ProjectNode> nodes = allPaths.stream()
                    .map(path -> {
                        String relativePath = projectFolderPath.relativize(path).toString().replace("\\", "/");
                        String type = Files.isDirectory(path) ? "DIR" : "FILE";
                        return new ProjectNode(relativePath, type);
                    })
                    .toList();

            long totalSize = allPaths.stream()
                    .filter(path -> !Files.isDirectory(path))
                    .mapToLong(path -> {
                        try { return Files.size(path); }
                        catch (IOException e) { return 0L; }
                    })
                    .sum();

            int fileCount = (int) allPaths.stream()
                    .filter(path -> !Files.isDirectory(path))
                    .count();

            // [성능 개션 구역]
            // 여기서 updateStorageMeta가 실행될 때, 예전처럼 수백 번의 DELETE/INSERT 쿼리가 난사되지 않고
            // 단 1번의 묵직한 단일 ROW 'UPDATE project SET file_nodes = ...' 쿼리만 나가며 마감됩니다.
            project.updateStorageMeta(totalSize, fileCount, nodes);
            return project;

        } catch (IOException e) {
            throw new IllegalStateException("프로젝트 자원 색인 마감 공정이 정상 실패했습니다.", e);
        }
    }


    @Cacheable(value = "projectTree", key = "#uuid", cacheManager = "cacheManager")
    @Transactional(readOnly = true)
    public List<ProjectNodeResponse> getProjectTree(String email, String uuid) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new ProjectNotFoundException("프로젝트 장부를 찾을 수 없습니다. UUID: " + uuid));

        // [N+1 해결] 이제 project.getFileNodes()를 때려도 지연 로딩 서브 쿼리가 단 1줄도 나가지 않습니다!
        // 이미 메인 쿼리 한 방으로 DB에서 이쁘게 정렬된 JSON 문자열을 가져와 파싱해 둔 상태이기 때문입니다.
        return project.getFileNodes().stream()
                .map(ProjectNodeResponse::new)
                .toList();
    }

    public String getFileContent(String email, String uuid, String path) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new ProjectNotFoundException("프로젝트 장부를 찾을 수 없습니다. UUID: " + uuid));

        try {
            Path rootPath = Paths.get(baseStoragePath).normalize();
            Path targetFilePath = rootPath
                    .resolve(String.valueOf(user.getId()))
                    .resolve(uuid)
                    .resolve(path)
                    .normalize();

            Path userProjectBoundary = rootPath.resolve(String.valueOf(user.getId())).resolve(uuid);
            if (!targetFilePath.startsWith(userProjectBoundary)) {
                throw new SecurityException("비정상적인 우회 파일 접근 시도입니다.");
            }

            if (!Files.exists(targetFilePath) || Files.isDirectory(targetFilePath)) {
                throw new ProjectNotFoundException("요청하신 소스 파일이 디스크에 존재하지 않습니다.");
            }

            String fileName = targetFilePath.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".jar") || fileName.endsWith(".class") || fileName.endsWith(".zip")) {
                return "[Binary File] 다운로드만 가능한 바이너리 파일입니다. 에디터에서 내용을 표시할 수 없습니다.";
            }

            return Files.readString(targetFilePath, StandardCharsets.UTF_8);

        } catch (MalformedInputException e) {
            log.warn("[StorageService] 인코딩 오류 - 텍스트 파일이 아닙니다. Path: {}", path);
            return "[Binary File] 인코딩할 수 없는 파일 포맷입니다.";
        } catch (IOException e) {
            throw new IllegalStateException("NFS 소스 코드 파일 텍스트 판독 스트림 개설에 실패했습니다.", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public FrameworkStatisticsResponse getFrameworkStatistics(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

        Cache cache = cacheManager.getCache("projectList");
        List<ProjectResponse> cachedProjects = null;

        if (cache != null) {
            cachedProjects = cache.get(user.getId(), List.class);
        }

        // 캐시 장부가 비어있다면, '진짜 프록시가 걸린 퍼블릭 메서드'를 호출하거나 레포지토리를 직접 조회합니다.
        if (cachedProjects == null) {
            List<Project> projects = projectRepository.findByUserOrderByLastModifiedAtDesc(user);
            cachedProjects = projects.stream().map(this::convertToDto).toList();
        }

        long totalCount = cachedProjects.size();
        Map<String, Long> frameworkCounts = cachedProjects.stream()
                .collect(Collectors.groupingBy(
                        ProjectResponse::getFramework,
                        Collectors.counting()
                ));

        return new FrameworkStatisticsResponse(totalCount, frameworkCounts);
    }

    @Transactional(readOnly = true)
    public long getUserTotalStorageUsage(User user) {
        return projectRepository.getTotalStorageSizeByUser(user);
    }

    @Transactional(readOnly = true)
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public ProjectResponse updateUserProjectDetails(String uuid, User user) {
        Project project = projectRepository.findByUuidAndUser(uuid, user)
                .orElseThrow(() -> new ProjectNotFoundException("해당 프로젝트를 찾을 수 없습니다. UUID: " + uuid));

        return convertToDto(project);
    }

    // 내부 인프라 제어 유틸 헬퍼 메서드 계열 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void deleteDirectoryRecursive(Path path) {
        if (Files.exists(path)) {
            // [자원 누수 방지] try-with-resources 문을 도입하여 파일 디스크립터 유출을 철저히 차단합니다!
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) { // File.delete() 결과 무시 경고 진압
                                log.warn("[NFS Warning] 파일 물리 삭제에 실패했습니다: {}", file.getAbsolutePath());
                            }
                        });
            } catch (IOException e) {
                throw new IllegalStateException("물리적 자원 청소 마감 처리에 실패했습니다.", e);
            }
        }
    }

    private void deleteDirectory(Path path) {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) { // try-with-resources 추가
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); }
                            catch (IOException ignored) {}
                        });
            } catch (IOException e) {
                log.error("물리 폴더 비상 복구 삭제 실패: {}", path, e);
            }
        }
    }

    private AiModel resolveModel(User user, String requestModel) {
        return Optional.ofNullable(requestModel)
                .filter(m -> !m.isBlank())
                .flatMap(aiModelRepository::findByModelNameAndIsActiveTrue)
                .or(() -> Optional.ofNullable(user.getModel()))
                .or(() -> aiModelRepository.findByDefaultActiveTrue())
                .orElseThrow(() -> new ModelNotFoundException("사용 가능한 AI 모델이 시스템에 존재하지 않습니다."));
    }

    private boolean hasValue(String str) {
        return str != null && !str.trim().isEmpty();
    }

    private ProjectResponse setFrameworkForAddProjectAndFiles(User user, ProjectCreateRequest requestDto, String framework) {
        String uuid = UUID.randomUUID().toString();
        TemplateInitializer initializer = factory.getInitializer(framework);
        AiModel targetModel = resolveModel(user, requestDto.getModel());

        Project project = projectRepository.save(Project.builder()
                .name(requestDto.getProjectName())
                .description("")
                .uuid(uuid)
                .framework(framework)
                .user(user)
                .model(targetModel)
                .build());

        Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);
        try {
            Files.createDirectories(projectPath);
            initializer.initialize(projectPath, requestDto);
        } catch (Exception e) {
            deleteDirectory(projectPath);
            throw new IllegalStateException("프로젝트 물리 세그먼트 생성 공정이 실패했습니다. UUID: " + uuid, e);
        }

        return convertToDto(project);
    }

    private ProjectResponse convertToDto(Project project) {
        return ProjectResponse.builder()
                .projectName(project.getName())
                .description(project.getDescription())
                .status(project.getStatus().toString())
                .uuid(project.getUuid())
                .framework(project.getFramework())
                .model(project.getModel().getModelName())
                .createdAt(project.getCreatedAt().format(DATE_FORMATTER))
                .lastModified(project.getLastModifiedAt().format(DATE_FORMATTER))
                .size(project.getTotalSize())
                .fileCount(project.getFileCount())
                .build();
    }
}
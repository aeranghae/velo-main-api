package cloud.velo.main.service;

import cloud.velo.main.client.AgentConnectionManager;
import cloud.velo.main.dto.response.FrameworkStatisticsResponse;
import cloud.velo.main.dto.request.ProjectCreateRequest;
import cloud.velo.main.dto.response.ProjectNodeResponse;
import cloud.velo.main.dto.response.ProjectResponse;
import cloud.velo.main.domain.AiModel;
import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.ProjectNode;
import cloud.velo.main.domain.User;
import cloud.velo.main.event.ProjectDeleteVerificationEvent;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
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
    private Path nfsRootPath;  // 자바 nio가 안전하게 사용할 진짜 Path 객체 변수를 선언합니다.

    @Value("#{'${velo.ignore.directories:}'.split(',')}")
    private List<String> ignoreDirs;

    /**
     *  [스프링 라이프사이클 훅]
     * 스프링이 켜지면서 @Value 주입을 완전히 마친 직후, 문자열을 Path 객체로 안전하게 변환
     */
    @PostConstruct
    public void init() {
        this.nfsRootPath = Paths.get(baseStoragePath).normalize();
        //log.info("StorageService 가동 - 설정된 NFS 루트 경로: {}", this.nfsRootPath);
    }

    // 생성 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

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
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public ProjectResponse createProject(User user, ProjectCreateRequest requestDto) {

        //  분기 검증
        if ("FULL_STACK".equals(requestDto.getArchitecture_type())) {

            // 하나의 프레임워크에서 풀스택으로 구현하는 경우
            if (hasValue(requestDto.getFullstack_framework()) && !hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                return setFrameworkForAddProjectAndFiles(user, requestDto, requestDto.getFullstack_framework());
            }
            // 백엔드와 프론트엔드의 프레임워크 가 분리되어 풀스택으로 구현하는 경우
            else if (!hasValue(requestDto.getFullstack_framework()) && hasValue(requestDto.getBackend_framework()) && hasValue(requestDto.getFrontend_framework())){
                // TODO: 풀스택 중 백엔드+프론트 복합인 경우는 아직 사용 불가
            }
        }
        else if ("CLIENT_SERVER".equals(requestDto.getArchitecture_type())) {

            // 백엔드 프론트엔드 중 백엔드만 구현하는 경우
            if (hasValue(requestDto.getBackend_framework()) && !hasValue(requestDto.getFrontend_framework())) {
                return setFrameworkForAddProjectAndFiles(user, requestDto, requestDto.getBackend_framework());
            }
            // 백엔드 프론트 엔드 중 프론트엔드만 구현하는 경우
            else if (!hasValue(requestDto.getBackend_framework()) && hasValue(requestDto.getFrontend_framework())) {
                return setFrameworkForAddProjectAndFiles(user, requestDto, requestDto.getFrontend_framework());
            }

        }
        return null; // 뭔가 올바르지 않은 값으로 전달되는 경우
    }

    /**
     * 프로젝트 설명 변경
     */
    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public ProjectResponse updateProjectDescription(User user, String uuid, String description) {

        // 1. URL로 들어온 uuid와 인증된 유저 정보로 프로젝트 검증 및 조회
        Project project = projectRepository.findByUuidAndUser(uuid, user)
                .orElseThrow(() -> new IllegalArgumentException("해당 프로젝트를 찾을 수 없거나 수정 권한이 없습니다."));

        // 2. 설명 단독 변경 (내부에서 수정 시간 반영)
        project.updateDescription(description);

        // 3. 기존 규칙대로 NFS 디스크 스캔 없이 DB 데이터로만 즉시 DTO 조립 및 반환
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return ProjectResponse.builder()
                .projectName(project.getName())
                .description(project.getDescription())
                .status(project.getStatus().toString())
                .uuid(project.getUuid())
                .framework(project.getFramework())
                .model(project.getModel().getModelName())
                .createdAt(project.getCreatedAt().format(formatter))
                .lastModified(project.getLastModifiedAt().format(formatter))
                .size(project.getTotalSize())
                .fileCount(project.getFileCount())
                .build();
    }

    /**
     * 프로젝트 이름 변경 (논리적 변경 - NFS 디스크 I/O 제로)
     */
    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public ProjectResponse updateProjectName(User user, String uuid, String newName) {
        // 1. DB에서 프로젝트 조회 및 권한 검증
        Project project = projectRepository.findByUuid(uuid)
                .filter(p -> p.getUser().getId().equals(user.getId())) // 본인 확인
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없거나 권한이 없습니다."));

        // 2. 엔티티 이름 변경 (Dirty Checking에 의해 메서드 종료 시 DB에 반영됨)
        project.updateName(newName);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 무거운 convertToDto(NFS 디스크 스캔)를 버리고, 엔티티 컬럼 값으로 즉시 DTO 조립
        return ProjectResponse.builder()
                .projectName(project.getName())
                .description(project.getDescription())
                .status(project.getStatus().toString())
                .uuid(project.getUuid())
                .framework(project.getFramework())
                .model(project.getModel().getModelName())
                .createdAt(project.getCreatedAt().format(formatter))
                // 엔티티가 이미 들고 있는 최신화된 메타데이터를 그대로 서빙 (NFS 접근 0번)
                .lastModified(project.getLastModifiedAt().format(formatter))
                .size(project.getTotalSize())
                .fileCount(project.getFileCount())
                .build();
    }

    /**
     * 프로젝트 단건 삭제 (DB + 물리 폴더)
     * @return 삭제된 프로젝트의 UUID 목록
     */
    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public List<String> deleteProject(User user, String uuid) {
        eventPublisher.publishEvent(new ProjectDeleteVerificationEvent(uuid));

        Project project = projectRepository.findByUuid(uuid)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없거나 권한이 없습니다."));

        projectLogService.deleteLogsByProjectId(project.getId());
        projectRepository.delete(project);

        Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);
        deleteDirectoryRecursive(projectPath);

        log.info("[StorageService] 프로젝트 단건 삭제 완료. UUID: {}", uuid);
        return List.of(uuid); // 삭제된 UUID 반환
    }

    /**
     * 프로젝트 전체 삭제
     * @return 실제로 삭제 완료된 프로젝트들의 UUID 목록
     */
    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public List<String> deleteAllProjects(User user) {
        List<Project> userProjects = projectRepository.findByUser(user);

        if (userProjects.isEmpty()) {
            return Collections.emptyList();
        }

        // 스트림 필터로 안전하게 삭제 가능한 것만 추출
        List<Project> deletableProjects = userProjects.stream()
                .filter(project -> {
                    try {
                        eventPublisher.publishEvent(new ProjectDeleteVerificationEvent(project.getUuid()));
                        return true;
                    } catch (IllegalStateException e) {
                        log.warn("[StorageService] 프로젝트 전체 삭제 중 제외됨 (현재 생성 작업 진행 중) - UUID: {}", project.getUuid());
                        return false;
                    }
                })
                .collect(Collectors.toList());

        if (deletableProjects.isEmpty()) {
            return Collections.emptyList();
        }

        // [수정] 실제로 삭제할 프로젝트들의 UUID 목록을 미리 땁니다.
        List<String> deletedUuids = deletableProjects.stream()
                .map(Project::getUuid)
                .collect(Collectors.toList());

        List<Long> deletableProjectIds = deletableProjects.stream()
                .map(Project::getId)
                .collect(Collectors.toList());

        // DB 및 물리 삭제 진행
        projectRepository.deleteAllByIdIn(deletableProjectIds);
        deletableProjects.forEach(project -> {
            Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), project.getUuid());
            deleteDirectoryRecursive(projectPath);
        });

        log.info("[StorageService] 사용자의 가용 프로젝트 일괄 물리/논리 삭제 완료. Target Count: {}", deletableProjectIds.size());
        return deletedUuids; // 실제로 지워진 UUID 리스트 반환
    }

    public byte[] downloadProject(User user, String uuid) {
        Project project = projectRepository.findByUuid(uuid)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없거나 권한이 없습니다."));

        Path sourceDirPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);

        if (!Files.exists(sourceDirPath) || !Files.isDirectory(sourceDirPath)) {
            throw new IllegalArgumentException("저장된 프로젝트 디렉토리가 존재하지 않습니다.");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos);
             // 💡 여기에 walk 스트림을 선언하여 try가 끝날 때 자동으로 close() 되게 만듭니다.
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
                            throw new UncheckedIOException("파일 압축 중 오류 발생: " + path, e);
                        }
                    });

            zos.finish();
            return baos.toByteArray();

        } catch (UncheckedIOException | IOException e) {
            throw new RuntimeException("프로젝트 압축 다운로드 중 오류가 발생했습니다.", e);
        }
    }

    // 조회 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    /**
     * 사용자 프로젝트 상세 목록 조회 (DB 정보 + 물리적 통계)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public List<ProjectResponse> getUserProjectDetails(User user) {
        List<Project> projects = projectRepository.findByUserOrderByLastModifiedAtDesc(user);

        if (projects.isEmpty()) return Collections.emptyList();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return projects.stream()
                .map(project -> ProjectResponse.builder()
                        .projectName(project.getName())
                        .description(project.getDescription())
                        .status(project.getStatus().toString())
                        .uuid(project.getUuid())
                        .framework(project.getFramework())
                        .model(project.getModel().getModelName())
                        .createdAt(project.getCreatedAt().format(formatter))
                        // DB 컬럼에 저장해둔 최신화된 수정 시간, 용량, 파일 수를 0.0001초만에 즉시 매핑 (NFS I/O 제로)
                        .lastModified(project.getLastModifiedAt().format(formatter))
                        .size(project.getTotalSize())
                        .fileCount(project.getFileCount())
                        .build())
                .collect(Collectors.toList());
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
     * 프로젝트 디렉토리 내의 모든 파일과 빈 폴더를 색인하여 DB에 저장하고 메타데이터를 갱신합니다.
     */
    @Transactional
    @Caching(evict = {
            // 1. 해당 프로젝트의 파일 트리 캐시 삭제 (수정 시 트리 새로고침용)
            @CacheEvict(value = "projectTree", key = "#projectUuid", cacheManager = "cacheManager"),
            // 2. 메서드가 반환하는 Project 객체에서 user.id를 읽어와 유저 프로젝트 리스트 캐시 파괴 (용량, 개수, 수정시간 반영용)
            @CacheEvict(value = "projectList", key = "#result.user.id", cacheManager = "cacheManager")
    })
    public Project indexProjectFiles(String projectUuid) {
        // 1. 먼저 DB에서 프로젝트와 연관된 유저 정보를 가져옵니다.
        Project project = projectRepository.findByUuid(projectUuid)
                .orElseThrow(() -> new IllegalArgumentException("해당 프로젝트가 존재하지 않습니다: " + projectUuid));

        // 2. nfsRootPath / userId / projectUuid 순서로 안전하게 조립합니다.
        Path projectFolderPath = nfsRootPath
                .resolve(String.valueOf(project.getUser().getId()))
                .resolve(projectUuid)
                .normalize();

        // 3. Files.walk 한 번으로 트리 색인 + 용량 계산 + 파일 수 카운트를 원패스로 처리합니다.
        try (Stream<Path> stream = Files.walk(projectFolderPath)) {

            // 디스크 스캔 결과를 메모리 리스트로 전수 수집
            List<Path> allPaths = stream
                    .filter(path -> !path.equals(projectFolderPath))
                    .filter(path -> {
                        // 윈도우 환경대응을 위해 백슬래시를 전면 슬래시로 교정
                        String relativeStr = projectFolderPath.relativize(path).toString().replace("\\", "/");

                        // "gradlew"가 ".gradle" 폴더명에 휩쓸려 삭제되는 contains() 버그 방어선
                        // 경로를 디렉토리 단위 배열로 쪼개어 '정확히 단어 전체가 일치'하는지 검사합니다.
                        String[] pathParts = relativeStr.split("/");

                        boolean isIgnored = ignoreDirs.stream().anyMatch(ignoreDir -> {
                            if (ignoreDir.isBlank()) return false;

                            for (String part : pathParts) {
                                if (part.equals(ignoreDir)) {
                                    return true; // 제외 타겟 폴더명과 100% 완전히 일치하는 구역만 걸러냅니다.
                                }
                            }
                            return false;
                        });

                        if (isIgnored) return false;

                        // 파일명 자체가 "." 이거나 ".." 인 리눅스 시스템 폴더 기호 기각 (.gitignore 등은 정상 생존)
                        String fileName = path.getFileName().toString();
                        return !fileName.equals(".") && !fileName.equals("..");
                    })
                    .collect(Collectors.toList());

            // 리액트 트리 뷰용 노드 데이터 조립
            List<ProjectNode> nodes = allPaths.stream()
                    .map(path -> {
                        String relativePath = projectFolderPath.relativize(path).toString().replace("\\", "/");
                        String type = Files.isDirectory(path) ? "DIR" : "FILE";
                        return new ProjectNode(relativePath, type);
                    })
                    .collect(Collectors.toList());

            // 프로젝트 총 용량 계산 (일반 파일들의 Bytes 크기 합산)
            long totalSize = allPaths.stream()
                    .filter(path -> !Files.isDirectory(path))
                    .mapToLong(path -> {
                        try { return Files.size(path); }
                        catch (IOException e) { return 0L; }
                    })
                    .sum();

            // 순수 파일 개수 카운트 (디렉토리 제외)
            int fileCount = (int) allPaths.stream()
                    .filter(path -> !Files.isDirectory(path))
                    .count();

            // 엔티티 내부 편의 메서드를 통해 DB 실물 컬럼 일괄 업데이트! (totalSize, fileCount, fileNodes 일괄 리프레시)
            project.updateStorageMeta(totalSize, fileCount, nodes);

            return project;

        } catch (IOException e) {
            throw new RuntimeException("프로젝트 파일 색인 및 메타데이터 갱신 실패. 경로: " + projectFolderPath, e);
        }
    }

    /**
     * 프로젝트 파일 트리 구조 조회 (레디스 캐시 탑재)
     */
    @Cacheable(value = "projectTree", key = "#uuid", cacheManager = "cacheManager")
    @Transactional(readOnly = true)
    public List<ProjectNodeResponse> getProjectTree(String email, String uuid) {
        // 1. 유저 및 프로젝트 검증
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));

        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("해당 프로젝트가 존재하지 않습니다."));

        // 다른 유저의 프로젝트 파일을 훔쳐보는 것을 방지하는 소유권 검증
        if (!project.getUser().getId().equals(user.getId())) {
            throw new SecurityException("해당 프로젝트에 접근 권한이 없습니다.");
        }

        // 2. DB 장부에 기록된 빈 폴더 + 파일 리스트를 DTO로 변환하여 리턴 (이후 캐싱됨)
        return project.getFileNodes().stream()
                .map(ProjectNodeResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * 특정 파일 내용 실시간 로드 (NFS I/O 담당)
     */
    public String getFileContent(String email, String uuid, String path) {
        // 1. 유저 및 프로젝트 검증
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));

        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("해당 프로젝트가 존재하지 않습니다."));

        if (!project.getUser().getId().equals(user.getId())) {
            throw new SecurityException("해당 프로젝트에 접근 권한이 없습니다.");
        }

        try {
            // 2. 물리 저장소 절대 경로 조립
            Path rootPath = Paths.get(baseStoragePath).normalize();
            Path targetFilePath = rootPath
                    .resolve(String.valueOf(user.getId()))
                    .resolve(uuid)
                    .resolve(path)
                    .normalize();

            // 상위 디렉토리 탈출 해킹 공격(../) 방어선 구축
            Path userProjectBoundary = rootPath.resolve(String.valueOf(user.getId())).resolve(uuid);
            if (!targetFilePath.startsWith(userProjectBoundary)) {
                throw new SecurityException("비정상적인 파일 접근 시도입니다.");
            }

            // 3. 파일 유효성 체크
            if (!Files.exists(targetFilePath) || Files.isDirectory(targetFilePath)) {
                throw new IllegalArgumentException("파일을 찾을 수 없거나 올바른 파일 포맷이 아닙니다.");
            }

            // [교정 핵심]: 바이너리 파일 차단 Guard 설정
            // .jar, .class, .png 등 텍스트로 읽을 수 없는 확장자는 사전에 걸러내어 에러를 방지합니다.
            String fileName = targetFilePath.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".jar") || fileName.endsWith(".class") || fileName.endsWith(".zip")) {
                return "[Binary File] 다운로드만 가능한 바이너리 파일입니다. 에디터에서 내용을 표시할 수 없습니다.";
            }

            // 4. NFS 디스크에서 진짜 소스 코드 텍스트 긁어오기
            return Files.readString(targetFilePath, StandardCharsets.UTF_8);

        } catch (MalformedInputException e) {
            //[인프라 가드]: 만약 확장자 체크를 놓친 다른 이진 파일이 들어와도 서버가 터지지 않게 예외 격리
            log.warn("[StorageService] 인코딩 오류 - 텍스트 파일이 아닙니다. Path: {}", path);
            return "[Binary File] 인코딩할 수 없는 파일 포맷입니다.";
        } catch (IOException e) {
            log.error("NFS 파일 읽기 실패 - UUID: {}, Path: {}", uuid, path, e);
            throw new RuntimeException("파일 시스템에서 내용을 읽어오지 못했습니다.", e);
        }
    }

    /**
     * 프레임워크 통계 반환
     */
    @Transactional(readOnly = true)
    public FrameworkStatisticsResponse getFrameworkStatistics(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));

        // 레디스의 'projectList' 캐시 저장소에서 이 유저의 캐시 데이터 꺼내기
        Cache cache = cacheManager.getCache("projectList");
        List<ProjectResponse> cachedProjects = null;

        if (cache != null) {
            // key = "#user.id" 로 캐싱하셨으므로 user.getId()로 장부를 조회합니다.
            cachedProjects = cache.get(user.getId(), List.class);
        }

        // 3. 만약 캐시 장부가 비어있다면 (첫 요청이거나 캐시가 만료된 경우) 어쩔 수 없이 DB 스캔 후 캐시 굽기 트리거
        if (cachedProjects == null) {
            // 기존에 만들어두신 캐시 메서드를 내부 호출하여 강제로 레디스에 적재하고 가져옵니다.
            cachedProjects = getUserProjectDetails(user);
        }

        // 레디스에서 가져온 프로젝트 리스트 기반으로 스트림 집계 돌리기
        long totalCount = cachedProjects.size();

        Map<String, Long> frameworkCounts = cachedProjects.stream()
                .collect(Collectors.groupingBy(
                        ProjectResponse::getFramework,
                        Collectors.counting()
                ));

        return new FrameworkStatisticsResponse(totalCount, frameworkCounts);
    }

    /**
     * 유저의 전체 스토리지 사용량 조회 (NFS 디스크 스캔 X, DB 초고속 합산)
     */
    @Transactional(readOnly = true)
    public long getUserTotalStorageUsage(User user) {
        // DB 에 적힌 프로젝트 용량만 합산해서 즉시 리턴합니다.
        return projectRepository.getTotalStorageSizeByUser(user);
    }

    // 사용자 특정 Uuid 프로젝트 갱신
    @Transactional(readOnly = true)
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public ProjectResponse updateUserProjectDetails(String uuid, User user) {
        // 앞서 안내해 드린 안전한 예외 처리 방식으로 변경하는 것을 추천합니다.
        Project project = projectRepository.findByUuidAndUser(uuid, user)
                .orElseThrow(() -> new IllegalArgumentException("해당 프로젝트를 찾을 수 없습니다."));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return ProjectResponse.builder()
                .projectName(project.getName())
                .description(project.getDescription())
                .status(project.getStatus().toString())
                .uuid(project.getUuid())
                .framework(project.getFramework())
                .model(project.getModel().getModelName())
                .createdAt(project.getCreatedAt().format(formatter))
                .lastModified(project.getLastModifiedAt().format(formatter))
                .size(project.getTotalSize())
                .fileCount(project.getFileCount())
                .build();
    }


    // 헬퍼 메서드  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // 폴더 내부까지 재귀적으로 삭제하는 헬퍼 메서드
    private void deleteDirectoryRecursive(Path path) {
        if (Files.exists(path)) {
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()) // 하위 파일부터 삭제
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                throw new RuntimeException("물리적 폴더 삭제 실패: " + path, e);
            }
        }
    }

    // 오류 발생 시 uuid 기반 폴더 삭제
    private void deleteDirectory(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); }
                            catch (IOException ignored) {}
                        });
            }
        } catch (IOException e) {
            log.error("폴더 삭제 실패: {}", path, e);
        }
    }

    // AI모델 선택 예외 처리
    private AiModel resolveModel(User user, String requestModel) {
        // 1. 요청 모델명 → 2. 유저 기본 모델 → 3. 시스템 기본 모델 순으로 탐색
        return Optional.ofNullable(requestModel)
                .filter(m -> !m.isBlank())
                .flatMap(aiModelRepository::findByModelNameAndIsActiveTrue)
                .or(() -> Optional.ofNullable(user.getModel()))
                .or(() -> aiModelRepository.findByDefaultActiveTrue())
                .orElseThrow(() -> new IllegalStateException("사용 가능한 AI 모델이 시스템에 존재하지 않습니다."));
    }

    /**
     * 문자열이 null이 아니고 비어있지 않은지(공백 제외) 체크하는 가벼운 유틸 메서드
     */
    private boolean hasValue(String str) {
        return str != null && !str.trim().isEmpty();
    }


    /**
    *  프로젝트 요구사항 기반 프로젝트 프레임워크 지정 분기 편의 메서드
    */
    private ProjectResponse setFrameworkForAddProjectAndFiles(User user, ProjectCreateRequest requestDto, String framework) {
        String uuid = UUID.randomUUID().toString();

        // 1. 프레임워크 검증 먼저 (폴더 생성 전에)
        TemplateInitializer initializer = factory.getInitializer(framework);

        // 2. 모델 결정
        AiModel targetModel = resolveModel(user, requestDto.getModel());

        // 3. DB 저장 (엔티티 생성자 내부에서 totalSize=0L, fileCount=0으로 안전하게 초기화됨)
        Project project = projectRepository.save(Project.builder()
                .name(requestDto.getProjectName())
                .description("")
                .uuid(uuid)
                .framework(framework)
                .user(user)
                .model(targetModel)
                .build());

        // 4. 폴더 생성 + 템플릿 초기화
        Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);
        try {
            Files.createDirectories(projectPath);
            initializer.initialize(projectPath, requestDto);
        } catch (Exception e) {
            deleteDirectory(projectPath);
            throw new RuntimeException("프로젝트 폴더 생성 실패: " + uuid, e);
        }

        // TODO: [로그상태갱신] 프로젝트 생성 완료

        // TODO: [로그상태갱신] 프로젝트 데이터 저장 완료

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return ProjectResponse.builder()
                .projectName(project.getName())
                .description(project.getDescription())
                .status(project.getStatus().toString())
                .uuid(project.getUuid())
                .framework(project.getFramework())
                .model(project.getModel().getModelName())
                .createdAt(project.getCreatedAt().format(formatter))
                .lastModified(project.getLastModifiedAt().format(formatter))
                .size(0L)       // 도메인 규칙에 따른 초기값 고정 (NFS I/O 제로)
                .fileCount(0)   // 도메인 규칙에 따른 초기값 고정 (NFS I/O 제로)
                .build();
    }
}
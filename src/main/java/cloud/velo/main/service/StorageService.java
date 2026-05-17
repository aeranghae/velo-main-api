package cloud.velo.main.service;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.controller.dto.ProjectNodeResponse;
import cloud.velo.main.controller.dto.ProjectResponseDto;
import cloud.velo.main.domain.AiModel;
import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.ProjectNode;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.AiModelRepository;
import cloud.velo.main.repository.ProjectRepository;
import cloud.velo.main.repository.UserRepository;
import cloud.velo.main.util.template.TemplateInitializer;
import cloud.velo.main.util.template.TemplateInitializerFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final ProjectRepository projectRepository;
    private final AiModelRepository aiModelRepository;
    private final UserRepository userRepository;
    private final TemplateInitializerFactory factory;

    @Value("${velo.storage.path}")
    private String baseStoragePath;
    private Path nfsRootPath;  // 자바 nio가 안전하게 사용할 진짜 Path 객체 변수를 선언합니다.

    @Value("#{'${velo.ignore.directories:}'.split(',')}")
    private List<String> ignoreDirs;

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
    public ProjectResponseDto createProject(User user, ProjectCreateRequestDto requestDto) {
        String uuid = UUID.randomUUID().toString();

        // 1. 프레임워크 검증 먼저 (폴더 생성 전에)
        TemplateInitializer initializer = factory.getInitializer(requestDto.getFramework());

        // 2. 모델 결정
        AiModel targetModel = resolveModel(user, requestDto.getModel());

        // 3. DB 저장 (엔티티 생성자 내부에서 totalSize=0L, fileCount=0으로 안전하게 초기화됨)
        Project project = projectRepository.save(Project.builder()
                .name(requestDto.getProjectName())
                .uuid(uuid)
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

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 5. 🚀 [수정] 무거운 convertToDto를 버리고 디스크 스캔 없이 0L, 0개로 DTO 즉시 조립 및 반환
        return ProjectResponseDto.builder()
                .projectName(project.getName())
                .uuid(project.getUuid())
                .model(project.getModel().getModelName())
                .createdAt(project.getCreatedAt().format(formatter))
                .lastModified(project.getLastModifiedAt().format(formatter))
                .size(0L)       // 도메인 규칙에 따른 초기값 고정 (NFS I/O 제로)
                .fileCount(0)   // 도메인 규칙에 따른 초기값 고정 (NFS I/O 제로)
                .build();
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
     * 사용자 프로젝트 상세 목록 조회 (DB 정보 + 물리적 통계)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public List<ProjectResponseDto> getUserProjectDetails(User user) {
        List<Project> projects = projectRepository.findByUserOrderByLastModifiedAtDesc(user);

        if (projects.isEmpty()) return Collections.emptyList();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return projects.stream()
                .map(project -> ProjectResponseDto.builder()
                        .projectName(project.getName())
                        .uuid(project.getUuid())
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
     * 유저의 전체 스토리지 사용량 조회 (NFS 디스크 스캔 X, DB 초고속 합산)
     */
    @Transactional(readOnly = true)
    public long getUserTotalStorageUsage(User user) {
        // DB 에 적힌 프로젝트 용량만 합산해서 즉시 리턴합니다.
        return projectRepository.getTotalStorageSizeByUser(user);
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
     * 프로젝트 이름 변경 (논리적 변경 - NFS 디스크 I/O 제로)
     */
    @Transactional
    @CacheEvict(value = "projectList", key = "#user.id", cacheManager = "cacheManager")
    public ProjectResponseDto updateProjectName(User user, String uuid, String newName) {
        // 1. DB에서 프로젝트 조회 및 권한 검증
        Project project = projectRepository.findByUuid(uuid)
                .filter(p -> p.getUser().getId().equals(user.getId())) // 본인 확인
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없거나 권한이 없습니다."));

        // 2. 엔티티 이름 변경 (Dirty Checking에 의해 메서드 종료 시 DB에 반영됨)
        project.updateName(newName);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 무거운 convertToDto(NFS 디스크 스캔)를 버리고, 엔티티 컬럼 값으로 즉시 DTO 조립
        return ProjectResponseDto.builder()
                .projectName(project.getName())
                .uuid(project.getUuid())
                .model(project.getModel().getModelName())
                .createdAt(project.getCreatedAt().format(formatter))
                // 엔티티가 이미 들고 있는 최신화된 메타데이터를 그대로 서빙 (NFS 접근 0번)
                .lastModified(project.getLastModifiedAt().format(formatter))
                .size(project.getTotalSize())
                .fileCount(project.getFileCount())
                .build();
    }

    /**
     * 프로젝트 삭제 (DB + 물리 폴더)
     */
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

    /**
     * 폴더 내부까지 재귀적으로 삭제하는 헬퍼 메서드
     */
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

    /**
     *  [스프링 라이프사이클 훅]
     * 스프링이 켜지면서 @Value 주입을 완전히 마친 직후, 문자열을 Path 객체로 안전하게 변환
     */
    @PostConstruct
    public void init() {
        this.nfsRootPath = Paths.get(baseStoragePath).normalize();
        log.info("StorageService 가동 - 설정된 NFS 루트 경로: {}", this.nfsRootPath);
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
                        String relativeStr = projectFolderPath.relativize(path).toString().replace("\\", "/");

                        // yml 장부에 등록된 폴더명(ex: .git, .idea, node_modules) 중 하나라도 경로에 속해 있다면 탈락
                        boolean isIgnored = ignoreDirs.stream().anyMatch(ignoreDir ->
                                !ignoreDir.isBlank() && (
                                        relativeStr.startsWith(ignoreDir + "/") ||
                                                relativeStr.contains("/" + ignoreDir + "/")
                                )
                        );

                        if (isIgnored) return false;

                        // 파일명 자체가 "." 이거나 ".." 인 리눅스 시스템 폴더 기호가 아니라면 다 허용 (.gitignore 등은 생존)
                        String fileName = path.getFileName().toString();
                        return !fileName.equals(".") && !fileName.equals("..");
                    })
                    .collect(Collectors.toList());

            // 리액트 트리 뷰용 노드 데이터 조립
            List<ProjectNode> nodes = allPaths.stream()
                    .map(path -> {
                        String relativePath = projectFolderPath.relativize(path).toString();
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
            // 2. 물리 저장소 절대 경로 조립 (/app/storage/userdir/{userId}/{projectUuid}/{relativePath})
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

            // 4. NFS 디스크에서 진짜 소스 코드 텍스트 긁어오기
            return Files.readString(targetFilePath, java.nio.charset.StandardCharsets.UTF_8);

        } catch (IOException e) {
            log.error("NFS 파일 읽기 실패 - UUID: {}, Path: {}", uuid, path, e);
            throw new RuntimeException("파일 시스템에서 내용을 읽어오지 못했습니다.", e);
        }
    }

}
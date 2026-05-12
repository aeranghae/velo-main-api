package cloud.aeranghae.main.service;

import cloud.aeranghae.main.controller.dto.ProjectResponseDto;
import cloud.aeranghae.main.domain.Project;
import cloud.aeranghae.main.domain.User;
import cloud.aeranghae.main.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    public ProjectResponseDto createProject(User user, String projectName) {
        String uuid = UUID.randomUUID().toString();

        // 1. DB에 프로젝트 메타데이터 저장
        Project project = projectRepository.save(Project.builder()
                .name(projectName)
                .uuid(uuid)
                .user(user)
                .build());

        // 2. 물리적 폴더 생성: {baseStoragePath}/{userId}/{uuid}
        Path projectPath = Paths.get(baseStoragePath, String.valueOf(user.getId()), uuid);
        try {
            Files.createDirectories(projectPath);

            //TODO: 각종 스택 요구사항 파싱 및 LLM서버로 요청 전달
            // 1. 개발 요청을 진행 (LLM에서 코드를 작성) 전체 파일 개수
            // 2. 스프링으로 전달
            // 3. 파일 작성 <- 해당 사용자에게 로그 전송 (로그를 지속적으로 파일에 작성하게하고 사용자는 필요할때마다 들어와서 해당 로그파일을 읽는 방식..?)
            // 4. 로그가 작성이되는걸 실시간으로 전송 (단 사용자가 해당 메뉴에 있을 경우에만 아니면 전송안함)
            
        } catch (IOException e) {
            throw new RuntimeException("프로젝트 폴더 생성 실패: " + uuid, e);
        }

        return convertToDto(project, projectPath);
    }

    /**
     * 사용자 프로젝트 상세 목록 조회 (DB 정보 + 물리적 통계)
     */
    @Transactional(readOnly = true)
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
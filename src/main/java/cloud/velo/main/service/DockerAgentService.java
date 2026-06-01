package cloud.velo.main.service;

import cloud.velo.main.dto.common.AiModelMessage;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerAgentService {

    private final DockerClient dockerClient;

    @Value("${velo.storage.path}")
    private String baseStoragePath;

    @Value("${velo.storage.realpath}")
    private String baseStorageRealPath;

    /**
     * [기능 1] 지정된 NFS 경로에 파일 작성 (다중 프레임워크 패키지 트리 구조 지원)
     */
    public void writeFile(String userId, String uuid, String relativePath, String content) {
        File baseDirFile = new File(new File(baseStoragePath, userId), uuid);

        if (!baseDirFile.exists() && !baseDirFile.mkdirs()) {
            throw new IllegalStateException("NFS 샌드박스 루트 디렉토리 물리 생성 실패");
        }

        File file = new File(baseDirFile, relativePath);
        File parentDir = file.getParentFile();

        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IllegalStateException("하위 소스 코드 패키지 물리 디렉토리 생성 실패: " + parentDir.getAbsolutePath());
        }

        //  체크 예외는 터지는 그 즉시 낚아채서 언체크 예외로 변환 격발합니다.
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        } catch (IOException e) {
            log.error("[NFS Error] 파일 물리 쓰기 공정 중 디스크 에러 터짐: {}", file.getAbsolutePath(), e);
            throw new IllegalStateException("샌드박스 물리 디스크에 파일을 기록하지 못했습니다.", e);
        }

        log.info("[DockerAgentService] 파일 생성 완료: {}", file.getAbsolutePath());
    }

    /**
     * [기능 2] 지정된 NFS 경로의 특정 파일 삭제 (리팩토링 및 파일 정리용)
     */
    public boolean deleteFile(String userId, String uuid, String relativePath) {
        File baseDirFile = new File(new File(baseStoragePath, userId), uuid);
        Path basePath = baseDirFile.toPath().toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(relativePath).toAbsolutePath().normalize();

        log.info("[Security-Check] 삭제 시도 대상 경로 검증 - Base: {}, Target: {}", basePath, targetPath);

        // 전역 예외 처리 클래스가 가로채서 403 처리를 할 수 있도록 정제된 정책 예외 출력
        if (!targetPath.startsWith(basePath)) {
            log.error("[Security-Check] 위험 감지! 샌드박스 탈출 시도가 차단되었습니다. 공격 유입 경로: {}", relativePath);
            throw new AccessDeniedException("허용되지 않은 디렉토리 접근입니다. 격리 구역 외의 자원은 조작할 수 없습니다.");
        }

        File file = targetPath.toFile();

        if (file.exists() && file.isFile()) {
            boolean deleted = file.delete();
            if (deleted) {
                log.info("[DockerAgentService] 격리 구역 내 파일 안전 삭제 완료: {}", file.getAbsolutePath());
                return true;
            } else {
                log.warn("[DockerAgentService] 파일 삭제 실패 (권한 부족 또는 파일 잠김): {}", file.getAbsolutePath());
                return false;
            }
        } else {
            log.warn("[DockerAgentService] 삭제 요청을 받았으나 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
            return false;
        }
    }

    /**
     * [기능 3] 켜져 있는 컨테이너 내부에서 연속적으로 명령어를 수행하는 메서드 (반복 호출 가능)
     */
    public AiModelMessage.Observation executeCommand(String containerId, String cmd) {
        AiModelMessage.Observation observation = new AiModelMessage.Observation();

        // try 괄호 안에 두 스트림을 선언하여 자동 리소스 관리(try-with-resources)를 발동시킵니다.
        // 이제 메서드가 정상 마감되든, 예외가 터지든 JVM이 파일 디스크립터 자원을 100% 안전하게 자동 회수합니다.
        try (ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
             ByteArrayOutputStream stderrStream = new ByteArrayOutputStream()) {

            log.info("[Sandbox] 명령어 실행 (컨테이너 상태 유지): {}", cmd);
            String[] shellCmd = {"sh", "-c", cmd};

            ExecCreateCmdResponse execCreateCmd = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(shellCmd)
                    .exec();

            ResultCallback.Adapter<Frame> resultCallback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    if (frame != null) {
                        try {
                            switch (frame.getStreamType()) {
                                case STDOUT, RAW -> stdoutStream.write(frame.getPayload());
                                case STDERR -> stderrStream.write(frame.getPayload());
                                default -> log.warn("알 수 없는 스트림 타입: {}", frame.getStreamType());
                            }
                        } catch (IOException e) {
                            log.error("스트림 파싱 중 오류 발생", e);
                        }
                    }
                    super.onNext(frame);
                }
            };

            dockerClient.execStartCmd(execCreateCmd.getId())
                    .exec(resultCallback)
                    .awaitCompletion();

            String stdout = stdoutStream.toString().trim();
            String stderr = stderrStream.toString().trim();

            observation.setStdout(stdout);
            observation.setStderr(stderr);

            if (!stderr.isEmpty() && (stderr.toLowerCase().contains("exception") || stderr.toLowerCase().contains("error"))) {
                observation.setStatus("ERROR");
                observation.setExitCode(1);
            } else {
                observation.setStatus("SUCCESS");
                observation.setExitCode(0);
            }

        } catch (Exception e) {
            log.error("[Sandbox] 명령어 실행 도중 치명적 에러 발생", e);
            observation.setStatus("ERROR");
            observation.setStderr("Sandbox 가상 인프라 내부 명령 수행 실패");
        }

        return observation;
    }


    /**
     * [기능 4] 지정된 NFS 격리 구역 내의 특정 파일 내용을 안전하게 읽어오는 메서드 (RAG 최적화 전용)
     */
    public String readFile(String userId, String uuid, String relativePath) {
        File baseDirFile = new File(new File(baseStoragePath, userId), uuid);
        Path basePath = baseDirFile.toPath().toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(relativePath).toAbsolutePath().normalize();

        log.info("[Security-Check] 파일 읽기 대상 경로 검증 - Base: {}, Target: {}", basePath, targetPath);

        // 403 예외 래핑
        if (!targetPath.startsWith(basePath)) {
            log.error("[Security-Check] 위반 감지! 샌드박스 바깥 영역에 대한 접근 차단. 공격 경로: {}", relativePath);
            throw new AccessDeniedException("허용되지 않은 파일 접근입니다. 격리 구역 외부의 자원은 조회할 수 없습니다.");
        }

        File file = targetPath.toFile();

        if (!file.exists()) {
            throw new IllegalArgumentException("요청한 소스 파일이 격리 구역 내에 존재하지 않습니다: " + relativePath);
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("요청한 경로가 올바른 파일 형식이 아닙니다: " + relativePath);
        }

        // 체크 예외 포장
        try {
            String content = java.nio.file.Files.readString(targetPath, java.nio.charset.StandardCharsets.UTF_8);
            log.info("[DockerAgentService] 격리 구역 내 파일 조회 및 텍스트 스트리밍 완료: {}", file.getAbsolutePath());
            return content;
        } catch (IOException e) {
            log.error("[NFS Error] 파일 시스템 판독 불능 장애 발생. Path: {}", targetPath, e);
            throw new IllegalStateException("서버 디스크 자원 통신 에러로 파일 내용을 판독하지 못했습니다.", e);
        }
    }

    /**
     * [기능 5] 공정 시작 시 컨테이너를 켜두는 메서드 (최초 1회만 호출)
     */
    public String startSandbox(String userId, String uuid, String baseImage) {
        try {
            File hostPhysicalDir = new File(new File(baseStorageRealPath, userId), uuid);
            String hostPhysicalPath = hostPhysicalDir.getAbsolutePath();

            Volume containerVolume = new Volume("/workspace");
            Bind bind = new Bind(hostPhysicalPath, containerVolume);

            log.info("[Sandbox] 샌드박스 컨테이너 기동 시작 (지속 실행 모드) - 이미지: {}", baseImage);

            CreateContainerResponse container = dockerClient.createContainerCmd(baseImage)
                    .withName("agent-sandbox-" + uuid)
                    .withWorkingDir("/workspace")
                    .withHostConfig(HostConfig.newHostConfig()
                            .withBinds(bind)
                            .withCpuQuota(100000L)
                            .withMemory(2147483648L))
                    .withTty(true)
                    .exec();

            String containerId = container.getId();
            dockerClient.startContainerCmd(containerId).exec();
            log.info("[Sandbox] 샌드박스 컨테이너 준비 완료. ID: {}", containerId);

            return containerId;

        } catch (Exception e) {
            // 도커 데몬 소켓 통신 단절 및 컨테이너 생성 폭발 에러 캡슐화 포장
            log.error("[Docker-Daemon API Error] 도커 컨테이너 가동 공정 중 심각한 합선 발생", e);
            throw new IllegalStateException("격리 기획 엔진(Docker)을 정상적으로 부트스트래핑하지 못했습니다. 관리자에게 문의하세요.", e);
        }
    }

    /**
     * [기능 6] 공정이 완전히 끝났을 때 샌드박스 파괴 (최종 1회 호출)
     */
    public void stopSandbox(String containerId) {
        try {
            log.info("[Sandbox] 공정 종료로 인한 컨테이너 폐기 요청. ID: {}", containerId);
            dockerClient.stopContainerCmd(containerId).exec();
            dockerClient.removeContainerCmd(containerId).exec();
            log.info("[Sandbox] 컨테이너 자원 반환 완료.");
        } catch (Exception e) {
            log.error("[Sandbox] 컨테이너 사후 정리 중 에러 발생", e);
            throw new IllegalStateException("가상화 격리 풀 컨테이너 가드 자원 해제(CleanUp) 공정이 정상 실패했습니다.", e);
        }
    }
}
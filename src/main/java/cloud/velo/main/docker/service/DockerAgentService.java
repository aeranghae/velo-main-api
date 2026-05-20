package cloud.velo.main.docker.service;

import cloud.velo.main.docker.dto.AiModelMessage;
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
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    public void writeFile(String userId, String uuid, String relativePath, String content) throws IOException {

        // 1. 자바 내장 File 생성자를 활용하여 문자열 더하기 오타(userdir1)를 원천 차단
        // 결과 경로: {baseStoragePath}/{userId}/{uuid}
        File baseDirFile = new File(new File(baseStoragePath, userId), uuid);

        // 2. 프로젝트 루트 샌드박스 폴더 자동 생성
        if (!baseDirFile.exists()) {
            baseDirFile.mkdirs();
        }

        // 3. 상대 경로에 따른 최종 파일 객체 매핑 (예: {baseDir}/test/hello.txt)
        File file = new File(baseDirFile, relativePath);

        // 4. 하위 폴더 구조가 깊을 경우를 대비한 부모 디렉토리 자동 생성 (예: src/components)
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 5. 파일 쓰기 수행 (Try-with-resources 구조로 스트림 자동 close)
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }

        log.info("[DockerAgentService] 파일 생성 완료: {}", file.getAbsolutePath());
    }

    /**
     * [기능 2] 지정된 NFS 경로의 특정 파일 삭제 (리팩토링 및 파일 정리용)
     */
    public boolean deleteFile(String userId, String uuid, String relativePath) {
        // 1. 자바 내장 File 생성자를 활용하여 문자열 더하기 오타(userdir1)를 원천 차단
        File baseDirFile = new File(new File(baseStoragePath, userId), uuid);
        Path basePath = baseDirFile.toPath().toAbsolutePath().normalize();

        // 2. 삭제 요청된 파일의 절대 경로 계산 및 정형화
        Path targetPath = basePath.resolve(relativePath).toAbsolutePath().normalize();

        log.info("[Security-Check] 삭제 시도 대상 경로 검증 - Base: {}, Target: {}", basePath, targetPath);

        // 3. 디렉토리 트래버설(샌드박스 탈출) 공격 검증 가드
        if (!targetPath.startsWith(basePath)) {
            log.error("[Security-Check] 위험 감지! 샌드박스 탈출 시도가 차단되었습니다. 공격 유입 경로: {}", relativePath);
            throw new SecurityException("허용되지 않은 디렉토리 접근입니다. 격리 구역 외의 파일은 삭제할 수 없습니다.");
        }

        // 4. 검증을 통과한 안전한 파일 객체 생성 후 삭제 처리
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
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();

        try {
            log.info("[Sandbox] 명령어 실행 (컨테이너 상태 유지): {}", cmd);

            // 독자 프로세스로 켜면 스트림이 누락될 수 있으므로
            // 리눅스 쉘 환경(sh -c)을 명시적으로 매핑하여 명령어를 래핑합니다.
            String[] shellCmd = {"sh", "-c", cmd};

            ExecCreateCmdResponse execCreateCmd = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(shellCmd) // ⭐️ 교정된 쉘 명령어 배열 주입
                    .exec();

            ResultCallback.Adapter<Frame> resultCallback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    if (frame != null) {
                        try {
                            switch (frame.getStreamType()) {
                                case STDOUT:
                                case RAW:
                                    stdoutStream.write(frame.getPayload());
                                    break;
                                case STDERR:
                                    stderrStream.write(frame.getPayload());
                                    break;
                                default:
                                    log.warn("알 수 없는 스트림 타입: {}", frame.getStreamType());
                            }
                        } catch (IOException e) {
                            log.error("스트림 파싱 중 오류 발생", e);
                        }
                    }
                    super.onNext(frame);
                }
            };

            // 명령어 실행 후 대기
            dockerClient.execStartCmd(execCreateCmd.getId())
                    .exec(resultCallback)
                    .awaitCompletion();

            String stdout = stdoutStream.toString().trim();
            String stderr = stderrStream.toString().trim();

            // 수집된 두 가닥의 스트림 원본을 모두 전송 객체에 바인딩
            observation.setStdout(stdout);
            observation.setStderr(stderr);

            // java -version 대응 및 런타임 예외 필터링
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
            observation.setStderr(e.getMessage());
        } finally {
            try {
                stdoutStream.close();
                stderrStream.close();
            } catch (IOException ignored) {}
        }

        return observation;
    }

    /**
     * [기능 4] 공정 시작 시 컨테이너를 켜두는 메서드 (최초 1회만 호출)
     * @return 생성된 도커 컨테이너 고유 ID
     */
    public String startSandbox(String userId, String uuid, String baseImage) {
        // 1. 자바 내장 File 생성자를 활용하여 문자열 더하기 오타(userdir1)를 원천 차단
        // 외부 도커 데몬이 마운트해야 하므로 물리 절대 경로인 'baseStorageRealPath'(realpath)를 사용합니다.
        File hostPhysicalDir = new File(new File(baseStorageRealPath, userId), uuid);
        String hostPhysicalPath = hostPhysicalDir.getAbsolutePath();

        // 2. 디버깅 및 추적용 파드 내부 NFS 마운트 경로 (선택 사항 - 로그 출력용)
        // File podLocalDir = new File(new File(baseStoragePath, userId), uuid);
        // log.info("[Sandbox] 파드 내부 실제 작업 관측 경로: {}", podLocalDir.getAbsolutePath());

        Volume containerVolume = new Volume("/workspace");
        Bind bind = new Bind(hostPhysicalPath, containerVolume);

        log.info("[Sandbox] 샌드박스 컨테이너 기동 시작 (지속 실행 모드) - 이미지: {}", baseImage);
        log.info("[Sandbox] 미니 PC 호스트 물리 매핑 경로 (도커 마운트용): {}", hostPhysicalPath);

        // 3. 컨테이너 생성 및 자원 격리 제한선(자드가 가동) 설정
        CreateContainerResponse container = dockerClient.createContainerCmd(baseImage)
                .withName("agent-sandbox-" + uuid)
                .withHostConfig(HostConfig.newHostConfig()
                        .withBinds(bind)
                        .withCpuQuota(100000L)   // 1코어 제한
                        .withMemory(536870912L)) // 512MB 제한
                .withTty(true) // 컨테이너가 즉시 종료되지 않고 지속적으로 명령을 대기하도록 설정
                .exec();

        String containerId = container.getId();
        dockerClient.startContainerCmd(containerId).exec();
        log.info("[Sandbox] 샌드박스 컨테이너 준비 완료. ID: {}", containerId);

        return containerId;
    }


    /**
     * [기능 5] 공정이 완전히 끝났을 때 샌드박스 파괴 (최종 1회 호출)
     */
    public void stopSandbox(String containerId) {
        try {
            log.info("[Sandbox] 공정 종료로 인한 컨테이너 폐기 요청. ID: {}", containerId);
            dockerClient.stopContainerCmd(containerId).exec();
            dockerClient.removeContainerCmd(containerId).exec();
            log.info("[Sandbox] 컨테이너 자원 반환 완료.");
        } catch (Exception e) {
            log.error("[Sandbox] 컨테이너 사후 정리 중 에러 발생", e);
        }
    }

}
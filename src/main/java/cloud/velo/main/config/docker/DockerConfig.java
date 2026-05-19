package cloud.velo.main.config.docker;


import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class DockerConfig {

    @Bean
    public DockerClient dockerClient() {
        // 1. 로컬 호스트의 도커 소켓 경로를 기반으로 기본 설정 생성
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();

        // 2. 도커와 통신할 HTTP 클라이언트 정의 (자바 21과 스프링 4.0 환경에 맞춰 타임아웃 세팅)
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        // 3. 설정과 클라이언트를 결합하여 스프링 컨테이너에 빈(Bean)으로 등록
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
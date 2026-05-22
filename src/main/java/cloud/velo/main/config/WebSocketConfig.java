package cloud.velo.main.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig {

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        // 1. 웹소켓 세션 유지 타임아웃을 5분(300,000ms)으로 대폭 상향
        // 그래들 빌드가 돌아가는 동안 세션이 끊기지 않도록 붙잡아줍니다.
        container.setMaxSessionIdleTimeout(300000L);

        container.setAsyncSendTimeout(300000L);

        // 2. 대용량 텍스트 메시지 버퍼 확장 (10MB)
        // 그래들 빌드 시 쏟아져 나오는 방대한 stdout 로그들을 잘림 없이 수신합니다.
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);

        return container;
    }
}
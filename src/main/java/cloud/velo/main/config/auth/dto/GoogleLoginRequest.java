package cloud.velo.main.config.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoogleLoginRequest {
    // 리액트에서 보낼 토큰(credential)을 담을 변수
    private String credential;
}
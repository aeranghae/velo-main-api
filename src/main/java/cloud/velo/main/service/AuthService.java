package cloud.velo.main.service;

import cloud.velo.main.domain.Role;
import cloud.velo.main.domain.User;
import cloud.velo.main.dto.response.LoginResponse;
import cloud.velo.main.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.security.GeneralSecurityException;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final GoogleAuthService googleAuthService;
    private final StorageService storageService;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional // 전체 흐름을 하나의 트랜잭션 울타리로 묶어 안전성을 확보합니다.
    public LoginResponse loginWithGoogle(String credential) throws GeneralSecurityException, IOException {

        // 1. 구글 인증 및 유저 영속화
        User user = googleAuthService.verifyTokenAndLogin(credential);

        // 2. 디렉토리 생성 인프라 로직 수행
        if (user.getRole() == Role.USER) {
            storageService.createUserDirectory(String.valueOf(user.getId()));
        }

        // 3. JWT 토큰 생성
        String accessToken = jwtTokenProvider.createToken(user.getEmail(), user.getRole().name());

        // 4. 비즈니스 결과물을 응답 스펙(DTO)으로 변환하여 반환
        return LoginResponse.builder()
                .accessToken(accessToken)
                .name(user.getName())
                .email(user.getEmail())
                .model(user.getModel().getModelName())
                .picture(user.getPicture())
                .role(user.getRole())
                .isNewUser(user.getRole() == Role.GUEST)
                .build();
    }
}
package cloud.velo.main.service;

import cloud.velo.main.domain.Role;
import cloud.velo.main.domain.User;
import cloud.velo.main.dto.response.LoginResponse;
import cloud.velo.main.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Slf4j
@RequiredArgsConstructor
@Service
public class AuthService {

    private final GoogleAuthService googleAuthService;
    private final StorageService storageService;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional // 이제 언체크 예외로 전파되므로, 하위 과정 어디서든 에러가 터지면 100% 전체 자동 롤백됩니다.
    public LoginResponse loginWithGoogle(String credential) {
        try {
            // 1. 구글 인증 및 유저 영속화
            User user = googleAuthService.verifyTokenAndLogin(credential);

            // 2. 디렉토리 생성 인프라 로직 수행
            if (user.getRole() == Role.USER) {
                storageService.createUserDirectory(String.valueOf(user.getId()));
            }

            // 3. JWT 토큰 생성
            String accessToken = jwtTokenProvider.createToken(user.getEmail(), user.getRole().name());

            // 4. AI 모델이 null일 경우를 대비한 가 안전 방어벽 세우기
            String assignedModelName = (user.getModel() != null) ? user.getModel().getModelName() : "NONE";

            // 5. 비즈니스 결과물을 응답 DTO로 변환하여 반환
            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .name(user.getName())
                    .email(user.getEmail())
                    .model(assignedModelName)
                    .picture(user.getPicture())
                    .role(user.getRole())
                    .isNewUser(user.getRole() == Role.GUEST)
                    .build();

        } catch (BadCredentialsException e) {
            // 구글 토큰 검증 실패는 이미 언체크(런타임)이므로 그대로 위로 토스!
            throw e;
        } catch (GeneralSecurityException | IOException e) {
            // [핵심] 외부 라이브러리/I/O 체크 예외가 터지면, 스프링 프록시가 트랜잭션을 롤백할 수 있도록
            // 런타임 예외(IllegalStateException)로 포장해서 위로 분수처럼 뿜어 올려버립니다!
            log.error("[AuthService] 구글 로그인 공정 중 물리적 시스템 예외 발생: {}", e.getMessage(), e);
            throw new IllegalStateException("로그인 처리 중 서버 인프라 에러가 발생했습니다. 잠시 후 다시 시도해주세요.", e);
        }
    }
}
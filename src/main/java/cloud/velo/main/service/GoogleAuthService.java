package cloud.velo.main.service;

import cloud.velo.main.domain.AiModel;
import cloud.velo.main.domain.Role;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.AiModelRepository;
import cloud.velo.main.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@RequiredArgsConstructor
@Slf4j
@Service
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final AiModelRepository aiModelRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Transactional
    public User verifyTokenAndLogin(String credential) throws GeneralSecurityException, IOException {

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(credential);

        // 2. 구글 토큰이 유효하지 않다면 컨트롤러 어드바이스가 가로챌 수 있도록 BadCredentialsException을 던집니다.
        // 이 예외는 런타임 예외이므로 터지는 즉시 프록시 객체가 트랜잭션을 알아서 안전하게 Rollback 시킵니다.
        if (idToken == null) {
            throw new BadCredentialsException("구글 로그인 토큰 인증에 실패했습니다.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        AiModel defaultModel = aiModelRepository.findByDefaultActiveTrue()
                .or(() -> aiModelRepository.findAllByIsActiveTrue().stream().findFirst())
                .orElse(null);

        if (defaultModel == null) {
            log.warn("현재 DB에 활성화된 AI 모델이 하나도 없습니다. 신규 유저에게 모델이 배정되지 않습니다.");
        }

        User user = userRepository.findByEmail(email)
                .map(entity -> entity.update(name, pictureUrl))
                .orElse(User.builder()
                        .email(email)
                        .name(name)
                        .picture(pictureUrl)
                        .model(defaultModel)
                        .role(Role.USER)
                        .build());

        User saved = userRepository.save(user);

        if (saved.getModel() != null) {
            saved.getModel().getModelName(); // @Transactional 울타리 덕분에 Lazy Loading 프록시가 안전하게 초기화됨!
        }
        return saved;
    }
}
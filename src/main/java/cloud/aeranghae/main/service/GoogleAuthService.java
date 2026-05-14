package cloud.aeranghae.main.service;

import cloud.aeranghae.main.domain.AiModel;
import cloud.aeranghae.main.domain.Role;
import cloud.aeranghae.main.domain.User;
import cloud.aeranghae.main.repository.AiModelRepository;
import cloud.aeranghae.main.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@RequiredArgsConstructor
@Service
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final AiModelRepository aiModelRepository;

    // application.yml에 있는 구글 클라이언트 ID를 가져옵니다.
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Transactional
    public User verifyTokenAndLogin(String credential) throws Exception {
        // 1. 구글 토큰 검증기 생성
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        // 2. 토큰이 진짜인지 구글 서버를 통해 확인
        GoogleIdToken idToken = verifier.verify(credential);

        if (idToken == null) {
            throw new IllegalArgumentException("유효하지 않은 구글 토큰입니다.");
        }

        // 3. 진짜라면 토큰 안에서 사용자 정보(페이로드) 꺼내기
        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        AiModel defaultModel = aiModelRepository.findByDefaultActiveTrue()
                .orElseThrow(() -> new IllegalStateException("기본 AI 모델이 설정되어 있지 않습니다."));

        // 4. 우리 DB에 있는 회원인지 확인 후, 없으면 자동 회원가입(Save), 있으면 정보 업데이트
        User user = userRepository.findByEmail(email)
                .map(entity -> entity.update(name, pictureUrl)) // 기존 회원이면 이름/프사 업데이트
                .orElse(User.builder()
                        .email(email)
                        .name(name)
                        .picture(pictureUrl)
                        .model(defaultModel)
                        .role(Role.USER) // 혹은 기본 권한 설정
                        .build()); // 신규 회원이면 객체 생성

        return userRepository.save(user); // DB에 저장
    }
}
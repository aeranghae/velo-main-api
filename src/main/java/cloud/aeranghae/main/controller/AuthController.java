package cloud.aeranghae.main.controller;

import cloud.aeranghae.main.config.auth.JwtTokenProvider; // 추가됨!
import cloud.aeranghae.main.domain.Role;
import cloud.aeranghae.main.domain.User;
import cloud.aeranghae.main.config.auth.dto.GoogleLoginRequest;
import cloud.aeranghae.main.service.GoogleAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final GoogleAuthService googleAuthService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request) {
        try {
            User user = googleAuthService.verifyTokenAndLogin(request.getCredential());
            String accessToken = jwtTokenProvider.createToken(user.getEmail(), user.getRole().name());

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("name", user.getName()); // 이게 닉네임임 작성하면 덮어쓰기됨
            response.put("email", user.getEmail());
            response.put("picture", user.getPicture());
            response.put("role", user.getRole());

            // Role이 GUEST면 아직 가입 절차(닉네임 설정)가 안 끝난 신규 유저입니다.
            boolean isNewUser = (user.getRole() == Role.GUEST);
            response.put("isNewUser", isNewUser);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body("로그인 실패: " + e.getMessage());
        }
    }
}
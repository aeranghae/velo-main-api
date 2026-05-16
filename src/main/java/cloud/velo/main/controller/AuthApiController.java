package cloud.velo.main.controller;

import cloud.velo.main.config.auth.JwtTokenProvider; // 추가됨!
import cloud.velo.main.domain.Role;
import cloud.velo.main.domain.User;
import cloud.velo.main.config.auth.dto.GoogleLoginRequest;
import cloud.velo.main.service.GoogleAuthService;
import cloud.velo.main.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AuthApiController {

    private final GoogleAuthService googleAuthService;
    private final JwtTokenProvider jwtTokenProvider;
    private final StorageService storageService;

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request) {
        try {
            User user = googleAuthService.verifyTokenAndLogin(request.getCredential());

            // 로그인할 때마다 디렉토리 존재 여부 체크 및 생성
            // 이미 폴더가 있으면 StorageService 내부에서 무시하도록 설계되어 있음
            // [핵심] 정식 유저인 경우에만 폴더 존재 여부 확인 및 자동 복구
            if (user.getRole() == Role.USER) {
                storageService.createUserDirectory(String.valueOf(user.getId()));
            }

            String accessToken = jwtTokenProvider.createToken(user.getEmail(), user.getRole().name());

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("name", user.getName()); // 이게 닉네임임 작성하면 덮어쓰기됨
            response.put("email", user.getEmail());
            response.put("model", user.getModel().getModelName());
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
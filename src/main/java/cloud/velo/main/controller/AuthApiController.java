package cloud.velo.main.controller;

import cloud.velo.main.dto.request.GoogleLoginRequest;
import cloud.velo.main.dto.response.LoginResponse;
import cloud.velo.main.service.AuthService; // 💡 단 하나의 통합 서비스만 의존
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost"})
public class AuthApiController {

    private final AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(@RequestBody GoogleLoginRequest request)
            throws GeneralSecurityException, IOException {

        LoginResponse response = authService.loginWithGoogle(request.getCredential());

        return ResponseEntity.ok(response);
    }
}
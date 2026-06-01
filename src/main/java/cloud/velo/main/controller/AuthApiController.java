package cloud.velo.main.controller;

import cloud.velo.main.dto.request.GoogleLoginRequest;
import cloud.velo.main.dto.response.LoginResponse;
import cloud.velo.main.service.AuthService;
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
    public ResponseEntity<LoginResponse> googleLogin(@RequestBody GoogleLoginRequest request) {

        LoginResponse response = authService.loginWithGoogle(request.getCredential());

        return ResponseEntity.ok(response);
    }
}
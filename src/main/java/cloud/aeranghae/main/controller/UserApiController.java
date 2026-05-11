package cloud.aeranghae.main.controller;

import cloud.aeranghae.main.controller.dto.SignupRequestDto;
import cloud.aeranghae.main.controller.dto.UserResponseDto;
import cloud.aeranghae.main.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class UserApiController {

    private final UserService userService;

    @PostMapping("/api/user/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequestDto requestDto,
                                         @AuthenticationPrincipal String email) { // JWT 필터에서 등록한 email

        // 1. 인증 체크
        if (email == null) {
            return ResponseEntity.status(401).body("인증 정보가 없습니다.");
        }

        // 2. 닉네임 유효성 검사 (공백 방지)
        if (requestDto.getNickname() == null || requestDto.getNickname().isBlank()) {
            return ResponseEntity.badRequest().body("유효하지 않은 닉네임입니다.");
        }

        try {
            // 3. 서비스 로직 수행 (닉네임 저장 + ROLE.USER 승급)
            userService.signup(email, requestDto.getNickname());
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("회원가입 처리 중 오류 발생: " + e.getMessage());
        }
    }

    @GetMapping("/api/user/me")
    public ResponseEntity<?> getMyInfo(@AuthenticationPrincipal String email) {
        if (email == null) {
            return ResponseEntity.status(401).body("인증되지 않은 사용자입니다.");
        }

        try {
            // 이메일로 유저 정보를 조회하여 DTO로 반환
            UserResponseDto userInfo = userService.getUserInfoByEmail(email);
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("사용자 정보를 찾을 수 없습니다.");
        }
    }

}
package cloud.aeranghae.main.controller;

import cloud.aeranghae.main.controller.dto.SignupRequestDto;
import cloud.aeranghae.main.controller.dto.UserResponseDto;
import cloud.aeranghae.main.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@Slf4j
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
        log.info("내 정보 조회 요청 시작 - email: {}", email); // 로그 남기기

        try {
            UserResponseDto userInfo = userService.getUserInfoByEmail(email);
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            // 에러 발생 시 원인을 정확히 찍어줍니다.
            log.error("내 정보 조회 중 에러 발생! 원인: {}", e.getMessage(), e);
            return ResponseEntity.status(404).body("사용자 정보를 찾을 수 없습니다.");
        }
    }

    @PatchMapping("/api/user/nickname")
    public ResponseEntity<String> updateNickname(@RequestBody SignupRequestDto requestDto,
                                                 @AuthenticationPrincipal String email) {
        if (email == null) {
            return ResponseEntity.status(401).body("인증 정보가 없습니다.");
        }

        if (requestDto.getNickname() == null || requestDto.getNickname().isBlank()) {
            return ResponseEntity.badRequest().body("유효하지 않은 닉네임입니다.");
        }

        try {
            // 이미 가입된 유저의 name(닉네임)만 변경하는 서비스 호출
            userService.updateNickname(email, requestDto.getNickname());
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("닉네임 변경 실패: " + e.getMessage());
        }
    }

}
package cloud.velo.main.controller;

import cloud.velo.main.dto.request.SignupRequest;
import cloud.velo.main.dto.response.UserResponse;
import cloud.velo.main.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserApiController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest requestDto,
                                         @AuthenticationPrincipal String email) {

        userService.signup(email, requestDto.getNickname());
        return ResponseEntity.ok("success");
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyInfo(@AuthenticationPrincipal String email) {

        UserResponse userInfo = userService.getUserInfoByEmail(email);
        return ResponseEntity.ok(userInfo);
    }

    @PatchMapping("/nickname")
    public ResponseEntity<String> updateNickname(@RequestBody SignupRequest requestDto,
                                                 @AuthenticationPrincipal String email) {

        userService.updateNickname(email, requestDto.getNickname());
        return ResponseEntity.ok("success");
    }

}
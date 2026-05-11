package cloud.aeranghae.main.controller;

import cloud.aeranghae.main.domain.User;
import cloud.aeranghae.main.repository.UserRepository;
import cloud.aeranghae.main.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final UserRepository userRepository;

    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> getUsage(@AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        long usageBytes = storageService.getUserStorageUsage(String.valueOf(user.getId()));

        Map<String, Object> response = new HashMap<>();
        response.put("usageBytes", usageBytes);
        response.put("usageMB", String.format("%.2f", (double) usageBytes / (1024 * 1024)));

        return ResponseEntity.ok(response);
    }
}
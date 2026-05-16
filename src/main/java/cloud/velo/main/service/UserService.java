package cloud.velo.main.service;

import cloud.velo.main.controller.dto.UserResponseDto;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final StorageService storageService;

    @Transactional
    @CacheEvict(value = "userCache", key = "#email") // 혹시 남아있을지 모를 가입 전 캐시 삭제
    public void signup(String email, String nickname) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 엔티티의 승급 메서드 호출 (JPA 더티 체킹으로 자동 DB 업데이트)
        user.authorizeUser(nickname);

        // 2. 유저 전용 디렉토리 생성 (유저 ID를 폴더명으로 사용 권장)
        storageService.createUserDirectory(String.valueOf(user.getId()));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "userCache", key = "#email", cacheManager = "cacheManager")
    public UserResponseDto getUserInfoByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자가 없습니다. email=" + email));

        // 유저 엔티티를 응답 DTO로 변환하여 반환
        return new UserResponseDto(
                user.getName(),
                user.getEmail(),
                user.getPicture(),
                user.getModel().getModelName(), // api키 유출 방지를 위한 이름만 전송
                user.getRole().name()
        );
    }

    @Transactional
    @CacheEvict(value = "userCache", key = "#email") // 닉네임 변경 시 해당 유저 캐시 삭제
    public void updateNickname(String email, String newNickname) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // JPA Dirty Checking에 의해 name이 변경되고 트랜잭션 종료 시 DB에 반영됨
        user.updateNickname(newNickname);
    }
}
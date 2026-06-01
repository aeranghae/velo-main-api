package cloud.velo.main.service;

import cloud.velo.main.dto.response.UserResponse;
import cloud.velo.main.domain.User;
import cloud.velo.main.exception.UserNotFoundException; // 💡 내가 만든 저격 예외 임포트!
import cloud.velo.main.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;

    @Transactional
    @CacheEvict(value = "userCache", key = "#email") // 혹시 남아있을지 모를 가입 전 캐시 삭제
    public void signup(String email, String nickname) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("회원가입을 진행할 임시 사용자 정보를 찾을 수 없습니다. email: " + email));

        // 엔티티의 승급 메서드 호출 (JPA 더티 체킹으로 자동 DB 업데이트)
        user.authorizeUser(nickname);

        // 유저 전용 디렉토리 생성 (물리 디렉토리 생성 중 IOException이 터지면 언체크로 전파되어 트랜잭션 자동 롤백!)
        storageService.createUserDirectory(String.valueOf(user.getId()));

        log.info("[UserService] 회원가입 승급 및 디렉토리 생성 완료. User ID: {}", user.getId());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "userCache", key = "#email", cacheManager = "cacheManager")
    public UserResponse getUserInfoByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("해당 사용자의 정보가 존재하지 않습니다. email: " + email));

        // 유저 엔티티를 응답 DTO로 변환하여 반환
        return new UserResponse(
                user.getName(),
                user.getEmail(),
                user.getPicture(),
                user.getModel().getModelName(), // 영속성 컨텍스트 덕분에 Lazy Loading 안전 초기화
                user.getRole().name()
        );
    }

    @Transactional
    @CacheEvict(value = "userCache", key = "#email") // 닉네임 변경 시 해당 유저 캐시 삭제
    public void updateNickname(String email, String newNickname) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("닉네임을 변경할 사용자 정보를 찾을 수 없습니다. email: " + email));

        // JPA Dirty Checking에 의해 name이 변경되고 트랜잭션 종료 시 DB에 반영됨
        user.updateNickname(newNickname);

        log.info("[UserService] 사용자 닉네임 변경 완료. email: {}, newNickname: {}", email, newNickname);
    }
}
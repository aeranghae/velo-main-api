package cloud.velo.main.exception;

/**
 * [404 Not Found] 유저 도메인에서 사용자를 찾을 수 없을 때 커스텀 예외
 */
public class UserNotFoundException extends RuntimeException {

    // 1. 기본 생성자 (메시지 없이 에러만 터트릴 때)
    public UserNotFoundException() {
        super("사용자 정보를 찾을 수 없습니다.");
    }

    // 2. 실무 핵심 생성자 (상세한 에러 메시지를 동적으로 담아 던질 때)
    public UserNotFoundException(String message) {
        super(message); // 부모인 RuntimeException의 심장부로 메시지를 토스합니다.
    }

    // 3. 원인 예외(Cause)까지 함께 묶어서 던질 때 (선택사항, 보통 외부 라이브러리 에러 감싸기용)
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
package cloud.velo.main.exception;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@Slf4j
// 모든 컨트롤러에서 발생하는 예외를 이 상자가 전부 가로챕니다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // IllegalArgumentException은 다른 도메인(예: 잘못된 파라미터 유입 등)을 위한 400 Bad Request나 500용 범용 방어벽으로 양보합니다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("❌ 잘못된 인자 유입 경고: {}", e.getMessage());
        return ResponseEntity.badRequest().body("잘못된 요청 양식입니다: " + e.getMessage());
    }

    // [404 Not Found] 유저 도메인에서 사용자를 찾을 수 없을 때 (최우선 순위 저격)
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFoundException(UserNotFoundException e) {
        log.warn("👤 [유저 도메인 예외 감지] : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // 2. [400 Bad Request] @Valid 유효성 검사(@NotBlank 등)에서 컷트당했을 때
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException e) {
        // 에러 메시지 중 개발자가 DTO에 적어둔 첫 번째 message 내용을 추출합니다.
        String errorMessage = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();
        log.warn("유효성 검사 실패: {}", errorMessage);
        return ResponseEntity.badRequest().body(errorMessage);
    }

    // 3. [500 Internal Server Error] 예측하지 못한 서버 내부의 치명적인 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAllException(Exception e) {
        log.error("서버 심각한 오류 발생! 원인: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("서버 내부 처리 중 오류가 발생했습니다. 관리자에게 문의하세요.");
    }

    // 4. [401 Unauthorized] 구글 토큰 인증 실패나 잘못된 자격 증명일 때
    @ExceptionHandler(BadCredentialsException.class) // 혹은 프로젝트에서 구글 인증 실패 시 던지는 예외 클래스 지정
    public ResponseEntity<String> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("인증 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인 실패: " + e.getMessage());
    }


    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException e) {
        // 서버 전체에서 발생하는 모든 상태 오류(SSE 포함)를 여기서 일괄 로깅 및 모니터링 처리!
        log.error("[시스템 상태 오류 감지] : {}", e.getMessage(), e);

        // 필요 시 여기에 슬랙 알림 발송 로직 추가 가능!

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }

    // [429 Too Many Requests] 디스크나 API 요청 제한 트래픽 초과 시
    @ExceptionHandler(OverRateLimitException.class) // 💡 커스텀 예외 정의 후 사용
    public ResponseEntity<String> handleOverRateLimitException(OverRateLimitException e) {
        log.warn("⚠트래픽 제한 초과 발생: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
    }

    // [403 Forbidden] 유저는 맞지만 해당 리소스나 기능의 권한이 없을 때
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("권한 거부 접근 발생: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}
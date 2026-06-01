package cloud.velo.main.exception;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
// 모든 컨트롤러에서 발생하는 예외를 이 상자가 전부 가로챕니다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. [404 Not Found] 사용자를 찾을 수 없거나 데이터가 없을 때
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("비즈니스 로직 경고: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // 2. [400 Bad Request] @Valid 유효성 검사(@NotBlank 등)에서 컷트당했을 때
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException e) {
        // 에러 메시지 중 개발자가 DTO에 적어둔 첫 번째 message 내용을 추출합니다.
        String errorMessage = e.getBindingResult().getFieldError().getDefaultMessage();
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
}
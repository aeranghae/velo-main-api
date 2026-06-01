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
@RestControllerAdvice
public class GlobalExceptionHandler {

    // [400 Bad Request] 잘못된 파라미터 유입 (범용 방어벽)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("[400 Bad Request] 잘못된 인자 유입: {}", e.getMessage());
        return ResponseEntity.badRequest().body("요청 파라미터가 올바르지 않습니다. 입력 값을 다시 확인해주세요.");
    }

    // [404 Not Found] 사용자를 찾을 수 없을 때 (내가 직접 던진 안전한 예외)
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFoundException(UserNotFoundException e) {
        log.warn("[유저 도메인 예외 감지] : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // [400 Bad Request] @Valid 유효성 검사 실패 시
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();
        log.warn("유효성 검사 실패: {}", errorMessage);

        // DTO에 개발자가 직접 적은 @NotBlank(message="...") 내용이므로 그대로 토스
        return ResponseEntity.badRequest().body(errorMessage);
    }

    // [401 Unauthorized] 구글 토큰 인증 실패나 잘못된 자격 증명일 때
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("인증 실패: {}", e.getMessage());
        // "구글 로그인 토큰 인증에 실패했습니다." 수준의 비즈니스 텍스트임
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    // [403 Forbidden] 권한이 없는 기능에 접근했을 때
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("권한 거부 접근 발생: {}", e.getMessage());

        // 서비스에서 직접 적은 권한 제한 메시지
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    // [429 Too Many Requests] 처리율 제한 트래픽 초과 시
    @ExceptionHandler(OverRateLimitException.class)
    public ResponseEntity<String> handleOverRateLimitException(OverRateLimitException e) {
        log.warn("트래픽 제한 초과 발생: {}", e.getMessage());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
    }

    // [500 Internal Server Error] 인프라 합선 및 시스템 상태 오류 (SSE, I/O 등)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException e) {
        log.error("[시스템 상태 오류 감지] : {}", e.getMessage(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("서버 실시간 연동 중 일시적인 장애가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    }

    // [500 Internal Server Error] 예측하지 못한 최상위 치명적 서버 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAllException(Exception e) {
        log.error("서버 심각한 오류 발생! 원인: {}", e.getMessage(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("서버 내부 처리 중 에러가 발생했습니다. 지속될 경우 관리자에게 문의하세요.");
    }

    // [404 Not Found] 저장소 도메인에서 프로젝트 데이터를 찾을 수 없을 때
    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<String> handleProjectNotFoundException(ProjectNotFoundException e) {
        log.warn("[저장소 도메인 예외 감지] : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // [400 Bad Request] 생성 가능한 최대 프로젝트 개수를 초과했을 때 격발
    @ExceptionHandler(MaxProjectLimitException.class)
    public ResponseEntity<String> handleMaxProjectLimitException(MaxProjectLimitException e) {
        log.warn("⚠️ 프로젝트 생성 제한 도달: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
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

    // [404 Not Found] 사용자를 찾을 수 없을 때
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
        return ResponseEntity.badRequest().body(errorMessage);
    }

    // [401 Unauthorized] 구글 토큰 인증 실패나 잘못된 자격 증명일 때
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("인증 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    // [403 Forbidden] 권한이 없는 기능에 접근했을 때
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("권한 거부 접근 발생: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    // [429 Too Many Requests] 처리율 제한 트래픽 초과 시
    @ExceptionHandler(OverRateLimitException.class)
    public ResponseEntity<String> handleOverRateLimitException(OverRateLimitException e) {
        log.warn("트래픽 제한 초과 발생: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
    }

    // ==========================================
    // 샌드박스 (Docker/NFS) 관련 예외 통합 처리부
    // ==========================================

    // [403 Forbidden] 샌드박스 바깥 경로 접근 등 보안 정책 위반 시
    @ExceptionHandler(SandboxSecurityException.class)
    public ResponseEntity<String> handleSandboxSecurityException(SandboxSecurityException e) {
        log.error("[Sandbox-Security-Violation] 보안 감지 정책 위반: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    // [404 Not Found] 격리 구역 내 파일이 실존하지 않을 때
    @ExceptionHandler(SandboxFileNotFoundException.class)
    public ResponseEntity<String> handleSandboxFileNotFoundException(SandboxFileNotFoundException e) {
        log.warn("[Sandbox-Not-Found] 요청 파일 없음: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // [500 Internal Server Error] 샌드박스 내부 디스크 물리 쓰기/읽기 실패 시
    @ExceptionHandler(SandboxStorageException.class)
    public ResponseEntity<String> handleSandboxStorageException(SandboxStorageException e) {
        log.error("[Sandbox-Storage-Error] 파일 시스템 입출력 실패: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("샌드박스 스토리지 연동 중 일시적인 장애가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    }

    // [500 Internal Server Error] 도커 컨테이너 기동 및 파괴 라이프사이클 에러 시
    @ExceptionHandler(SandboxLifecycleException.class)
    public ResponseEntity<String> handleSandboxLifecycleException(SandboxLifecycleException e) {
        log.error("[Sandbox-Lifecycle-Error] 도커 가상화 환경 제어 실패: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("격리 샌드박스 가상 인프라 제어 중 에러가 발생했습니다. 지속될 경우 관리자에게 문의하세요.");
    }

    // ==========================================

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
        log.warn("프로젝트 생성 제한 도달: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    // [400 Bad Request] 프로젝트가 현재 AI 생성 공정 중(웹소켓 활성 상태)이라 명령을 수행할 수 없을 때
    @ExceptionHandler(ProjectActiveSessionException.class)
    public ResponseEntity<String> handleProjectActiveSessionException(ProjectActiveSessionException e) {
        log.warn("[세션 제어 정책 위반] 활성 프로젝트 조작 거부: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    // SSE 실시간 스트리밍 중 끊김 현상 으로 인한 예외
    @org.springframework.web.bind.annotation.ExceptionHandler({
            org.apache.catalina.connector.ClientAbortException.class,
            org.springframework.web.context.request.async.AsyncRequestNotUsableException.class
    })
    public void handleBrokenPipe(Exception e) {
        log.info("[Network-Notice] 스트리밍 전송 중 클라이언트가 연결을 해제했습니다. (Broken Pipe)");
    }
}
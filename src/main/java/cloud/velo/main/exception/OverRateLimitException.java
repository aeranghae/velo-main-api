package cloud.velo.main.exception;

/**
 * [429 Too Many Requests] 처리율 제한(Rate Limit) 초과 시 발동하는 커스텀 예외
 */
public class OverRateLimitException extends RuntimeException {

    public OverRateLimitException(String message) {
        super(message); // 부모인 RuntimeException 상자에 에러 메시지를 넘겨줍니다.
    }
}
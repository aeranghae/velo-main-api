package cloud.velo.main.exception;


/**
 * [400 Bad Request] 생성 가능한 최대 프로젝트 개수를 초과했을 때 격발
 */
public class MaxProjectLimitException extends RuntimeException {
    public MaxProjectLimitException(String message) {
        super(message);
    }
}

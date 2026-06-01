package cloud.velo.main.exception;

/**
 * [404 Not Found] AI 모델 도메인에서 모델을 찾을 수 없거나 비활성화 상태일 때
 */
public class ModelNotFoundException extends RuntimeException {
    public ModelNotFoundException(String message) {
        super(message);
    }
}
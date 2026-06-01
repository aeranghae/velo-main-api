package cloud.velo.main.exception;

/**
 * [400 Bad Request] 프로젝트가 현재 AI 생성 공정 중(웹소켓 활성 상태)이라 명령을 수행할 수 없을 때
 */
public class ProjectActiveSessionException extends RuntimeException {
    public ProjectActiveSessionException(String message) {
        super(message);
    }
}
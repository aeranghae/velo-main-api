package cloud.velo.main.exception;

public class SandboxLifecycleException extends SandboxException {
    public SandboxLifecycleException(String message) {
        super(message);
    }

    public SandboxLifecycleException(String message, Throwable cause) {
        super(message, cause);
    }
}
package cloud.velo.main.exception;

public class SandboxStorageException extends SandboxException {
    public SandboxStorageException(String message) {
        super(message);
    }

    public SandboxStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
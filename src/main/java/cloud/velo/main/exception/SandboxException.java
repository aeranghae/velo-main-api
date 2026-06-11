package cloud.velo.main.exception;

public abstract class SandboxException extends RuntimeException {
  protected SandboxException(String message) {
    super(message);
  }
  protected SandboxException(String message, Throwable cause) {
    super(message, cause);
  }
}
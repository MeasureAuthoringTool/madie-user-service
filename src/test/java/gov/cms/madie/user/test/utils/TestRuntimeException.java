package gov.cms.madie.user.test.utils;

public class TestRuntimeException extends RuntimeException {
  public TestRuntimeException(String message) {
    super(message);
  }

  public TestRuntimeException(Throwable cause) {
    super(cause);
  }
}

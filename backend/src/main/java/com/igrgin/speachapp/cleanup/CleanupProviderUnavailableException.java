package com.igrgin.speachapp.cleanup;

public class CleanupProviderUnavailableException extends RuntimeException {
  public CleanupProviderUnavailableException(String message) {
    super(message);
  }

  public CleanupProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

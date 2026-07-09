package com.igrgin.speachapp.web;

import org.springframework.http.HttpStatus;

public class ApiProblem extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final boolean retryable;

  private ApiProblem(HttpStatus status, String code, String detail, boolean retryable) {
    super(detail);
    this.status = status;
    this.code = code;
    this.retryable = retryable;
  }

  public static ApiProblem badRequest(String code, String detail) {
    return new ApiProblem(HttpStatus.BAD_REQUEST, code, detail, false);
  }

  public static ApiProblem retryableServiceUnavailable(String code, String detail) {
    return new ApiProblem(HttpStatus.SERVICE_UNAVAILABLE, code, detail, true);
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public boolean retryable() {
    return retryable;
  }
}

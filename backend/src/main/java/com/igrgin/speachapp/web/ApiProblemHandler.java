package com.igrgin.speachapp.web;

import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiProblemHandler {
  @ExceptionHandler(ApiProblem.class)
  ProblemDetail handleApiProblem(ApiProblem problem) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(problem.status(), problem.getMessage());
    detail.setType(URI.create("https://smart-ai-text-to-speach-app.local/problems/" + problem.code()));
    detail.setTitle(problem.code());
    detail.setProperty("code", problem.code());
    detail.setProperty("retryable", problem.retryable());
    return detail;
  }
}

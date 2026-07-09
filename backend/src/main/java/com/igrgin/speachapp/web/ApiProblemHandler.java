package com.igrgin.speachapp.web;

import com.igrgin.speachapp.cleanup.CleanupProviderUnavailableException;
import com.igrgin.speachapp.transcription.TranscriptionProviderUnavailableException;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

  @ExceptionHandler(MissingServletRequestPartException.class)
  ProblemDetail handleMissingRequestPart(MissingServletRequestPartException exception) {
    return handleApiProblem(ApiProblem.badRequest(
        "malformed_multipart",
        "The dictation request must include a multipart audio part."
    ));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
    return handleApiProblem(ApiProblem.badRequest(
        "audio_too_large",
        "The uploaded audio exceeds the MVP request limit."
    ));
  }

  @ExceptionHandler(MultipartException.class)
  ProblemDetail handleMultipartException(MultipartException exception) {
    return handleApiProblem(ApiProblem.badRequest(
        "malformed_multipart",
        "The dictation request must be valid multipart form data."
    ));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
    if ("durationSeconds".equals(exception.getName())) {
      return handleApiProblem(ApiProblem.badRequest(
          "invalid_duration",
          "The reported recording duration must be a number within the MVP policy."
      ));
    }

    return handleApiProblem(ApiProblem.badRequest(
        "invalid_request",
        "The dictation request contains an invalid field value."
    ));
  }

  @ExceptionHandler(TranscriptionProviderUnavailableException.class)
  ProblemDetail handleTranscriptionProviderUnavailable(TranscriptionProviderUnavailableException exception) {
    return handleRetryableProviderProblem("transcription_provider_unavailable", exception);
  }

  @ExceptionHandler(CleanupProviderUnavailableException.class)
  ProblemDetail handleCleanupProviderUnavailable(CleanupProviderUnavailableException exception) {
    return handleRetryableProviderProblem("cleanup_provider_unavailable", exception);
  }

  private ProblemDetail handleRetryableProviderProblem(String code, RuntimeException exception) {
    return handleApiProblem(ApiProblem.retryableServiceUnavailable(
        code,
        exception.getMessage()
    ));
  }
}

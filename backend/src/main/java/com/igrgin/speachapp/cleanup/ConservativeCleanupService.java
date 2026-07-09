package com.igrgin.speachapp.cleanup;

import com.igrgin.speachapp.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "provider-mode",
    havingValue = AiProperties.SPRING_AI_OPENAI_PROVIDER_MODE
)
public class ConservativeCleanupService implements TranscriptCleaner {
  private static final int MAX_ATTEMPTS = 2;

  private final CleanupModelClient cleanupModelClient;

  public ConservativeCleanupService(CleanupModelClient cleanupModelClient) {
    this.cleanupModelClient = cleanupModelClient;
  }

  @Override
  public CleanupResult clean(String rawTranscript) {
    RuntimeException lastFailure = null;

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        CleanupModelResult result = cleanupModelClient.clean(rawTranscript);
        if (result == null) {
          throw new CleanupModelResult.MalformedCleanupModelResultException(
              "Cleanup provider returned no Cleanup Result."
          );
        }
        return result.toCleanupResult(rawTranscript);
      } catch (CleanupModelResult.MalformedCleanupModelResultException exception) {
        lastFailure = exception;
      } catch (RuntimeException exception) {
        lastFailure = exception;
      }
    }

    throw new CleanupProviderUnavailableException(
        lastFailure instanceof CleanupModelResult.MalformedCleanupModelResultException
            ? "Cleanup provider returned malformed Cleanup Result."
            : "Cleanup provider is temporarily unavailable.",
        lastFailure
    );
  }
}

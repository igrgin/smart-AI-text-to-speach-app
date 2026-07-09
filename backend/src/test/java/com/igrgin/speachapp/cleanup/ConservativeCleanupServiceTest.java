package com.igrgin.speachapp.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConservativeCleanupServiceTest {
  @Test
  void returnsValidatedCleanupResultFromStructuredModelResult() {
    CleanupModelResult modelResult = validResult("um ship it", "Ship it.");
    ConservativeCleanupService service = new ConservativeCleanupService(new QueueCleanupModelClient(modelResult));

    CleanupResult result = service.clean("um ship it");

    assertEquals("um ship it", result.rawTranscript());
    assertEquals("Ship it.", result.cleanedText());
    assertEquals(1, result.uncertainties().size());
    assertEquals("maybe", result.uncertainties().getFirst().text());
    assertEquals(UncertaintyReasonCategory.HEDGING_LANGUAGE, result.uncertainties().getFirst().reasonCategory());
  }

  @Test
  void retriesMalformedStructuredOutputBeforeReturningCleanupResult() {
    ConservativeCleanupService service = new ConservativeCleanupService(new QueueCleanupModelClient(
        validResult("different raw transcript", "Ship it."),
        validResult("um ship it", "Ship it.")
    ));

    CleanupResult result = service.clean("um ship it");

    assertEquals("Ship it.", result.cleanedText());
  }

  @Test
  void mapsRepeatedMalformedStructuredOutputToRetryableCleanupFailure() {
    ConservativeCleanupService service = new ConservativeCleanupService(new QueueCleanupModelClient(
        validResult("different raw transcript", "Ship it."),
        validResult("um ship it", " ")
    ));

    CleanupProviderUnavailableException exception = assertThrows(
        CleanupProviderUnavailableException.class,
        () -> service.clean("um ship it")
    );

    assertEquals("Cleanup provider returned malformed Cleanup Result.", exception.getMessage());
  }

  @Test
  void mapsProviderExceptionsToRetryableCleanupFailure() {
    ConservativeCleanupService service = new ConservativeCleanupService(new QueueCleanupModelClient(
        new IllegalStateException("provider timeout"),
        new IllegalStateException("provider timeout")
    ));

    CleanupProviderUnavailableException exception = assertThrows(
        CleanupProviderUnavailableException.class,
        () -> service.clean("um ship it")
    );

    assertEquals("Cleanup provider is temporarily unavailable.", exception.getMessage());
  }

  @Test
  void retriesStructuredOutputParsingFailuresBeforeReturningCleanupResult() {
    ConservativeCleanupService service = new ConservativeCleanupService(new QueueCleanupModelClient(
        new IllegalArgumentException("structured output parse failed"),
        validResult("um ship it", "Ship it.")
    ));

    CleanupResult result = service.clean("um ship it");

    assertEquals("Ship it.", result.cleanedText());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "- Ship it.",
      "[OpenAI](https://openai.com) ships it.",
      "![OpenAI logo](openai.png)",
      "_Ship it._",
      "~~Ship it.~~",
      "| Result |"
  })
  void rejectsFormattedCleanedTextAsMalformedOutput(String formattedCleanedText) {
    ConservativeCleanupService service = new ConservativeCleanupService(new QueueCleanupModelClient(
        validResult("um ship it", formattedCleanedText),
        validResult("um ship it", "Ship it.")
    ));

    CleanupResult result = service.clean("um ship it");

    assertEquals("Ship it.", result.cleanedText());
  }

  @Test
  void rejectsMissingCleanupUncertaintiesAsMalformedOutput() {
    ConservativeCleanupService service = new ConservativeCleanupService(new QueueCleanupModelClient(
        new CleanupModelResult("um ship it", "Ship it.", null),
        validResult("um ship it", "Ship it.")
    ));

    CleanupResult result = service.clean("um ship it");

    assertEquals("Ship it.", result.cleanedText());
  }

  @Test
  void rejectsMalformedCleanupUncertainties() {
    ConservativeCleanupService service = new ConservativeCleanupService(new QueueCleanupModelClient(
        new CleanupModelResult(
            "um ship it",
            "Ship it.",
            List.of(new CleanupModelResult.CleanupModelUncertainty(
                "",
                UncertaintyReasonCategory.AMBIGUOUS_INTENT,
                "Needs review."
            ))
        ),
        validResult("um ship it", "Ship it.")
    ));

    CleanupResult result = service.clean("um ship it");

    assertEquals("Ship it.", result.cleanedText());
  }

  private static CleanupModelResult validResult(String rawTranscript, String cleanedText) {
    return new CleanupModelResult(
        rawTranscript,
        cleanedText,
        List.of(new CleanupModelResult.CleanupModelUncertainty(
            "maybe",
            UncertaintyReasonCategory.HEDGING_LANGUAGE,
            "Hedging Language preserved during Conservative Cleanup."
        ))
    );
  }

  private static class QueueCleanupModelClient implements CleanupModelClient {
    private final Queue<Object> outcomes = new ArrayDeque<>();

    QueueCleanupModelClient(Object... outcomes) {
      this.outcomes.addAll(List.of(outcomes));
    }

    @Override
    public CleanupModelResult clean(String rawTranscript) {
      Object outcome = outcomes.remove();
      if (outcome instanceof RuntimeException exception) {
        throw exception;
      }
      return (CleanupModelResult) outcome;
    }
  }
}

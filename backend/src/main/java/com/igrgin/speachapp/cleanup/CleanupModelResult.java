package com.igrgin.speachapp.cleanup;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record CleanupModelResult(
    String rawTranscript,
    String cleanedText,
    List<CleanupModelUncertainty> uncertainties
) {
  private static final Pattern MARKDOWN_BLOCK_OR_RICH_TEXT = Pattern.compile(
      "(?m)(^#{1,6}\\s+|^\\s*[-*+]\\s+|^\\s*\\d+\\.\\s+|^>\\s+|^\\s*\\|.+\\|\\s*$"
          + "|```|`[^`]+`|!\\[[^\\]]*\\]\\([^)]*\\)|\\[[^\\]]+\\]\\([^)]*\\)"
          + "|\\*\\*[^*]+\\*\\*|__[^_]+__|~~[^~]+~~|(?<!\\*)\\*[^*\\n]+\\*(?!\\*)|(?<!_)_[^_\\n]+_(?!_))"
  );

  CleanupResult toCleanupResult(String expectedRawTranscript) {
    validate(expectedRawTranscript);
    List<CleanupUncertainty> mappedUncertainties = Objects.requireNonNull(uncertainties)
        .stream()
        .map(CleanupModelUncertainty::toCleanupUncertainty)
        .toList();

    return new CleanupResult(rawTranscript, cleanedText, mappedUncertainties);
  }

  private void validate(String expectedRawTranscript) {
    if (!expectedRawTranscript.equals(rawTranscript)) {
      throw new MalformedCleanupModelResultException("Cleanup provider changed the Raw Transcript.");
    }
    if (cleanedText == null || cleanedText.isBlank()) {
      throw new MalformedCleanupModelResultException("Cleanup provider returned blank Cleaned Text.");
    }
    if (MARKDOWN_BLOCK_OR_RICH_TEXT.matcher(cleanedText).find()) {
      throw new MalformedCleanupModelResultException("Cleanup provider returned formatted Cleaned Text.");
    }
    if (uncertainties == null) {
      throw new MalformedCleanupModelResultException("Cleanup provider omitted Cleanup Uncertainties.");
    }

    for (CleanupModelUncertainty uncertainty : uncertainties) {
      if (uncertainty == null
          || uncertainty.text() == null
          || uncertainty.text().isBlank()
          || uncertainty.reasonCategory() == null
          || uncertainty.reason() == null
          || uncertainty.reason().isBlank()) {
        throw new MalformedCleanupModelResultException(
            "Cleanup provider returned malformed Cleanup Uncertainty."
        );
      }
    }
  }

  public record CleanupModelUncertainty(
      String text,
      UncertaintyReasonCategory reasonCategory,
      String reason
  ) {
    CleanupUncertainty toCleanupUncertainty() {
      return new CleanupUncertainty(text, reasonCategory, reason);
    }
  }

  static class MalformedCleanupModelResultException extends RuntimeException {
    MalformedCleanupModelResultException(String message) {
      super(message);
    }
  }
}

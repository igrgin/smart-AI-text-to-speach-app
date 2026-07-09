package com.igrgin.speachapp.fake;

import com.igrgin.speachapp.cleanup.CleanupResult;
import com.igrgin.speachapp.cleanup.CleanupUncertainty;
import com.igrgin.speachapp.cleanup.TranscriptCleaner;
import com.igrgin.speachapp.cleanup.UncertaintyReasonCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class FakeTranscriptCleaner implements TranscriptCleaner {
  private static final Pattern DISFLUENCY_FILLER = Pattern.compile("\\b(um|uh)\\b\\s*", Pattern.CASE_INSENSITIVE);
  private static final Pattern ACCIDENTAL_REPEATED_THE = Pattern.compile("\\b(the)\\s+\\1\\b", Pattern.CASE_INSENSITIVE);
  private static final String DEFAULT_RAW_TRANSCRIPT = "um this is a quick note about open ai and spring boot new paragraph i think the upload flow should stay simple but maybe we should make the retry obvious";

  @Override
  public CleanupResult clean(String rawTranscript) {
    List<CleanupUncertainty> uncertainties = new ArrayList<>();
    String cleanedText = normalizeWhitespace(rawTranscript);

    if (cleanedText.contains("i said period")) {
      uncertainties.add(new CleanupUncertainty(
          "said period",
          UncertaintyReasonCategory.SPOKEN_PUNCTUATION_AMBIGUITY,
          "Spoken punctuation may be literal content or a command."
      ));
    }
    if (cleanedText.contains("friday or maybe next week")) {
      uncertainties.add(new CleanupUncertainty(
          "friday or maybe next week",
          UncertaintyReasonCategory.AMBIGUOUS_INTENT,
          "The intended deadline is ambiguous and should be reviewed."
      ));
    }
    if (rawTranscript.equals(DEFAULT_RAW_TRANSCRIPT)) {
      uncertainties.add(new CleanupUncertainty(
          "maybe",
          UncertaintyReasonCategory.HEDGING_LANGUAGE,
          "Hedging Language preserved during Conservative Cleanup."
      ));
    }

    cleanedText = DISFLUENCY_FILLER.matcher(cleanedText).replaceAll("");
    cleanedText = ACCIDENTAL_REPEATED_THE.matcher(cleanedText).replaceAll("$1");
    cleanedText = applyParagraphCommand(cleanedText);
    cleanedText = applyPunctuationInference(cleanedText);
    cleanedText = normalizeDomainTerms(cleanedText);
    cleanedText = capitalizeSentenceStarts(cleanedText);

    return new CleanupResult(rawTranscript, cleanedText, List.copyOf(uncertainties));
  }

  private static String normalizeWhitespace(String rawTranscript) {
    return rawTranscript.trim().replaceAll("\\s+", " ");
  }

  private static String applyParagraphCommand(String text) {
    if (text.contains("phrase new paragraph")) {
      return text;
    }
    return text.replace(" new paragraph ", ".\n\n");
  }

  private static String applyPunctuationInference(String text) {
    String punctuated = text
        .replace(
            "the upload finished the cleanup started the result is ready",
            "the upload finished. the cleanup started. the result is ready"
        )
        .replace(
            "this matters this matters because",
            "this matters. this matters because"
        );

    if (punctuated.endsWith(".") || punctuated.endsWith("?") || punctuated.endsWith("!")) {
      return punctuated;
    }
    if (punctuated.matches("^(can|could|would|should|what|where|when|why|how)\\b.*")) {
      return punctuated + "?";
    }
    return punctuated + ".";
  }

  private static String normalizeDomainTerms(String text) {
    return text
        .replace("open ai", "OpenAI")
        .replace("github", "GitHub")
        .replace("react", "React")
        .replace("spring boot", "Spring Boot")
        .replace("cleaned text", "Cleaned Text")
        .replace("unsupported spoken edit command", "Unsupported Spoken Edit Command")
        .replace("friday", "Friday");
  }

  private static String capitalizeSentenceStarts(String text) {
    StringBuilder capitalized = new StringBuilder(text.length());
    boolean capitalizeNext = true;

    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (capitalizeNext && Character.isLetter(character)) {
        capitalized.append(Character.toUpperCase(character));
        capitalizeNext = false;
        continue;
      }

      capitalized.append(character);
      if (character == '.' || character == '?' || character == '!' || character == '\n') {
        capitalizeNext = true;
      } else if (!Character.isWhitespace(character)) {
        capitalizeNext = false;
      }
    }

    return capitalized.toString();
  }
}

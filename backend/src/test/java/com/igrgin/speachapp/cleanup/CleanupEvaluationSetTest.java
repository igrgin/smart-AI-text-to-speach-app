package com.igrgin.speachapp.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.igrgin.speachapp.fake.FakeTranscriptCleaner;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CleanupEvaluationSetTest {
  private final TranscriptCleaner cleaner = new FakeTranscriptCleaner();

  @ParameterizedTest(name = "{0}")
  @MethodSource("cleanupEvaluationCases")
  void matchesResolvedCleanupEvaluationCase(CleanupEvaluationCase evaluationCase) {
    CleanupResult result = cleaner.clean(evaluationCase.rawTranscript());

    assertEquals(evaluationCase.rawTranscript(), result.rawTranscript());
    assertEquals(
        normalize(evaluationCase.expectedCleanedText()),
        normalize(result.cleanedText())
    );
    assertEquals(
        evaluationCase.expectedUncertainties().size(),
        result.uncertainties().size()
    );

    for (int index = 0; index < evaluationCase.expectedUncertainties().size(); index++) {
      ExpectedUncertainty expected = evaluationCase.expectedUncertainties().get(index);
      CleanupUncertainty actual = result.uncertainties().get(index);

      assertEquals(expected.text(), actual.text());
      assertEquals(expected.reasonCategory(), actual.reasonCategory());
    }
  }

  static Stream<CleanupEvaluationCase> cleanupEvaluationCases() {
    return Stream.of(
        new CleanupEvaluationCase(
            "Disfluency Filler removal",
            "um we should ship the review console today",
            "We should ship the review console today.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "meaningful like preservation",
            "i like the way the cleaned text keeps my voice",
            "I like the way the Cleaned Text keeps my voice.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "Hedging Language preservation",
            "i think we should maybe keep the first version simple",
            "I think we should maybe keep the first version simple.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "basic Punctuation Inference",
            "the upload finished the cleanup started the result is ready",
            "The upload finished. The cleanup started. The result is ready.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "question punctuation",
            "can we retry the cleanup if the provider is unavailable",
            "Can we retry the cleanup if the provider is unavailable?",
            List.of()
        ),
        new CleanupEvaluationCase(
            "standalone Paragraph Command",
            "record the first thought new paragraph record the second thought",
            """
                Record the first thought.

                Record the second thought.""",
            List.of()
        ),
        new CleanupEvaluationCase(
            "literal new paragraph",
            "the phrase new paragraph appears in the title",
            "The phrase new paragraph appears in the title.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "new line preservation",
            "please keep the words new line in this sentence",
            "Please keep the words new line in this sentence.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "unsupported spoken edit command preservation",
            "scratch that should stay in the raw idea because it is an unsupported spoken edit command",
            "Scratch that should stay in the raw idea because it is an Unsupported Spoken Edit Command.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "spoken punctuation ambiguity",
            "i said period because the abbreviation ends with a period",
            "I said period because the abbreviation ends with a period.",
            List.of(new ExpectedUncertainty(
                "said period",
                UncertaintyReasonCategory.SPOKEN_PUNCTUATION_AMBIGUITY
            ))
        ),
        new CleanupEvaluationCase(
            "accidental repeated word removal",
            "the the cleanup should remove the accidental repeated word",
            "The cleanup should remove the accidental repeated word.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "repeated idea preservation",
            "this matters this matters because the export has to use edited cleaned text",
            "This matters. This matters because the export has to use edited Cleaned Text.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "entity casing and spacing normalization",
            "open ai and github work with react and spring boot",
            "OpenAI and GitHub work with React and Spring Boot.",
            List.of()
        ),
        new CleanupEvaluationCase(
            "ambiguous cleanup surfacing Cleanup Uncertainty",
            "set up the thing by friday or maybe next week",
            "Set up the thing by Friday or maybe next week.",
            List.of(new ExpectedUncertainty(
                "friday or maybe next week",
                UncertaintyReasonCategory.AMBIGUOUS_INTENT
            ))
        )
    );
  }

  private static String normalize(String value) {
    return value.replace("\r\n", "\n").replace('\r', '\n').strip();
  }

  private record CleanupEvaluationCase(
      String name,
      String rawTranscript,
      String expectedCleanedText,
      List<ExpectedUncertainty> expectedUncertainties
  ) {
    @Override
    public String toString() {
      return name;
    }
  }

  private record ExpectedUncertainty(
      String text,
      UncertaintyReasonCategory reasonCategory
  ) {
  }
}

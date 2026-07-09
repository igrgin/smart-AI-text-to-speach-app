package com.igrgin.speachapp.fake;

import com.igrgin.speachapp.cleanup.CleanupResult;
import com.igrgin.speachapp.cleanup.CleanupUncertainty;
import com.igrgin.speachapp.cleanup.TranscriptCleaner;
import com.igrgin.speachapp.cleanup.UncertaintyReasonCategory;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FakeTranscriptCleaner implements TranscriptCleaner {
  private static final String CLEANED_TEXT = """
      This is a quick note about OpenAI and Spring Boot.

      I think the upload flow should stay simple, but maybe we should make the retry obvious.""";

  @Override
  public CleanupResult clean(String rawTranscript) {
    return new CleanupResult(
        rawTranscript,
        CLEANED_TEXT,
        List.of(new CleanupUncertainty(
            "maybe",
            UncertaintyReasonCategory.HEDGING_LANGUAGE,
            "Hedging Language preserved during Conservative Cleanup."
        ))
    );
  }
}

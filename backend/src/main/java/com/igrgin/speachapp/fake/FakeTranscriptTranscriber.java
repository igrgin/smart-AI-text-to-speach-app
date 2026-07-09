package com.igrgin.speachapp.fake;

import com.igrgin.speachapp.config.AiProperties;
import com.igrgin.speachapp.dictation.DictationRequest;
import com.igrgin.speachapp.transcription.TranscriptTranscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "provider-mode",
    havingValue = AiProperties.FAKE_PROVIDER_MODE,
    matchIfMissing = true
)
public class FakeTranscriptTranscriber implements TranscriptTranscriber {
  @Override
  public String transcribe(DictationRequest request) {
    return "um this is a quick note about open ai and spring boot new paragraph i think the upload flow should stay simple but maybe we should make the retry obvious";
  }
}

package com.igrgin.speachapp.fake;

import com.igrgin.speachapp.dictation.DictationRequest;
import com.igrgin.speachapp.transcription.TranscriptTranscriber;
import org.springframework.stereotype.Component;

@Component
public class FakeTranscriptTranscriber implements TranscriptTranscriber {
  @Override
  public String transcribe(DictationRequest request) {
    return "um this is a quick note about open ai and spring boot new paragraph i think the upload flow should stay simple but maybe we should make the retry obvious";
  }
}

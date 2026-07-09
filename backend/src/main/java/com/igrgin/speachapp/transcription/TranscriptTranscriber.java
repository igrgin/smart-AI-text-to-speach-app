package com.igrgin.speachapp.transcription;

import com.igrgin.speachapp.dictation.DictationRequest;

public interface TranscriptTranscriber {
  String transcribe(DictationRequest request);
}

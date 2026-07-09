package com.igrgin.speachapp.cleanup;

public interface TranscriptCleaner {
  CleanupResult clean(String rawTranscript);
}

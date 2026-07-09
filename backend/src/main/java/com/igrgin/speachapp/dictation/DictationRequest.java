package com.igrgin.speachapp.dictation;

public record DictationRequest(
    byte[] audioBytes,
    String contentType,
    String recordingMimeType,
    Double durationSeconds
) {
}

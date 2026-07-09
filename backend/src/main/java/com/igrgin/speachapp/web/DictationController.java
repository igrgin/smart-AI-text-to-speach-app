package com.igrgin.speachapp.web;

import com.igrgin.speachapp.cleanup.CleanupResult;
import com.igrgin.speachapp.config.DictationProperties;
import com.igrgin.speachapp.dictation.DictationRequest;
import com.igrgin.speachapp.dictation.DictationService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DictationController {
  private final DictationService dictationService;
  private final DictationProperties properties;

  public DictationController(DictationService dictationService, DictationProperties properties) {
    this.dictationService = dictationService;
    this.properties = properties;
  }

  @PostMapping(
      path = "/api/dictations",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public CleanupResult createDictation(
      @RequestPart("audio") MultipartFile audio,
      @RequestParam(required = false) String recordingMimeType,
      @RequestParam(required = false) Double durationSeconds
  ) throws IOException {
    String contentType = audio.getContentType();
    validateAudio(audio, contentType, durationSeconds);

    return dictationService.createCleanupResult(new DictationRequest(
        audio.getBytes(),
        contentType,
        recordingMimeType,
        durationSeconds
    ));
  }

  private void validateAudio(MultipartFile audio, String contentType, Double durationSeconds) {
    if (audio.isEmpty()) {
      throw ApiProblem.badRequest("empty_audio", "The audio part must contain browser-recorded audio.");
    }

    if (contentType == null || !properties.supportedAudioTypes().contains(contentType)) {
      throw ApiProblem.badRequest(
          "unsupported_audio_type",
          "Only browser-recorded audio containers such as audio/webm, audio/mp4, audio/ogg, or audio/wav are accepted."
      );
    }

    if (audio.getSize() > properties.maxAudioBytes()) {
      throw ApiProblem.badRequest("audio_too_large", "The uploaded audio exceeds the MVP request limit.");
    }

    if (durationSeconds != null && (durationSeconds <= 0 || durationSeconds > properties.maxDurationSeconds())) {
      throw ApiProblem.badRequest("invalid_duration", "The reported recording duration is outside the MVP policy.");
    }
  }
}

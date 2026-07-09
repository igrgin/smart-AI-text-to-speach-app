package com.igrgin.speachapp.web;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.igrgin.speachapp.cleanup.CleanupProviderUnavailableException;
import com.igrgin.speachapp.cleanup.CleanupResult;
import com.igrgin.speachapp.cleanup.CleanupUncertainty;
import com.igrgin.speachapp.cleanup.TranscriptCleaner;
import com.igrgin.speachapp.cleanup.UncertaintyReasonCategory;
import com.igrgin.speachapp.transcription.TranscriptTranscriber;
import com.igrgin.speachapp.transcription.TranscriptionProviderUnavailableException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

@SpringBootTest
@AutoConfigureMockMvc
class DictationControllerSmokeTest {
  @Autowired
  private MockMvc mockMvc;

  @Test
  void returnsCannedCleanupResultWithoutLiveProviderCalls() throws Exception {
    MockMultipartFile audio = webmAudio("fake-browser-audio");

    mockMvc.perform(multipart("/api/dictations")
            .file(audio)
            .param("recordingMimeType", "audio/webm")
            .param("durationSeconds", "42"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.rawTranscript", startsWith("um this is a quick note")))
        .andExpect(jsonPath("$.cleanedText", startsWith("This is a quick note about OpenAI and Spring Boot.")))
        .andExpect(jsonPath("$.uncertainties", hasSize(1)))
        .andExpect(jsonPath("$.uncertainties[0].text", equalTo("maybe")))
        .andExpect(jsonPath("$.uncertainties[0].reasonCategory", equalTo("HEDGING_LANGUAGE")));
  }

  @Test
  void rejectsUnsupportedAudioBeforeTheFakeProviderBoundary() throws Exception {
    MockMultipartFile audio = new MockMultipartFile(
        "audio",
        "note.txt",
        "text/plain",
        "not audio".getBytes()
    );

    expectProblem(
        mockMvc.perform(multipart("/api/dictations").file(audio)),
        status().isBadRequest(),
        "unsupported_audio_type",
        false
    );
  }

  @Test
  void rejectsUnsupportedRecordingMimeTypeBeforeTheFakeProviderBoundary() throws Exception {
    expectProblem(
        mockMvc.perform(multipart("/api/dictations")
            .file(webmAudio("fake-browser-audio"))
            .param("recordingMimeType", "text/plain")),
        status().isBadRequest(),
        "unsupported_audio_type",
        false
    );
  }

  @Test
  void rejectsOversizedAudioBeforeTheFakeProviderBoundary() throws Exception {
    MockMultipartFile audio = new MockMultipartFile(
        "audio",
        "large.webm",
        "audio/webm",
        new byte[25_000_001]
    );

    expectProblem(
        mockMvc.perform(multipart("/api/dictations").file(audio)),
        status().isBadRequest(),
        "audio_too_large",
        false
    );
  }

  @Test
  void rejectsInvalidDurationPolicyBeforeTheFakeProviderBoundary() throws Exception {
    expectProblem(
        mockMvc.perform(multipart("/api/dictations")
            .file(webmAudio("fake-browser-audio"))
            .param("durationSeconds", "-1")),
        status().isBadRequest(),
        "invalid_duration",
        false
    );
  }

  @Test
  void rejectsMalformedDurationValuesWithStableProblemDetails() throws Exception {
    expectProblem(
        mockMvc.perform(multipart("/api/dictations")
            .file(webmAudio("fake-browser-audio"))
            .param("durationSeconds", "not-a-number")),
        status().isBadRequest(),
        "invalid_duration",
        false
    );
  }

  @Test
  void rejectsNonFiniteDurationPolicyBeforeTheFakeProviderBoundary() throws Exception {
    expectProblem(
        mockMvc.perform(multipart("/api/dictations")
            .file(webmAudio("fake-browser-audio"))
            .param("durationSeconds", "NaN")),
        status().isBadRequest(),
        "invalid_duration",
        false
    );
  }

  @Test
  void rejectsMissingAudioPartAsMalformedMultipart() throws Exception {
    expectProblem(
        mockMvc.perform(multipart("/api/dictations")),
        status().isBadRequest(),
        "malformed_multipart",
        false
    );
  }

  @Test
  void rejectsMalformedMultipartBodies() throws Exception {
    expectProblem(
        mockMvc.perform(post("/api/dictations")
            .contentType(MediaType.parseMediaType("multipart/form-data; boundary=broken"))
            .content("not a multipart body")),
        status().isBadRequest(),
        "malformed_multipart",
        false
    );
  }

  @Test
  void mapsTranscriptionProviderFailuresToRetryableProblemDetails() throws Exception {
    expectProblem(
        mockMvc.perform(multipart("/api/dictations")
            .file(webmAudio("simulate-transcription-provider-failure"))),
        status().isServiceUnavailable(),
        "transcription_provider_unavailable",
        true
    );
  }

  @Test
  void mapsCleanupProviderFailuresToRetryableProblemDetails() throws Exception {
    expectProblem(
        mockMvc.perform(multipart("/api/dictations")
            .file(webmAudio("simulate-cleanup-provider-failure"))),
        status().isServiceUnavailable(),
        "cleanup_provider_unavailable",
        true
    );
  }

  private static MockMultipartFile webmAudio(String content) {
    return new MockMultipartFile(
        "audio",
        "note.webm",
        "audio/webm",
        content.getBytes(StandardCharsets.UTF_8)
    );
  }

  private static void expectProblem(
      ResultActions result,
      ResultMatcher statusMatcher,
      String code,
      boolean retryable
  ) throws Exception {
    result
        .andExpect(statusMatcher)
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code", equalTo(code)))
        .andExpect(jsonPath("$.retryable", equalTo(retryable)));
  }

  @TestConfiguration
  static class ContractFakes {
    @Bean
    @Primary
    TranscriptTranscriber contractTranscriber() {
      return request -> {
        String audioText = new String(request.audioBytes(), StandardCharsets.UTF_8);
        if (audioText.equals("simulate-transcription-provider-failure")) {
          throw new TranscriptionProviderUnavailableException("Transcription provider is temporarily unavailable.");
        }
        if (audioText.equals("simulate-cleanup-provider-failure")) {
          return "simulate cleanup provider failure";
        }
        return "um this is a quick note about open ai and spring boot new paragraph i think the upload flow should stay simple but maybe we should make the retry obvious";
      };
    }

    @Bean
    @Primary
    TranscriptCleaner contractCleaner() {
      return rawTranscript -> {
        if (rawTranscript.equals("simulate cleanup provider failure")) {
          throw new CleanupProviderUnavailableException("Cleanup provider is temporarily unavailable.");
        }
        return new CleanupResult(
            rawTranscript,
            """
                This is a quick note about OpenAI and Spring Boot.

                I think the upload flow should stay simple, but maybe we should make the retry obvious.""",
            List.of(new CleanupUncertainty(
                "maybe",
                UncertaintyReasonCategory.HEDGING_LANGUAGE,
                "Hedging Language preserved during Conservative Cleanup."
            ))
        );
      };
    }
  }
}

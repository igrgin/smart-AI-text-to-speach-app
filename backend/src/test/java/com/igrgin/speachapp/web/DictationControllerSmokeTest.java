package com.igrgin.speachapp.web;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DictationControllerSmokeTest {
  @Autowired
  private MockMvc mockMvc;

  @Test
  void returnsCannedCleanupResultWithoutLiveProviderCalls() throws Exception {
    MockMultipartFile audio = new MockMultipartFile(
        "audio",
        "note.webm",
        "audio/webm",
        "fake-browser-audio".getBytes()
    );

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

    mockMvc.perform(multipart("/api/dictations").file(audio))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code", equalTo("unsupported_audio_type")))
        .andExpect(jsonPath("$.retryable", equalTo(false)));
  }
}

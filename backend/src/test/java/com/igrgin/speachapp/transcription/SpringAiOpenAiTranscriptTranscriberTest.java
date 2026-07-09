package com.igrgin.speachapp.transcription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.igrgin.speachapp.config.AiProperties;
import com.igrgin.speachapp.dictation.DictationRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;

class SpringAiOpenAiTranscriptTranscriberTest {
  private final TranscriptionModel transcriptionModel = org.mockito.Mockito.mock(TranscriptionModel.class);
  private final SpringAiOpenAiTranscriptTranscriber transcriber = new SpringAiOpenAiTranscriptTranscriber(
      transcriptionModel,
      new AiProperties(AiProperties.SPRING_AI_OPENAI_PROVIDER_MODE, "gpt-4o-mini-transcribe")
  );

  @Test
  void buildsSpringAiPromptFromUploadedAudioAndConfiguredModel() throws IOException {
    when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(transcriptionResponse("raw words"));

    String transcript = transcriber.transcribe(new DictationRequest(
        "browser-audio".getBytes(StandardCharsets.UTF_8),
        "audio/webm",
        "audio/webm",
        12.0
    ));

    assertThat(transcript).isEqualTo("raw words");

    ArgumentCaptor<AudioTranscriptionPrompt> promptCaptor = ArgumentCaptor.forClass(AudioTranscriptionPrompt.class);
    verify(transcriptionModel).call(promptCaptor.capture());
    AudioTranscriptionPrompt prompt = promptCaptor.getValue();

    assertThat(prompt.getInstructions().getFilename()).isEqualTo("dictation.webm");
    assertThat(prompt.getInstructions().getContentAsByteArray())
        .isEqualTo("browser-audio".getBytes(StandardCharsets.UTF_8));
    assertThat(prompt.getOptions()).isInstanceOf(OpenAiAudioTranscriptionOptions.class);
    assertThat(((OpenAiAudioTranscriptionOptions) prompt.getOptions()).getModel())
        .isEqualTo("gpt-4o-mini-transcribe");
  }

  @Test
  void mapsSpringAiProviderFailuresToRetryableTranscriptionProblem() {
    IllegalStateException providerFailure = new IllegalStateException("provider unavailable");
    when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenThrow(providerFailure);

    assertThatThrownBy(() -> transcriber.transcribe(new DictationRequest(
        "browser-audio".getBytes(StandardCharsets.UTF_8),
        "audio/mp4",
        "audio/mp4",
        12.0
    )))
        .isInstanceOf(TranscriptionProviderUnavailableException.class)
        .hasMessage("Transcription provider is temporarily unavailable.")
        .hasCause(providerFailure);
  }

  @Test
  void treatsEmptyTranscriptionAsProviderFailure() {
    when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(transcriptionResponse(" "));

    assertThatThrownBy(() -> transcriber.transcribe(new DictationRequest(
        "browser-audio".getBytes(StandardCharsets.UTF_8),
        "audio/ogg",
        "audio/ogg",
        12.0
    )))
        .isInstanceOf(TranscriptionProviderUnavailableException.class)
        .hasMessage("Transcription provider returned an empty transcript.");
  }

  private static AudioTranscriptionResponse transcriptionResponse(String transcript) {
    return new AudioTranscriptionResponse(
        new AudioTranscription(transcript),
        new AudioTranscriptionResponseMetadata()
    );
  }
}

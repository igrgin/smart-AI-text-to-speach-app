package com.igrgin.speachapp.transcription;

import com.igrgin.speachapp.config.AiProperties;
import com.igrgin.speachapp.dictation.DictationRequest;
import java.util.Map;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "provider-mode",
    havingValue = AiProperties.SPRING_AI_OPENAI_PROVIDER_MODE
)
public class SpringAiOpenAiTranscriptTranscriber implements TranscriptTranscriber {
  private static final Map<String, String> FILE_EXTENSIONS_BY_CONTENT_TYPE = Map.of(
      "audio/webm", "webm",
      "audio/mp4", "mp4",
      "audio/ogg", "ogg",
      "audio/wav", "wav"
  );

  private final TranscriptionModel transcriptionModel;
  private final AiProperties properties;

  public SpringAiOpenAiTranscriptTranscriber(TranscriptionModel transcriptionModel, AiProperties properties) {
    this.transcriptionModel = transcriptionModel;
    this.properties = properties;
  }

  @Override
  public String transcribe(DictationRequest request) {
    Resource audioResource = new NamedByteArrayResource(
        request.audioBytes(),
        "dictation." + extensionFor(request.contentType())
    );
    OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
        .model(properties.transcriptionModel())
        .build();

    try {
      AudioTranscriptionResponse response = transcriptionModel.call(
          new AudioTranscriptionPrompt(audioResource, options)
      );
      String rawTranscript = response.getResult().getOutput();
      if (!StringUtils.hasText(rawTranscript)) {
        throw new TranscriptionProviderUnavailableException("Transcription provider returned an empty transcript.");
      }
      return rawTranscript;
    } catch (TranscriptionProviderUnavailableException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new TranscriptionProviderUnavailableException(
          "Transcription provider is temporarily unavailable.",
          exception
      );
    }
  }

  private static String extensionFor(String contentType) {
    if (contentType == null) {
      return "audio";
    }
    String normalizedContentType = contentType.split(";", 2)[0].trim();
    return FILE_EXTENSIONS_BY_CONTENT_TYPE.getOrDefault(normalizedContentType, "audio");
  }

  private static final class NamedByteArrayResource extends ByteArrayResource {
    private final String filename;

    private NamedByteArrayResource(byte[] byteArray, String filename) {
      super(byteArray);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }
}

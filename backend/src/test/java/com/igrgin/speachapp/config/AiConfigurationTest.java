package com.igrgin.speachapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class AiConfigurationTest {
  @Test
  void sharedConfigurationUsesFakeProvidersAndDoesNotActivateSpringProfile() throws IOException {
    PropertySource<?> application = loadYaml("application.yml").getFirst();

    assertThat(application.getProperty("spring.profiles.active")).isNull();
    assertThat(application.getProperty("app.ai.provider-mode")).isEqualTo(AiProperties.FAKE_PROVIDER_MODE);
    assertThat(application.getProperty("app.ai.transcription-model"))
        .isEqualTo(AiProperties.DEFAULT_TRANSCRIPTION_MODEL);
    assertThat(application.getProperty("app.ai.cleanup-model"))
        .isEqualTo(AiProperties.DEFAULT_CLEANUP_MODEL);
    assertThat(application.getProperty("spring.ai.model.audio.transcription")).isEqualTo("none");
    assertThat(application.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
    assertThat(application.getProperty("spring.ai.model.chat")).isEqualTo("none");
    assertThat(application.getProperty("spring.ai.model.embedding")).isEqualTo("none");
    assertThat(application.getProperty("spring.ai.model.image")).isEqualTo("none");
    assertThat(application.getProperty("spring.ai.model.moderation")).isEqualTo("none");
  }

  @Test
  void productionConfigurationResolvesOpenAiTranscriptionFromBackendEnvironment() throws IOException {
    PropertySource<?> production = loadYaml("application-prod.yml").getFirst();

    assertThat(production.getProperty("app.ai.provider-mode"))
        .isEqualTo(AiProperties.SPRING_AI_OPENAI_PROVIDER_MODE);
    assertThat(production.getProperty("app.ai.transcription-model"))
        .isEqualTo("${OPENAI_TRANSCRIPTION_MODEL:gpt-4o-mini-transcribe}");
    assertThat(production.getProperty("app.ai.cleanup-model"))
        .isEqualTo("${OPENAI_CLEANUP_MODEL:gpt-5.4-mini}");
    assertThat(production.getProperty("spring.ai.model.audio.transcription")).isEqualTo("openai");
    assertThat(production.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
    assertThat(production.getProperty("spring.ai.model.chat")).isEqualTo("openai");
    assertThat(production.getProperty("spring.ai.model.embedding")).isEqualTo("none");
    assertThat(production.getProperty("spring.ai.model.image")).isEqualTo("none");
    assertThat(production.getProperty("spring.ai.model.moderation")).isEqualTo("none");
    assertThat(production.getProperty("spring.ai.openai.api-key")).isEqualTo("${OPENAI_API_KEY}");
    assertThat(production.getProperty("spring.ai.openai.audio.transcription.model"))
        .isEqualTo("${OPENAI_TRANSCRIPTION_MODEL:gpt-4o-mini-transcribe}");
  }

  private static List<PropertySource<?>> loadYaml(String path) throws IOException {
    return new YamlPropertySourceLoader().load(path, new ClassPathResource(path));
  }
}

package com.igrgin.speachapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai")
public record AiProperties(
    String providerMode,
    String transcriptionModel,
    String cleanupModel
) {
  public static final String FAKE_PROVIDER_MODE = "fake";
  public static final String SPRING_AI_OPENAI_PROVIDER_MODE = "spring-ai-openai";
  public static final String DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe";
  public static final String DEFAULT_CLEANUP_MODEL = "gpt-5.4-mini";

  public AiProperties {
    if (providerMode == null || providerMode.isBlank()) {
      providerMode = FAKE_PROVIDER_MODE;
    }
    if (transcriptionModel == null || transcriptionModel.isBlank()) {
      transcriptionModel = DEFAULT_TRANSCRIPTION_MODEL;
    }
    if (cleanupModel == null || cleanupModel.isBlank()) {
      cleanupModel = DEFAULT_CLEANUP_MODEL;
    }
  }
}

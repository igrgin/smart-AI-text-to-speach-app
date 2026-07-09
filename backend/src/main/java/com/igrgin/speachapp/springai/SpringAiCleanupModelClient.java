package com.igrgin.speachapp.springai;

import com.igrgin.speachapp.cleanup.CleanupModelClient;
import com.igrgin.speachapp.cleanup.CleanupModelResult;
import com.igrgin.speachapp.config.AiProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "provider-mode",
    havingValue = AiProperties.SPRING_AI_OPENAI_PROVIDER_MODE
)
class SpringAiCleanupModelClient implements CleanupModelClient {
  static final String CONSERVATIVE_CLEANUP_PROMPT = """
      You perform Conservative Cleanup for English-First Cleanup.

      Return a structured Cleanup Result with:
      - rawTranscript: the exact Raw Transcript input, unchanged.
      - cleanedText: Plain Text Cleanup only.
      - uncertainties: Cleanup Uncertainties with text, reasonCategory, and reason.

      Conservative Cleanup policy:
      - Remove meaningless Disfluency Fillers such as um and uh.
      - Apply Punctuation Inference when sentence and question boundaries are clear.
      - Fix clear Speech Artifacts and obvious entity casing or spacing, including OpenAI, GitHub, React, and Spring Boot.
      - Preserve speaker wording, tone, order, Hedging Language, Literal Phrases, repeated ideas, and emphasis unless a correction is clear.
      - The exact standalone Paragraph Command "new paragraph" creates one paragraph break and is removed when clearly used as a command.
      - Preserve literal uses of "new paragraph" as content.
      - Preserve Unsupported Spoken Edit Commands as content, including new line, scratch that, delete that, and replace X with Y.
      - Do not perform Style Actions such as summarizing, formalizing, shortening, changing tone, or converting to bullets.
      - Do not introduce Markdown, headings, bullets, bold text, code spans, rich text, or explanatory wrapper text.

      Cleanup Uncertainty reasonCategory must be one of:
      HEDGING_LANGUAGE, AMBIGUOUS_SPEECH_ARTIFACT, SPOKEN_PUNCTUATION_AMBIGUITY, AMBIGUOUS_INTENT.
      Add a Cleanup Uncertainty when a cleanup choice could alter intended meaning.
      """;

  private final ChatClient chatClient;

  SpringAiCleanupModelClient(
      ChatClient.Builder chatClientBuilder,
      AiProperties properties
  ) {
    this.chatClient = chatClientBuilder
        .defaultOptions(OpenAiChatOptions.builder()
            .model(properties.cleanupModel())
            .temperature(0.0))
        .build();
  }

  @Override
  public CleanupModelResult clean(String rawTranscript) {
    return chatClient.prompt()
        .system(CONSERVATIVE_CLEANUP_PROMPT)
        .user(user -> user.text("""
            Clean this Raw Transcript and return only the structured Cleanup Result.

            Raw Transcript:
            {rawTranscript}
            """).param("rawTranscript", rawTranscript))
        .call()
        .entity(
            CleanupModelResult.class,
            entity -> entity.useProviderStructuredOutput().validateSchema()
        );
  }
}

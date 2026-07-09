export const MOCK_RECORDING_MIME_TYPE = "audio/webm";
export const MOCK_AUDIO_PAYLOAD = "recorded browser audio";
export const MOCK_CLEANED_TEXT = "This is a quick note about OpenAI and Spring Boot.";
export const PROVIDER_BLIND_TEXT_PATTERN = /OPENAI_API_KEY|OPENAI_TRANSCRIPTION_MODEL|OPENAI_CLEANUP_MODEL|provider selector|model selector|api key|credential/i;

export function mockCleanupResult() {
  return {
    rawTranscript: "um this is a quick note about open ai and spring boot",
    cleanedText: MOCK_CLEANED_TEXT,
    uncertainties: [
      {
        text: "maybe",
        reasonCategory: "HEDGING_LANGUAGE",
        reason: "Hedging Language preserved during Conservative Cleanup.",
      },
    ],
  };
}

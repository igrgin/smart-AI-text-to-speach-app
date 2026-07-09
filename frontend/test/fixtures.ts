export const MOCK_RECORDING_MIME_TYPE = "audio/webm";
export const MOCK_AUDIO_PAYLOAD = "recorded browser audio";
export const MOCK_CLEANED_TEXT = "This is a quick note about OpenAI and Spring Boot.";
export const PROVIDER_BLIND_TEXT_PATTERN = /OPENAI_API_KEY|OPENAI_TRANSCRIPTION_MODEL|OPENAI_CLEANUP_MODEL|provider selector|model selector|api key|credential/i;
export const PROVIDER_CONTROL_NAME_PATTERN = /OPENAI_API_KEY|OPENAI_TRANSCRIPTION_MODEL|OPENAI_CLEANUP_MODEL|provider|model|openai|gpt-|whisper|api key|credential|token|secret/i;
export const PROVIDER_CONTROL_ROLES = ["button", "combobox", "textbox", "radio", "radiogroup", "listbox", "option", "checkbox", "switch", "tab"] as const;
export const PROVIDER_CONTROL_ATTRIBUTES = ["aria-label", "placeholder", "name", "value", "title"] as const;
export const REVIEW_TAB_NAMES = ["Cleaned Text", "Raw Transcript", "State"] as const;
export const APP_OWNED_PROVIDER_SURFACE_LABELS = ["Dictation workflow", "Review inspector", "State"] as const;

export const MOCK_RECORDING = {
  audioPayload: MOCK_AUDIO_PAYLOAD,
  recordingMimeType: MOCK_RECORDING_MIME_TYPE,
};

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

export type UncertaintyReasonCategory =
  | "HEDGING_LANGUAGE"
  | "AMBIGUOUS_SPEECH_ARTIFACT"
  | "SPOKEN_PUNCTUATION_AMBIGUITY"
  | "AMBIGUOUS_INTENT";

export type CleanupUncertainty = {
  text: string;
  reasonCategory: UncertaintyReasonCategory;
  reason: string;
};

export type CleanupResult = {
  rawTranscript: string;
  cleanedText: string;
  uncertainties: CleanupUncertainty[];
};

export async function createDictation(audio: Blob): Promise<CleanupResult> {
  const formData = new FormData();
  formData.append("audio", audio, "browser-recording.webm");
  formData.append("recordingMimeType", audio.type || "audio/webm");
  formData.append("durationSeconds", "42");

  const response = await fetch("/api/dictations", {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`Dictation request failed with status ${response.status}`);
  }

  return response.json() as Promise<CleanupResult>;
}

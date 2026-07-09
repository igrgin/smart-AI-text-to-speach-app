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

export const MAX_AUDIO_BYTES = 25_000_000;
export const MAX_RECORDING_SECONDS = 600;
export const RECORDING_FORMATS = [
  { mimeType: "audio/webm", extension: "webm" },
  { mimeType: "audio/mp4", extension: "m4a" },
  { mimeType: "audio/ogg", extension: "ogg" },
  { mimeType: "audio/wav", extension: "wav" },
] as const;

export type RecordingMimeType = (typeof RECORDING_FORMATS)[number]["mimeType"];

export type DictationProblem = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  retryable?: boolean;
};

export class DictationProblemError extends Error {
  readonly status: number;
  readonly code: string;
  readonly retryable: boolean;

  constructor(problem: DictationProblem, fallbackStatus: number) {
    super(problem.detail || problem.title || `Dictation request failed with status ${fallbackStatus}`);
    this.name = "DictationProblemError";
    this.status = problem.status ?? fallbackStatus;
    this.code = problem.code || problem.title || "dictation_request_failed";
    this.retryable = problem.retryable === true;
  }
}

export async function createDictation(
  audio: Blob,
  options: {
    recordingMimeType: RecordingMimeType;
    durationSeconds?: number;
  },
): Promise<CleanupResult> {
  const formData = new FormData();
  formData.append("audio", audio, `browser-recording.${extensionForMimeType(options.recordingMimeType)}`);
  formData.append("recordingMimeType", options.recordingMimeType);

  if (options.durationSeconds !== undefined) {
    formData.append("durationSeconds", options.durationSeconds.toFixed(1));
  }

  const response = await fetch("/api/dictations", {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new DictationProblemError(await readProblem(response), response.status);
  }

  return response.json() as Promise<CleanupResult>;
}

function extensionForMimeType(mimeType: string) {
  return RECORDING_FORMATS.find((format) => format.mimeType === mimeType)?.extension ?? "webm";
}

async function readProblem(response: Response): Promise<DictationProblem> {
  const contentType = response.headers.get("content-type") || "";

  if (!contentType.includes("json")) {
    return {
      status: response.status,
      detail: `Dictation request failed with status ${response.status}`,
      retryable: false,
    };
  }

  try {
    return (await response.json()) as DictationProblem;
  } catch {
    return {
      status: response.status,
      detail: `Dictation request failed with status ${response.status}`,
      retryable: false,
    };
  }
}

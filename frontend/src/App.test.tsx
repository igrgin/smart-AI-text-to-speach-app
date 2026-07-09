import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { App } from "./App";
import { MAX_AUDIO_BYTES } from "./api";

type FakeRecorderState = "inactive" | "recording";

let recorderInstances: FakeMediaRecorder[] = [];
let supportedMimeTypes = ["audio/webm"];

class FakeMediaRecorder {
  static isTypeSupported = vi.fn((mimeType: string) => supportedMimeTypes.includes(mimeType));
  static constructorError: Error | null = null;
  static nextData = new Blob(["recorded browser audio"], { type: "audio/webm" });

  readonly mimeType: string;
  state: FakeRecorderState = "inactive";
  ondataavailable: ((event: BlobEvent) => void) | null = null;
  onstop: (() => void) | null = null;

  constructor(_stream: MediaStream, options?: MediaRecorderOptions) {
    if (FakeMediaRecorder.constructorError) {
      throw FakeMediaRecorder.constructorError;
    }

    this.mimeType = options?.mimeType || "audio/webm";
    recorderInstances.push(this);
  }

  start() {
    this.state = "recording";
  }

  stop() {
    this.state = "inactive";
    this.ondataavailable?.({
      data: FakeMediaRecorder.nextData,
    } as BlobEvent);
    this.onstop?.();
  }
}

beforeEach(() => {
  recorderInstances = [];
  supportedMimeTypes = ["audio/webm"];
  FakeMediaRecorder.constructorError = null;
  FakeMediaRecorder.nextData = new Blob(["recorded browser audio"], { type: "audio/webm" });
  FakeMediaRecorder.isTypeSupported.mockClear();
  vi.restoreAllMocks();
  vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

test("requests microphone permission only on start, records with a probed MIME type, and uploads multipart audio", async () => {
  supportedMimeTypes = ["audio/mp4"];
  const stopTrack = vi.fn();
  const getUserMedia = vi.fn(async () => mediaStreamWithStop(stopTrack));
  stubMediaDevices(getUserMedia);
  const fetchMock = vi.fn(async () => jsonResponse(cleanupResult()));
  vi.stubGlobal("fetch", fetchMock);

  render(<App />);

  expect(getUserMedia).not.toHaveBeenCalled();

  await userEvent.click(screen.getByRole("button", { name: /start recording/i }));

  expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
  expect(screen.getByText("Recording microphone audio")).toBeInTheDocument();

  await userEvent.click(screen.getByRole("button", { name: /stop recording/i }));

  expect(stopTrack).toHaveBeenCalledTimes(1);
  expect(await screen.findByDisplayValue("This is a quick note about OpenAI and Spring Boot.")).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledWith(
    "/api/dictations",
    expect.objectContaining({
      method: "POST",
      body: expect.any(FormData),
    }),
  );

  const body = fetchMock.mock.calls[0][1]?.body as FormData;
  expect(body.get("recordingMimeType")).toBe("audio/mp4");
  expect(body.get("durationSeconds")).toBeTruthy();
  expect((body.get("audio") as File).name).toBe("browser-recording.m4a");
  expect((body.get("audio") as File).type).toBe("audio/mp4");
  expect(screen.queryByLabelText(/api key/i)).not.toBeInTheDocument();
  expect(screen.queryByLabelText(/model/i)).not.toBeInTheDocument();
  expect(screen.queryByLabelText(/provider/i)).not.toBeInTheDocument();
});

test("shows upload, transcription, cleanup, and review progress states", async () => {
  const stopTrack = vi.fn();
  stubMediaDevices(vi.fn(async () => mediaStreamWithStop(stopTrack)));
  const pendingResponse = deferred<Response>();
  vi.stubGlobal("fetch", vi.fn(() => pendingResponse.promise));

  render(<App />);

  await recordOnce();

  expect(await screen.findByText("Uploading audio")).toBeInTheDocument();
  expect(await screen.findByText("Transcribing audio")).toBeInTheDocument();
  expect(await screen.findByText("Cleaning transcript")).toBeInTheDocument();

  pendingResponse.resolve(jsonResponse(cleanupResult()));

  expect(await screen.findByDisplayValue("This is a quick note about OpenAI and Spring Boot.")).toBeInTheDocument();
  expect(screen.getByText("Ready for review")).toBeInTheDocument();
});

test("explains unsupported browser recording and validation failures without retry controls", async () => {
  vi.stubGlobal("MediaRecorder", undefined);
  const getUserMedia = vi.fn();
  stubMediaDevices(getUserMedia);

  const { unmount } = render(<App />);

  await userEvent.click(screen.getByRole("button", { name: /start recording/i }));

  expect(getUserMedia).not.toHaveBeenCalled();
  expect(screen.getByRole("alert")).toHaveTextContent("This browser does not support the recording workflow");
  expect(screen.queryByRole("button", { name: /retry/i })).not.toBeInTheDocument();

  unmount();
  vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
  stubMediaDevices(vi.fn(async () => mediaStreamWithStop(vi.fn())));
  vi.stubGlobal(
    "fetch",
    vi.fn(async () =>
      jsonResponse(
        {
          status: 400,
          detail: "Only browser-recorded audio containers such as audio/webm, audio/mp4, audio/ogg, or audio/wav are accepted.",
          code: "unsupported_audio_type",
          retryable: false,
        },
        400,
      ),
    ),
  );

  render(<App />);

  await recordOnce();

  expect(await screen.findByRole("alert")).toHaveTextContent("Only browser-recorded audio containers");
  expect(screen.getByRole("alert")).toHaveTextContent("unsupported_audio_type");
  expect(screen.queryByRole("button", { name: /retry/i })).not.toBeInTheDocument();
});

test("stops tracks when recorder setup fails and blocks oversized recordings before upload", async () => {
  const setupFailureStopTrack = vi.fn();
  stubMediaDevices(vi.fn(async () => mediaStreamWithStop(setupFailureStopTrack)));
  FakeMediaRecorder.constructorError = new Error("Recorder setup failed");
  const fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);

  const { unmount } = render(<App />);

  await userEvent.click(screen.getByRole("button", { name: /start recording/i }));

  expect(await screen.findByRole("alert")).toHaveTextContent("Recorder setup failed");
  expect(setupFailureStopTrack).toHaveBeenCalledTimes(1);
  expect(fetchMock).not.toHaveBeenCalled();

  unmount();
  FakeMediaRecorder.constructorError = null;
  FakeMediaRecorder.nextData = new Blob([new Uint8Array(MAX_AUDIO_BYTES + 1)], { type: "audio/webm" });
  stubMediaDevices(vi.fn(async () => mediaStreamWithStop(vi.fn())));

  render(<App />);

  await recordOnce();

  expect(await screen.findByRole("alert")).toHaveTextContent("exceeds the MVP request limit");
  expect(screen.getByRole("alert")).toHaveTextContent("audio_too_large");
  expect(fetchMock).not.toHaveBeenCalled();
});

test("offers retry controls for retryable transcription and cleanup failures", async () => {
  await expectRetryableProblem("transcription_provider_unavailable", "Retry transcription", "Transcribing audio");
  await expectRetryableProblem("cleanup_provider_unavailable", "Retry cleanup", "Cleaning transcript");
});

async function expectRetryableProblem(code: string, buttonName: string, retryState: string) {
  vi.unstubAllGlobals();
  vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
  stubMediaDevices(vi.fn(async () => mediaStreamWithStop(vi.fn())));
  const retryResponse = deferred<Response>();
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(
      jsonResponse(
        {
          status: 503,
          detail: `${code} can be retried.`,
          code,
          retryable: true,
        },
        503,
      ),
    )
    .mockReturnValueOnce(retryResponse.promise);
  vi.stubGlobal("fetch", fetchMock);
  const { unmount } = render(<App />);

  await recordOnce();

  expect(await screen.findByRole("alert")).toHaveTextContent(code);
  await userEvent.click(screen.getByRole("button", { name: buttonName }));

  expect(await screen.findByText(retryState)).toBeInTheDocument();
  retryResponse.resolve(jsonResponse(cleanupResult()));
  expect(await screen.findByDisplayValue("This is a quick note about OpenAI and Spring Boot.")).toBeInTheDocument();
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  unmount();
}

async function recordOnce() {
  await userEvent.click(screen.getByRole("button", { name: /start recording/i }));
  await userEvent.click(screen.getByRole("button", { name: /stop recording/i }));
}

function stubMediaDevices(getUserMedia: ReturnType<typeof vi.fn>) {
  Object.defineProperty(navigator, "mediaDevices", {
    configurable: true,
    value: { getUserMedia },
  });
}

function mediaStreamWithStop(stopTrack: () => void) {
  return {
    getTracks: () => [{ stop: stopTrack }],
  } as unknown as MediaStream;
}

function cleanupResult() {
  return {
    rawTranscript: "um this is a quick note about open ai and spring boot",
    cleanedText: "This is a quick note about OpenAI and Spring Boot.",
    uncertainties: [
      {
        text: "maybe",
        reasonCategory: "HEDGING_LANGUAGE",
        reason: "Hedging Language preserved during Conservative Cleanup.",
      },
    ],
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/problem+json" },
  });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve;
  });

  return { promise, resolve };
}

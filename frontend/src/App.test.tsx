import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import {
  APP_OWNED_PROVIDER_SURFACE_LABELS,
  MOCK_AUDIO_PAYLOAD,
  MOCK_CLEANED_TEXT,
  MOCK_RECORDING_MIME_TYPE,
  mockCleanupResult,
  PROVIDER_CONTROL_ATTRIBUTES,
  PROVIDER_CONTROL_NAME_PATTERN,
  PROVIDER_CONTROL_ROLES,
  PROVIDER_BLIND_TEXT_PATTERN,
  REVIEW_TAB_NAMES,
} from "../test/fixtures";
import { App } from "./App";
import { MAX_AUDIO_BYTES } from "./api";

type FakeRecorderState = "inactive" | "recording";

let recorderInstances: FakeMediaRecorder[] = [];
let supportedMimeTypes = ["audio/webm"];

class FakeMediaRecorder {
  static isTypeSupported = vi.fn((mimeType: string) => supportedMimeTypes.includes(mimeType));
  static constructorError: Error | null = null;
  static nextData = new Blob([MOCK_AUDIO_PAYLOAD], { type: MOCK_RECORDING_MIME_TYPE });

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
  FakeMediaRecorder.nextData = new Blob([MOCK_AUDIO_PAYLOAD], { type: MOCK_RECORDING_MIME_TYPE });
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
  const fetchMock = vi.fn(async () => jsonResponse(mockCleanupResult()));
  vi.stubGlobal("fetch", fetchMock);

  render(<App />);

  expect(getUserMedia).not.toHaveBeenCalled();

  await userEvent.click(screen.getByRole("button", { name: /start recording/i }));

  expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
  expect(screen.getByText("Recording microphone audio")).toBeInTheDocument();

  await userEvent.click(screen.getByRole("button", { name: /stop recording/i }));

  expect(stopTrack).toHaveBeenCalledTimes(1);
  expect(await screen.findByDisplayValue(MOCK_CLEANED_TEXT)).toBeInTheDocument();
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
  expectNoProviderControls();
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

  pendingResponse.resolve(jsonResponse(mockCleanupResult()));

  expect(await screen.findByDisplayValue(MOCK_CLEANED_TEXT)).toBeInTheDocument();
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

test("keeps Cleaned Text editable and uses local edits for copy and export", async () => {
  const writeText = vi.fn(async () => undefined);
  const createObjectURL = vi.fn(() => "blob:edited-cleaned-text");
  const revokeObjectURL = vi.fn();
  const clickDownload = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
  Object.defineProperty(navigator, "clipboard", {
    configurable: true,
    value: { writeText },
  });
  vi.stubGlobal("URL", {
    ...URL,
    createObjectURL,
    revokeObjectURL,
  });
  await renderRecordedCleanup();

  const cleanedText = await screen.findByRole("textbox", { name: "Cleaned Text" });
  expect(cleanedText).toHaveValue(MOCK_CLEANED_TEXT);
  expect(cleanedText).not.toHaveAttribute("readonly");

  await userEvent.clear(cleanedText);
  await userEvent.type(cleanedText, "Edited local Cleaned Text.");
  await userEvent.click(screen.getByRole("button", { name: "Copy Cleaned Text" }));
  await userEvent.click(screen.getByRole("button", { name: "Export Cleaned Text" }));

  expect(writeText).toHaveBeenCalledWith("Edited local Cleaned Text.");
  expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
  await expect((createObjectURL.mock.calls[0][0] as Blob).text()).resolves.toBe("Edited local Cleaned Text.");
  expect(clickDownload).toHaveBeenCalledTimes(1);
  expect(revokeObjectURL).toHaveBeenCalledWith("blob:edited-cleaned-text");
  expect(screen.getByText("Exported edited Cleaned Text.")).toBeInTheDocument();
});

test("keeps Raw Transcript and State secondary review tabs with Cleanup Uncertainty signals", async () => {
  await renderRecordedCleanup();

  expect(await screen.findByRole("textbox", { name: "Cleaned Text" })).toHaveValue(MOCK_CLEANED_TEXT);
  expect(screen.queryByLabelText("Raw Transcript")).not.toBeInTheDocument();
  expect(screen.getByText("HEDGING_LANGUAGE")).toBeInTheDocument();
  expect(screen.getByText("maybe")).toBeInTheDocument();

  await userEvent.click(screen.getByRole("tab", { name: "Raw Transcript" }));

  expect(screen.getByLabelText("Raw Transcript")).toHaveTextContent("um this is a quick note about open ai and spring boot");

  await userEvent.click(screen.getByRole("tab", { name: "State" }));

  expect(screen.getByLabelText("State")).toHaveTextContent('"reviewOwner": "frontend local review"');
  expect(screen.getByLabelText("State")).toHaveTextContent('"editedTextLength"');
  expect(screen.getByLabelText("State")).toHaveTextContent('"uncertaintyCount": 1');
});

test("keeps the mocked MVP smoke workflow provider-blind through review", async () => {
  await renderRecordedCleanup();

  expect(await screen.findByRole("textbox", { name: "Cleaned Text" })).toHaveValue(MOCK_CLEANED_TEXT);
  expect(screen.getByLabelText("Review inspector")).toHaveTextContent("Waiting for local output.");
  await expectProviderBlindReviewTabs();

  await userEvent.click(screen.getByRole("tab", { name: "Cleaned Text" }));
  await userEvent.click(screen.getByRole("tab", { name: "State" }));

  expect(screen.getByLabelText("State")).toHaveTextContent('"endpoint": "POST /api/dictations"');
  expect(screen.getByLabelText("State")).toHaveTextContent('"reviewOwner": "frontend local review"');
  expect(screen.getByLabelText("State")).not.toHaveTextContent("apiKey");
  expect(screen.getByLabelText("State")).not.toHaveTextContent("modelSelector");
  expect(screen.getByLabelText("State")).not.toHaveTextContent("providerSelector");
  expectNoProviderControls();
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
  retryResponse.resolve(jsonResponse(mockCleanupResult()));
  expect(await screen.findByDisplayValue(MOCK_CLEANED_TEXT)).toBeInTheDocument();
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  unmount();
}

async function recordOnce() {
  await userEvent.click(screen.getByRole("button", { name: /start recording/i }));
  await userEvent.click(screen.getByRole("button", { name: /stop recording/i }));
}

async function renderRecordedCleanup() {
  stubMediaDevices(vi.fn(async () => mediaStreamWithStop(vi.fn())));
  vi.stubGlobal("fetch", vi.fn(async () => jsonResponse(mockCleanupResult())));
  render(<App />);
  await recordOnce();
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

function expectNoProviderControls() {
  expect(document.body).not.toHaveTextContent(PROVIDER_BLIND_TEXT_PATTERN);

  for (const role of PROVIDER_CONTROL_ROLES) {
    expect(screen.queryAllByRole(role, { name: PROVIDER_CONTROL_NAME_PATTERN })).toHaveLength(0);
  }

  expect(screen.queryByLabelText(PROVIDER_CONTROL_NAME_PATTERN)).not.toBeInTheDocument();

  for (const control of document.querySelectorAll("button,input,textarea,select,[role]")) {
    const values = PROVIDER_CONTROL_ATTRIBUTES.map((attribute) => control.getAttribute(attribute));
    const visibleText = control.matches("input,textarea,select") ? null : control.textContent;
    expect([...values, visibleText].filter(Boolean).join(" ")).not.toMatch(PROVIDER_CONTROL_NAME_PATTERN);
  }

  for (const label of APP_OWNED_PROVIDER_SURFACE_LABELS) {
    for (const surface of screen.queryAllByLabelText(label)) {
      expect(surface).not.toHaveTextContent(PROVIDER_CONTROL_NAME_PATTERN);
    }
  }
}

async function expectProviderBlindReviewTabs() {
  for (const tabName of REVIEW_TAB_NAMES) {
    await userEvent.click(screen.getByRole("tab", { name: tabName }));
    expectNoProviderControls();
  }
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

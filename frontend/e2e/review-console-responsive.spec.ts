import { expect, type Page, test } from "@playwright/test";
import {
  APP_OWNED_PROVIDER_SURFACE_LABELS,
  MOCK_CLEANED_TEXT,
  MOCK_AUDIO_PAYLOAD,
  MOCK_RECORDING_MIME_TYPE,
  MOCK_RECORDING,
  mockCleanupResult,
  PROVIDER_CONTROL_ATTRIBUTES,
  PROVIDER_CONTROL_NAME_PATTERN,
  PROVIDER_CONTROL_ROLES,
  PROVIDER_BLIND_TEXT_PATTERN,
  REVIEW_TAB_NAMES,
} from "../test/fixtures";

test("keeps the Review Console usable when rails stack at phone width", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 });
  const dictationRequests = await installMockedDictationWorkflow(page);

  await page.goto("/");
  await page.getByRole("button", { name: "Start recording" }).click();
  await page.getByRole("button", { name: "Stop recording" }).click();

  await expect(page.getByRole("textbox", { name: "Cleaned Text" })).toHaveValue(MOCK_CLEANED_TEXT);
  await expect(page.getByRole("button", { name: "Copy Cleaned Text" })).toBeEnabled();
  await expect(page.getByRole("button", { name: "Export Cleaned Text" })).toBeEnabled();
  await expect(page.getByRole("tab", { name: "Raw Transcript" })).toBeVisible();
  await expect(page.getByLabel("Review inspector")).toContainText("HEDGING_LANGUAGE");
  await expectProviderBlindReviewTabs(page);
  await page.getByRole("tab", { name: "Cleaned Text" }).click();

  expect(dictationRequests).toHaveLength(1);
  expect(dictationRequests[0]).toMatchObject({
    method: "POST",
    recordingMimeType: MOCK_RECORDING_MIME_TYPE,
    hasAudioPart: true,
    hasAudioPayload: true,
    hasDurationSeconds: true,
    durationSeconds: expect.stringMatching(/^\d+\.\d$/),
  });
  expect(dictationRequests[0].contentType).toContain("multipart/form-data");

  const rail = await page.getByLabel("Dictation workflow").boundingBox();
  const workspace = await page.getByRole("region", { name: "Cleanup Result" }).boundingBox();
  const inspector = await page.getByLabel("Review inspector").boundingBox();
  const cleanedText = await page.getByRole("textbox", { name: "Cleaned Text" }).boundingBox();

  expect(rail).not.toBeNull();
  expect(workspace).not.toBeNull();
  expect(inspector).not.toBeNull();
  expect(cleanedText).not.toBeNull();

  expect(workspace!.y).toBeGreaterThanOrEqual(rail!.y + rail!.height);
  expect(inspector!.y).toBeGreaterThanOrEqual(workspace!.y + workspace!.height);
  expect(cleanedText!.height).toBeGreaterThanOrEqual(320);
  expect(cleanedText!.x + cleanedText!.width).toBeLessThanOrEqual(390);
});

async function installMockedDictationWorkflow(page: Page) {
  const dictationRequests: DictationRequestSnapshot[] = [];

  await page.addInitScript((mockRecording) => {
    window.__mockRecording = mockRecording;

    class FakeMediaRecorder {
      static isTypeSupported(mimeType: string) {
        return mimeType === window.__mockRecording.recordingMimeType;
      }

      state = "inactive";
      ondataavailable: ((event: { data: Blob }) => void) | null = null;
      onstop: (() => void) | null = null;

      start() {
        this.state = "recording";
      }

      stop() {
        this.state = "inactive";
        this.ondataavailable?.({
          data: new Blob([window.__mockRecording.audioPayload], { type: window.__mockRecording.recordingMimeType }),
        });
        this.onstop?.();
      }
    }

    Object.defineProperty(window, "MediaRecorder", {
      configurable: true,
      value: FakeMediaRecorder,
    });
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: {
        getUserMedia: async () => ({
          getTracks: () => [{ stop: () => undefined }],
        }),
      },
    });
  }, MOCK_RECORDING);

  await page.route("**/api/dictations", async (route) => {
    const request = route.request();
    const contentType = request.headers()["content-type"] ?? "";
    const body = request.postDataBuffer()?.toString("utf-8") ?? "";
    dictationRequests.push({
      method: request.method(),
      contentType,
      recordingMimeType: multipartFieldValue(body, "recordingMimeType"),
      hasAudioPart:
        body.includes('name="audio"; filename="browser-recording.webm"') &&
        body.includes(`Content-Type: ${MOCK_RECORDING_MIME_TYPE}`),
      hasAudioPayload: body.includes(MOCK_AUDIO_PAYLOAD),
      hasDurationSeconds: body.includes('name="durationSeconds"'),
      durationSeconds: multipartFieldValue(body, "durationSeconds"),
    });

    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(mockCleanupResult()),
    });
  });

  return dictationRequests;
}

async function expectProviderBlindReviewTabs(page: Page) {
  for (const tabName of REVIEW_TAB_NAMES) {
    await page.getByRole("tab", { name: tabName }).click();
    await expect(page.locator("body")).not.toContainText(PROVIDER_BLIND_TEXT_PATTERN);

    for (const role of PROVIDER_CONTROL_ROLES) {
      await expect(page.getByRole(role, { name: PROVIDER_CONTROL_NAME_PATTERN })).toHaveCount(0);
    }

    const controlAttributes = await page.locator("button,input,textarea,select,[role]").evaluateAll(
      (controls, attributes) =>
        controls
          .map((control) =>
            [
              ...attributes.map((attribute) => control.getAttribute(attribute)),
              control.matches("input,textarea,select") ? null : control.textContent,
            ]
              .filter(Boolean)
              .join(" "),
          )
          .join(" "),
      [...PROVIDER_CONTROL_ATTRIBUTES],
    );
    expect(controlAttributes).not.toMatch(PROVIDER_CONTROL_NAME_PATTERN);

    for (const label of APP_OWNED_PROVIDER_SURFACE_LABELS) {
      const surface = page.getByLabel(label);
      if ((await surface.count()) > 0) {
        await expect(surface).not.toContainText(PROVIDER_CONTROL_NAME_PATTERN);
      }
    }
  }
}

function multipartFieldValue(body: string, name: string) {
  const match = body.match(new RegExp(`name="${name}"\\r?\\n\\r?\\n([^\\r\\n-]+)`));
  return match?.[1].trim() ?? null;
}

type DictationRequestSnapshot = {
  method: string;
  contentType: string;
  recordingMimeType: string | null;
  hasAudioPart: boolean;
  hasAudioPayload: boolean;
  hasDurationSeconds: boolean;
  durationSeconds: string | null;
}

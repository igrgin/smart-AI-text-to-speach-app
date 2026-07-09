import { expect, type Page, test } from "@playwright/test";
import {
  MOCK_AUDIO_PAYLOAD,
  MOCK_CLEANED_TEXT,
  MOCK_RECORDING_MIME_TYPE,
  mockCleanupResult,
  PROVIDER_BLIND_TEXT_PATTERN,
} from "../test/fixtures";

test("keeps the Review Console usable when rails stack at phone width", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 });
  await installMockedDictationWorkflow(page);

  await page.goto("/");
  await page.getByRole("button", { name: "Start recording" }).click();
  await page.getByRole("button", { name: "Stop recording" }).click();

  await expect(page.getByRole("textbox", { name: "Cleaned Text" })).toHaveValue(MOCK_CLEANED_TEXT);
  await expect(page.getByRole("button", { name: "Copy Cleaned Text" })).toBeEnabled();
  await expect(page.getByRole("button", { name: "Export Cleaned Text" })).toBeEnabled();
  await expect(page.getByRole("tab", { name: "Raw Transcript" })).toBeVisible();
  await expect(page.getByLabel("Review inspector")).toContainText("HEDGING_LANGUAGE");
  await expect(page.locator("body")).not.toContainText(PROVIDER_BLIND_TEXT_PATTERN);

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
  await page.addInitScript(() => {
    class FakeMediaRecorder {
      static isTypeSupported(mimeType: string) {
        return mimeType === window.__mockRecordingMimeType;
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
          data: new Blob([window.__mockAudioPayload], { type: window.__mockRecordingMimeType }),
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
  });
  await page.addInitScript(
    ({ audioPayload, recordingMimeType }) => {
      window.__mockAudioPayload = audioPayload;
      window.__mockRecordingMimeType = recordingMimeType;
    },
    {
      audioPayload: MOCK_AUDIO_PAYLOAD,
      recordingMimeType: MOCK_RECORDING_MIME_TYPE,
    },
  );

  await page.route("**/api/dictations", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(mockCleanupResult()),
    });
  });
}

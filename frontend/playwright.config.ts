import { defineConfig, type PlaywrightTestConfig } from "@playwright/test";

const chromeChannel = process.env.PLAYWRIGHT_CHROME_CHANNEL;
const use: PlaywrightTestConfig["use"] = {
  baseURL: "http://127.0.0.1:5173",
  browserName: "chromium",
  ...(chromeChannel ? { channel: chromeChannel } : {}),
};

export default defineConfig({
  testDir: "./e2e",
  webServer: {
    command: "npm run dev -- --host 127.0.0.1",
    url: "http://127.0.0.1:5173",
    reuseExistingServer: !process.env.CI,
  },
  use,
});

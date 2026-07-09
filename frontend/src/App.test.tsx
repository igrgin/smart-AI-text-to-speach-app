import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";
import { App } from "./App";

beforeEach(() => {
  vi.restoreAllMocks();
});

test("runs the fake-provider dictation loop and shows the Cleanup Result", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({
      ok: true,
      json: async () => ({
        rawTranscript: "um this is a quick note about open ai and spring boot",
        cleanedText: "This is a quick note about OpenAI and Spring Boot.",
        uncertainties: [
          {
            text: "maybe",
            reasonCategory: "HEDGING_LANGUAGE",
            reason: "Hedging Language preserved during Conservative Cleanup.",
          },
        ],
      }),
    })),
  );

  render(<App />);

  await userEvent.click(screen.getByRole("button", { name: /run fake loop/i }));

  expect(await screen.findByDisplayValue("This is a quick note about OpenAI and Spring Boot.")).toBeInTheDocument();
  expect(screen.getByText("HEDGING_LANGUAGE")).toBeInTheDocument();
  expect(screen.queryByLabelText(/api key/i)).not.toBeInTheDocument();
  expect(screen.queryByLabelText(/model/i)).not.toBeInTheDocument();
  expect(screen.queryByLabelText(/provider/i)).not.toBeInTheDocument();
});

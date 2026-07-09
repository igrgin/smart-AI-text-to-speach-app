import { AlertTriangle, CheckCircle2, FileText, Loader2, Mic, Sparkles } from "lucide-react";
import { useMemo, useState } from "react";
import { createDictation, type CleanupResult } from "./api";

type WorkflowPhase = "idle" | "uploading" | "review" | "error";

export function App() {
  const [phase, setPhase] = useState<WorkflowPhase>("idle");
  const [result, setResult] = useState<CleanupResult | null>(null);
  const [activeTab, setActiveTab] = useState<"cleaned" | "raw" | "state">("cleaned");
  const [error, setError] = useState<string | null>(null);

  const stateSummary = useMemo(
    () => ({
      endpoint: "POST /api/dictations",
      reviewOwner: "frontend local review",
      phase,
    }),
    [phase],
  );

  async function runFakeProviderLoop() {
    setPhase("uploading");
    setError(null);

    try {
      const audio = new Blob(["fake browser audio"], { type: "audio/webm" });
      const cleanupResult = await createDictation(audio);
      setResult(cleanupResult);
      setActiveTab("cleaned");
      setPhase("review");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Dictation request failed");
      setPhase("error");
    }
  }

  return (
    <main className="app-shell">
      <section className="rail" aria-label="Dictation workflow">
        <div>
          <p className="eyebrow">Review Console</p>
          <h1>Smart AI Text To Speach</h1>
        </div>

        <button className="record-button" onClick={runFakeProviderLoop} disabled={phase === "uploading"}>
          {phase === "uploading" ? <Loader2 aria-hidden className="spin" /> : <Mic aria-hidden />}
          <span>{phase === "uploading" ? "Processing" : "Run fake loop"}</span>
        </button>

        <ol className="steps" aria-label="Workflow progress">
          <Step icon={<Mic aria-hidden />} label="Upload" active={phase === "uploading"} complete={phase === "review"} />
          <Step icon={<Sparkles aria-hidden />} label="Cleanup" active={phase === "uploading"} complete={phase === "review"} />
          <Step icon={<FileText aria-hidden />} label="Review" active={phase === "review"} complete={phase === "review"} />
        </ol>
      </section>

      <section className="workspace" aria-label="Cleanup Result">
        <div className="tabs" role="tablist" aria-label="Cleanup Result views">
          <TabButton active={activeTab === "cleaned"} onClick={() => setActiveTab("cleaned")}>
            Cleaned Text
          </TabButton>
          <TabButton active={activeTab === "raw"} onClick={() => setActiveTab("raw")}>
            Raw Transcript
          </TabButton>
          <TabButton active={activeTab === "state"} onClick={() => setActiveTab("state")}>
            State
          </TabButton>
        </div>

        <article className="text-surface">
          {activeTab === "cleaned" && (
            <textarea
              aria-label="Cleaned Text"
              value={result?.cleanedText ?? ""}
              readOnly
              placeholder="Run the fake provider loop to see Cleaned Text."
            />
          )}
          {activeTab === "raw" && (
            <pre aria-label="Raw Transcript">{result?.rawTranscript ?? "Raw Transcript will appear after upload."}</pre>
          )}
          {activeTab === "state" && <pre aria-label="State">{JSON.stringify(stateSummary, null, 2)}</pre>}
        </article>
      </section>

      <aside className="inspector" aria-label="Review inspector">
        <div className="status-card">
          {phase === "review" ? <CheckCircle2 aria-hidden /> : <Sparkles aria-hidden />}
          <div>
            <p className="card-label">Cleanup Result</p>
            <p>{phase === "review" ? "Ready for review" : "Waiting for fake upload"}</p>
          </div>
        </div>

        {error && (
          <div className="problem" role="alert">
            <AlertTriangle aria-hidden />
            <span>{error}</span>
          </div>
        )}

        <div>
          <p className="card-label">Cleanup Uncertainties</p>
          {result?.uncertainties.length ? (
            <ul className="uncertainties">
              {result.uncertainties.map((uncertainty) => (
                <li key={`${uncertainty.reasonCategory}:${uncertainty.text}`}>
                  <strong>{uncertainty.text}</strong>
                  <span>{uncertainty.reasonCategory}</span>
                  <p>{uncertainty.reason}</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">No review signals yet.</p>
          )}
        </div>
      </aside>
    </main>
  );
}

function Step({
  icon,
  label,
  active,
  complete,
}: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  complete: boolean;
}) {
  return (
    <li className={active ? "active" : complete ? "complete" : ""}>
      {icon}
      <span>{label}</span>
    </li>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button className={active ? "active" : ""} onClick={onClick} role="tab" aria-selected={active}>
      {children}
    </button>
  );
}

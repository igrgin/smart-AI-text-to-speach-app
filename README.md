# smart-AI-text-to-speach-app

A web app for turning recorded speech into Cleaned Text. The MVP keeps the
frontend provider-blind: browser audio is uploaded to the Spring Boot backend,
and backend-owned AI configuration handles transcription and Conservative
Cleanup.

## Local Setup

This repository has two runnable app surfaces:

- `backend/` - Spring Boot API for `POST /api/dictations`.
- `frontend/` - Vite React Review Console.

Prerequisites:

- JDK 25
- Maven
- Node.js 24.18.0 or newer
- npm

Install frontend dependencies once:

```bash
npm --prefix frontend install
```

Start the backend with the default fake provider mode:

```bash
mvn -f backend/pom.xml spring-boot:run
```

Start the frontend in another terminal:

```bash
npm --prefix frontend run dev
```

The frontend dev server proxies `/api` calls to `http://127.0.0.1:8080`.

Run the full automated test suite:

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
```

The frontend test command runs Vitest unit coverage plus a Playwright responsive
workflow check. It installs or checks Playwright's managed Chromium
automatically; set `PLAYWRIGHT_CHROME_CHANNEL=chrome` only when you explicitly
want to use a locally installed Chrome channel.

## Backend Configuration

Shared backend configuration lives in
`backend/src/main/resources/application.yml`. It contains non-secret defaults
and must not activate a Spring profile. Local runs and deployments choose a
profile externally, for example:

```bash
SPRING_PROFILES_ACTIVE=dev mvn -f backend/pom.xml spring-boot:run
SPRING_PROFILES_ACTIVE=prod OPENAI_API_KEY=... mvn -f backend/pom.xml spring-boot:run
```

The supported profiles are:

- `dev` - uses `app.ai.provider-mode=fake` for local development without live
  provider calls.
- `prod` - uses `app.ai.provider-mode=spring-ai-openai` and backend-owned OpenAI
  configuration.

Production AI settings are environment-backed in
`backend/src/main/resources/application-prod.yml`:

- `OPENAI_API_KEY` - required when `prod` uses the OpenAI-backed transcription
  and cleanup adapters.
- `OPENAI_TRANSCRIPTION_MODEL` - optional; defaults to
  `gpt-4o-mini-transcribe`.
- `OPENAI_CLEANUP_MODEL` - optional; defaults to `gpt-5.4-mini`.

Do not commit real provider credentials. Local secret-bearing files such as
`application-dev.local.yml` are ignored by git.

## Test And Provider Behavior

Normal automated tests avoid live provider calls. Backend tests use fake or
mocked transcription and cleanup seams so Cleanup Evaluation Cases can verify
Raw Transcript to Cleanup Result behavior without audio capture, network access,
or provider variability. Frontend tests use mocked API responses for Review
Console workflow states.

Manual provider checks are optional and should be run deliberately. To exercise
the OpenAI-backed path, start the backend with `SPRING_PROFILES_ACTIVE=prod` and
a real `OPENAI_API_KEY`, then submit a short browser recording through the
frontend. That manual check may call paid external services and should not be
required for routine local development or CI.

## MVP Boundaries

The MVP is English-First Cleanup and Plain Text Cleanup. Conservative Cleanup
removes Disfluency Fillers, applies Punctuation Inference, fixes clear Speech
Artifacts, preserves Hedging Language and Literal Phrases, and applies only the
exact standalone Paragraph Command `new paragraph`. Unsupported Spoken Edit
Commands remain content.

Out of scope for the MVP:

- realtime transcription, live partial transcript deltas, WebRTC provider
  sessions, backend-mediated streaming, or streaming API endpoints
- production Web Speech API transcription as the main path
- browser-owned provider keys or direct browser calls to provider APIs
- user-supplied provider API keys
- multiple providers, provider selectors, model selectors, or credential
  controls in the UI
- persistence of audio, Raw Transcript, Cleaned Text, edit history, accepted
  results, exports, history, search, folders, or retention controls
- authentication, accounts, teams, permissions, or multi-user behavior
- deployment targets, infrastructure, and runtime operations beyond the `dev`
  and `prod` profile convention
- Style Actions such as summarize, formalize, shorten, change tone, or convert
  to bullets
- generated Markdown formatting, headings, bullets, bold text, code spans, or
  rich text output
- spoken edit command execution beyond the Paragraph Command

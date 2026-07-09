# smart-AI-text-to-speach-app

## Local Development

This repository has two runnable app surfaces:

- `backend/` - Spring Boot API for `POST /api/dictations`.
- `frontend/` - Vite React Review Console.

Start the backend:

```bash
mvn -f backend/pom.xml spring-boot:run
```

Start the frontend in another terminal:

```bash
npm --prefix frontend install
npm --prefix frontend run dev
```

The frontend dev server proxies `/api` calls to `http://127.0.0.1:8080`.

Run the current smoke tests:

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
```

## Configuration

Shared backend configuration lives in `backend/src/main/resources/application.yml` and does not activate a Spring profile. Choose `dev` or `prod` externally with `SPRING_PROFILES_ACTIVE` when needed.

Production AI settings are environment-backed in `backend/src/main/resources/application-prod.yml`:

- `OPENAI_API_KEY`
- `OPENAI_TRANSCRIPTION_MODEL`, defaulting to `gpt-4o-mini-transcribe`
- `OPENAI_CLEANUP_MODEL`, defaulting to `gpt-5.4-mini`

Local secret-bearing files such as `application-dev.local.yml` are ignored by git.

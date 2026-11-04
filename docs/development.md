# Development

## Local workflow

Run the Spring Boot backend and Angular frontend in separate terminals as described in the [README](../README.md). Use Java 21 and a compatible Node.js runtime. Maven Wrapper downloads Maven on its first invocation; `npm ci` installs the exact versions from `package-lock.json`.

On Windows PowerShell, invoke `mvnw.cmd` and `npm.cmd` explicitly. On macOS/Linux, invoke `sh mvnw` and `npm`. No machine-specific paths or shell-policy changes are required.

The Angular development server proxies `/api` to the local backend. Keep API access in the frontend's typed services so components remain independent of deployment addresses.

After applying the Writing & Communication feature, stop any older backend/frontend processes with `Ctrl+C`, run `npm.cmd ci` (`npm ci` on macOS/Linux) in `frontend`, and restart both servers once. This loads the new Java endpoints and updated frontend dependencies together. Subsequent frontend source edits reload through `npm start`; restart that development server after proxy or dependency configuration changes. Restart the backend after Java/resource changes when running with the standard Maven command.

Demo backend restart resets saved drafts, preset edits, agent changes, and usage because demo storage is in memory. Personal mode persists configuration, private rules, drafts, usage, and conversations in PostgreSQL. Unsaved browser edits remain temporary. Mock account disconnections and email preview/duplicate-attempt state are still temporary infrastructure fixtures; no live email service is connected.

See [private-data setup](private-data.md) for personal PostgreSQL configuration, external `SAI_DATA_DIR`, Windows/macOS instructions, and migration. See [Model management](models.md) for usage estimation and adapter extension. No API keys are needed for local mock startup. Run `node scripts/privacy-audit.mjs` before pushing; local hook setup is documented in [Privacy audit](privacy-audit.md).

## Backend checks

From `backend`:

| Check | Windows PowerShell | macOS/Linux |
| --- | --- | --- |
| Run tests | `.\mvnw.cmd test` | `sh mvnw test` |
| Package with tests | `.\mvnw.cmd verify` | `sh mvnw verify` |
| Run application | `.\mvnw.cmd spring-boot:run` | `sh mvnw spring-boot:run` |

Unit tests should exercise business behavior and provider orchestration. REST tests should verify the HTTP contract, validation failures, and errors. Run affected tests after backend behavior changes.

Writing coverage includes supported/planned workflow selection, independent input/output languages, explicit mock detection, shared generation/preview instruction composition, protected Markdown and preset validation, subject/body preservation, draft revisions and unchanged manual snapshots, recipient provenance, email syntax validation, explicit confirmation, mock connector restrictions, and duplicate-attempt guards. HTTP tests exercise the corresponding JSON contracts and legacy writing-agent aliases. CV/application-package creation remains Phase 2 and is not represented as working generation.

The backend retains JUnit 5 as requested. REST tests start and close a Spring application context programmatically and use MockMvc, avoiding the Spring Test JUnit extension's JUnit 6 requirement in Spring Boot 4.

## Frontend checks

From `frontend`:

| Check | Windows PowerShell | macOS/Linux |
| --- | --- | --- |
| Install locked dependencies | `npm.cmd ci` | `npm ci` |
| Run component and service tests | `npm.cmd test` | `npm test` |
| Build production assets | `npm.cmd run build` | `npm run build` |
| Start the development server | `npm.cmd start` | `npm start` |

Tests use Angular's unit-test builder with Vitest and jsdom. They cover HTTP contracts, loading and connection errors, saved agent configuration, per-agent conversations, keyboard submission, and retrying a failed chat after navigating away and back. Writing tests cover workflow state, replacement confirmations, pending-response/edit guards, saved draft revisions, presets, and email preview restrictions. Export tests exercise actual TXT/PDF/DOCX artifacts and PNG wrapping/pagination; browser Canvas support is additionally relevant for PNG use. No AI account or running backend is needed for these tests.

Backend unit tests also cover rule composition and Markdown metadata, supported document formats and associations, retrieval boundaries, tool registration and authorization, and backward-compatible assignment updates. The frontend editor regression test ensures saves preserve rule, knowledge, and tool assignments. See [Module foundations](modules.md) before adding an upload adapter, retrieval implementation, or executable tool.

Production assets are emitted to `dist/agent-studio/browser`. Serving them requires a host that forwards `/api` to the Java backend, or an explicitly configured API origin. The Angular development proxy is only active during `npm start`.

## Configuration and local data

The backend reads `src/main/resources/application.properties` and the process environment:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_ADDRESS` | `127.0.0.1` | Backend bind address. Keep loopback for local development. |
| `SERVER_PORT` | `8080` | Backend HTTP port. |
| `API_PROXY_TARGET` | `http://127.0.0.1:8080` | Frontend development proxy target; set in the frontend server's environment. |

Use environment variables for machine-specific settings. A `.env` file is not automatically loaded by Spring Boot or Angular; set variables in the process environment or configure your IDE's run configuration.

In-memory agent configuration, writing drafts/presets, mock account state, and email previews reset when the backend restarts. Chat history and per-workflow unsaved writing documents are temporary frontend state. No database migration, OAuth connection, email password, or API credentials are needed for the mock provider. Writing samples do not perform real translation or interpret the Markdown presets; automatic detection is an explicitly labeled heuristic. See [Writing & Communication](writing.md) for the implemented/planned boundaries.

If the backend port changes, set `API_PROXY_TARGET` to match and restart the frontend development server.

The frontend loads `public/config.json` before bootstrapping. Its `apiBaseUrl` defaults to `/api` and is provided through the `API_BASE_URL` injection token. The file contains public configuration only; never put credentials in it. Production builds copy it alongside the application assets, allowing a future desktop wrapper to configure its backend address without changing components. An API on another origin would also need an explicit backend CORS policy; none is enabled in this phase.

Routes use URL hashes so navigating within a future desktop shell does not depend on a web server's history fallback.

## Repository hygiene

- Commit source, Maven Wrapper files, and `frontend/package-lock.json`.
- Keep generated output, dependency directories, local tool installations, and credentials out of Git.
- Use the root `.editorconfig`; `.gitattributes` keeps source and Unix wrapper line endings consistent while preserving Windows command-file line endings.
- Keep controllers small, validate DTOs, and put execution behavior in services.
- Add provider implementations behind `AIProvider`; avoid provider-specific HTTP calls in the agent or chat controllers.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Maven reports an unsupported Java release | Confirm both `JAVA_HOME` and `java -version` point to Java 21. |
| PowerShell blocks `npm.ps1` | Run `npm.cmd` instead. |
| First build cannot download dependencies | Verify network access to Maven/npm repositories and any required proxy configuration. |
| Frontend cannot load agents or models | Check backend health, local port availability, and the frontend proxy target. |
| Agent edits disappear | Configuration is intentionally stored only in backend memory. |
| Saved drafts or preset edits disappear after backend restart | These repositories are intentionally in memory; export important content before restarting. |
| Writing endpoints are missing after updating the source | Stop the old backend process and restart it; restart the frontend once after installing the new locked dependencies. |
| Email Send or Attach is disabled | The connector is mock-only; live delivery and attachments are explicitly planned. |
| Chat output looks templated | The application uses a mock provider; it does not call an AI service. |
| Writing output repeats source text or translation is not accurate | The demo returns fixed language samples and unchanged input; no real translation/model inference runs. |

Report verification blockers explicitly when required runtimes, browser support, network access, or dependencies are unavailable. Do not substitute an unexecuted test command for a test result.

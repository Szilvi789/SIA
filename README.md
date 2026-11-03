# SAI (Szilvi AI)

A personal AI agent platform with chat, configurable agents, and an integrated Writing & Communication workspace. The existing Spring Boot backend and Angular interface retain their rules, knowledge, tool, and interchangeable-provider boundaries. The application uses a local mock provider and requires no AI account or API key. The UI retains its Agent Studio branding.

The repository is rooted directly in this folder.

## What is included

- A responsive Angular Material shell with Dashboard, Chat, Writing, My Agents, Projects, and Settings navigation.
- Chat with agent/model selection, message history, and demonstration responses.
- An agent editor with name, description, system prompt, provider, model, temperature, and maximum output tokens.
- Three canonical agents: Coding, Documentation, and Writing & Communication. The former Translation and Email IDs remain compatible API aliases for the shared writing agent configuration.
- A writing workspace for General Writing, Email, Translation, and Custom Document, with independent English, German, and Hungarian output settings.
- Editable output, independent email subject/body generation, reusable Markdown presets, instruction previews, and revision-checked draft storage.
- Working browser exports of the latest edited document as TXT, PDF, DOCX, and paginated PNG images. Export never calls an AI provider.
- A mock email account selector and exact message preview with live sending disabled.
- Validated REST endpoints, an in-memory repository, and a replaceable `AIProvider` boundary.
- Rules Management: global instructions, ordered agent-specific Markdown assignments, and deterministic instruction composition.
- Knowledge Management: document metadata, agent/project associations, independent storage and retrieval interfaces, and untrusted reference data.
- Tool Management: tool contracts, registry, per-agent enablement, permission requirements, and an authorization policy that blocks execution by default.
- Backend and frontend tests, Maven Wrapper, and an npm lockfile.

All AI output is simulated. Writing samples do not perform real translation or interpret Markdown style instructions; automatic language detection is an explicitly labeled keyword heuristic. Agent changes, saved writing drafts, and edited presets live in backend memory and reset on restart. Unsaved writing documents and conversation history are temporary browser-session state. CV, Cover Letter, and Job Application modes are visibly planned and disabled; creating additional agents is also future work.

## Technologies

| Area | Stack |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.2, Spring Web, Bean Validation, Maven 3.9.9 via Maven Wrapper |
| Backend tests | JUnit 5, Mockito, Spring MVC tests |
| Frontend | Angular 21, TypeScript 5.9, Angular Material, SCSS |
| Document export | Lazy-loaded pdfmake with embedded Unicode fonts, docx, and browser Canvas 2D |
| Storage and providers | In-memory configuration/draft/preset repositories and local mock AI provider |

Exact dependency versions are recorded in `backend/pom.xml` and `frontend/package-lock.json`.

## Structure

```text
.
├── backend/       Spring Boot application and Maven Wrapper
├── frontend/      Angular application and npm lockfile
├── docs/          Architecture, API, and development notes
├── .editorconfig
├── .gitattributes
└── .gitignore
```

The backend separates HTTP DTOs, application services, repositories, and provider implementations. The frontend uses typed HTTP services and relative `/api` URLs. See [architecture](docs/architecture.md) for module responsibilities and extension points.

## Rules, knowledge, and tools

Agents can carry ordered `ruleIds`, `knowledgeSourceIds`, and the existing `enabledTools` alongside their provider/model selection. Older agents default to empty assignment lists. Legacy updates that omit the new lists preserve their current values; explicit empty lists clear them. The existing Angular editor preserves loaded assignments when saving other settings.

At execution, instructions combine application policy, all global rules, the agent prompt, and assigned Markdown rules, in that priority order. Knowledge remains separately attributed, untrusted data. Tool definitions describe capabilities and cannot grant permission to execute them. Default retrieval returns no data, and no production tools are registered.

Packaged Markdown resources provide shared instructions and protected writing safety, workflow, style, and language rules. The writing preset editor stores user-owned Markdown through the existing rule repository, in a separate namespace that cannot overwrite packaged rules. Writing composition places policy and factual accuracy before agent, workflow, style, language, and custom preferences; task input remains separate untrusted data.

Document metadata recognizes Markdown, PDF, TXT, JSON, and CSV. Uploads, real retrieval, and executable tool integrations remain unimplemented. See [Module foundations](docs/modules.md) for the original contracts and [Writing & Communication](docs/writing.md) for the implemented workspace, rule composition, exports, and future integrations.

## Prerequisites

- A Java 21 JDK with `JAVA_HOME` set and `java` available on `PATH`.
- Node.js `22.x` version `22.12.0` or newer, with npm available on `PATH`. Angular 21 also supports `^20.19.0` and `^24.0.0` ([Angular compatibility](https://angular.dev/reference/versions)).
- Internet access for the initial Maven Wrapper and npm dependency downloads.

A separate Maven installation is unnecessary. Keep the committed npm lockfile and use `npm ci` for reproducible dependency installation.

## Run locally

Start the backend and frontend in separate terminals from the repository root.

On Windows PowerShell, start the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

In another terminal, start the frontend:

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

Using `npm.cmd` also works when PowerShell's execution policy blocks the `npm.ps1` shim; changing the execution policy is unnecessary.

On macOS or Linux, start the backend:

```sh
cd backend
sh mvnw spring-boot:run
```

In another terminal, start the frontend:

```sh
cd frontend
npm ci
npm start
```

Open <http://127.0.0.1:4200>. The frontend forwards `/api` requests to <http://127.0.0.1:8080>. The health endpoint is <http://127.0.0.1:8080/api/health>.

Both servers run locally by default. Stop each with `Ctrl+C`.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/health` | Application health. |
| `GET` | `/api/agents` | List configured agents. |
| `GET` | `/api/agents/{id}` | Read one agent. |
| `PUT` | `/api/agents/{id}` | Update an existing agent in memory. |
| `GET` | `/api/models` | List available mock models. |
| `POST` | `/api/chat` | Execute an agent using the mock provider. |
| `GET` | `/api/writing/options` | Read workflow availability, presets, mock accounts, and capability flags. |
| `POST` | `/api/writing/generate` | Generate an explicitly labeled writing sample. |
| `POST` | `/api/writing/preview-instructions` | Preview the same composed instructions used by generation. |
| `GET` / `POST` / `PUT` | `/api/writing/presets[/{id}]` | List, create, or update reusable writing presets. |
| `GET` / `POST` / `PUT` | `/api/writing/drafts[/{id}]` | Read or save edited draft snapshots with revision checks. |
| `GET` / `DELETE` | `/api/writing/email/accounts[/{id}]` | Read or disconnect mock account metadata. |
| `POST` | `/api/writing/email/preview` | Validate an exact email snapshot. |
| `POST` | `/api/writing/email/send` | Validate confirmation; always reject delivery with the mock connector. |

See [API documentation](docs/api.md) for request shapes, validation, and examples.

## Development and testing

From the repository root, run the backend verification on Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd verify
```

Then run the frontend checks in a separate terminal from the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
```

On macOS or Linux, the equivalent commands are:

```sh
cd backend
sh mvnw verify
```

```sh
cd frontend
npm ci
npm test
npm run build
```

Backend tests cover legacy compatibility, writing orchestration and HTTP contracts, rule precedence and protected presets, draft revisions, language settings, mock restrictions, and email confirmation/duplicate guards. Frontend tests cover writing state, edit preservation, typed APIs, and real export artifacts. Production assets are written to `frontend/dist/agent-studio/browser`.

See [development](docs/development.md) for configuration and testing notes and [verification](docs/verification.md) for the checks performed on this implementation.

The current verification environment uses JDK 22 with Java release 21 compilation. Running the application and tests on an actual Java 21 runtime remains unverified; the configured project target is still Java 21.

## Current scope

Agent updates, drafts, preset edits, knowledge metadata, and conversations are volatile. Projects and Settings remain navigation foundations. There is no database, authentication, streaming, real AI integration, document upload/storage implementation, vector database, embedding generation, executable tool access, terminal execution, or native desktop package. Email connectors have no live OAuth or delivery implementation; review checkboxes and generation never authorize sending. The unauthenticated API is intended for local use. Server-verified authorization and durable approval/idempotency storage are prerequisites for external integrations.

## Roadmap

1. Phase 2: factual master professional profiles, CVs, cover letters, job-advertisement input, and reviewed application packages.
2. Phase 2: document attachments and optional recipient assistance using explicitly classified mock data before any live discovery.
3. Phase 3: persistence, authentication, authorized ownership, and document uploads with provider-independent storage and retrieval.
4. Phase 3: real AI providers; Gmail OAuth and other email connectors with encrypted token storage, separate permissions, exact-message confirmation, and durable duplicate prevention.
5. Phase 3: permission-controlled contact/company research and tools, conversation streaming, and user-controlled browser integration. No automated job-portal submission is implemented.
6. Later: backend lifecycle and packaging for a Tauri desktop application.

Never commit API keys or credentials. The mock application needs no secrets.

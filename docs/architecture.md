# Architecture

SAI (Szilvi AI), currently presented in the UI as Agent Studio, is a local application for configuring task-specific agents, chat, and an integrated Writing & Communication workspace. Spring Boot and Angular retain their existing service and API boundaries. Rules, knowledge, and tools remain provider-independent foundations reused by writing. Responses remain explicitly labeled mock output, with agent configuration, presets, and drafts in memory.

## System boundaries

```text
Angular application :4200
        +-- Writing session state --> DocumentExportService --> TXT / PDF / DOCX / PNG
        |
        | /api requests through the development proxy
        v
Spring Boot application 127.0.0.1:8080
        |
        +-- Agent configuration service --> In-memory agent repository
        |           |
        |           +-- Rule / knowledge assignment and tool registry validation
        |
        +-- WritingController
        |       +-- WritingService --> Shared agent / model / provider / knowledge / tool services
        |       |       +-- WritingInstructionComposer --> RuleService + protected core + user presets
        |       |       +-- LanguageDetector --> DemoLanguageDetector
        |       |       +-- ProviderRequest with WritingContext --> AIProvider
        |       +-- WritingPresetService --> Existing RuleRepository + in-memory preset metadata
        |       +-- DraftService --> Revision-checked in-memory draft repository
        |       +-- EmailPreparationService --> EmailConnector --> MockEmailConnector (never sends)
        |
        +-- Agent execution service (existing chat)
                    +-- RuleService --> Application policy + packaged Markdown rules
                    +-- KnowledgeService --> Metadata repository
                    +-- KnowledgeRetriever --> NoOpKnowledgeRetriever
                    +-- ToolRegistry --> Enabled tool definitions (empty by default)
                    |
                    +-- ProviderRequest --> AIProvider --> MockAIProvider

Future explicit tool invocation --> ToolExecutionService --> ToolAuthorizationPolicy
                                                               (denies execution)
```

The frontend uses relative API URLs. The development proxy connects it to the backend without embedding a machine-specific path or a public backend address in application code. Both development servers bind to the loopback interface by default.

## Backend modules

| Package | Responsibility |
| --- | --- |
| `configuration` | Application wiring and configuration shared across modules. |
| `agents` | Agent definitions, validated configuration, repository, service, and REST resources. |
| `chat` | Chat DTOs, request validation, and agent execution orchestration. |
| `models` | Provider/model metadata exposed to the frontend. |
| `providers` | The `AIProvider` abstraction and the mock implementation. |
| `rules` | Trusted Markdown instructions, immutable application policy, assignment validation, and deterministic instruction composition. |
| `knowledge` | Document metadata, explicit agent/project associations, storage/retrieval ports, and untrusted excerpts. |
| `tools` | Registered tool contracts and metadata, per-agent enablement, permission requirements, and an authorization gate that denies execution by default. |
| `writing` | Writing workflows, typed preferences, instruction composition, drafts, presets, and REST endpoints. Subpackages contain email/recipient ports and in-memory/mock infrastructure. |
| `presets` | The initial Coding, Documentation, and Writing & Communication agent definitions. |
| `common` | Shared API error handling and health information. |

Controllers translate HTTP requests into typed DTOs and delegate to services. Services own behavior; repositories own configuration metadata. `AgentService` validates provider/model compatibility, rule assignments, knowledge associations, and enabled tool IDs before saving an update. Failed validation leaves the stored agent unchanged.

`DefaultAgentExecutionService` assembles chat provider requests. `ProviderRequest` keeps three distinct inputs: `ResolvedInstructions instructions`, the user's `message`, and `List<KnowledgeExcerpt> knowledgeContext`. It also carries enabled `ToolDefinition` metadata and an optional structured `WritingContext`; the original six-argument constructor is preserved. Tool definitions describe capabilities; they do not authorize execution. `MockAIProvider` keeps existing chat templates and delegates dedicated writing requests to `MockWritingRenderer`, a fixed-sample infrastructure adapter that does not perform real translation or interpret rules.

Agent configuration includes an ID, name, description, system prompt, selected provider/model, generation parameters, `enabledTools`, `ruleIds`, and `knowledgeSourceIds`. The original constructor defaults both new assignment lists to empty. Existing presets therefore continue working. Responses include the assignments; legacy update requests that omit them or send `null` preserve current assignments, while `[]` clears them. Rule IDs are ordered because their order determines instruction composition.

Only registered tools may be enabled. No production tool implementations are registered, so the default application accepts an empty enabled-tool list and rejects unknown tool IDs.

`AgentIds.canonical` and `InMemoryAgentRepository` preserve `translation-agent` and `email-assistant` as legacy API views of `writing-agent`. The catalog exposes only three canonical agents; alias reads/updates retain the caller's ID while sharing the canonical configuration. Knowledge assignment checks canonicalize agent associations so pre-merge writing references remain recognized. Project-only associations still grant no access.

`WritingService` orchestrates generation independently of providers: it validates workflow availability, resolves compatible models and shared instructions, detects or accepts input language, authorizes knowledge sources, and maps structured provider output. English/German/Hungarian output selection remains independent of input. `GENERAL`, `EMAIL`, `TRANSLATION`, and `CUSTOM_DOCUMENT` are enabled; CV, cover letter, and job application modes are explicitly planned. Email subject/body-only operations preserve the other field exactly.

`WritingInstructionComposer` calls the existing rule service with a protected core layer, then appends workflow, preset/style, language, and custom-preference sections. The same composer powers preview and generation. Editable Markdown uses `RuleRepository.saveUserOwned` under `writing-user-*`; writes to packaged/global/core resources are rejected. Structured preferences remain separate from Markdown, and current task/email/knowledge data never becomes system instructions. Preset repositories store metadata, while the existing rule repository stores their Markdown.

`DraftService` captures the latest manually edited document and original configuration. Its repository uses optimistic numeric revisions, rejecting stale writes without changing stored data. Generation does not depend on draft persistence, and drafts remain `DRAFT` regardless of delivery preview state. In-memory implementations are replaceable without changing AI provider contracts.

The detailed contracts, trust boundaries, and future integration sequence are described in [Module foundations](modules.md).

## Frontend modules

The standalone Angular application separates layout and views from typed API services. Angular Material supplies accessible interface primitives, while SCSS provides the application theme and responsive layout.

The shell provides Dashboard, Chat, Writing, My Agents, Projects, and Settings navigation. The chat view selects an agent/model and submits messages as before. My Agents edits shared in-memory configuration and preserves rule/knowledge assignments. Writing adds the editor, draft library, Markdown preset editor, instruction preview, email fields, and mock preview. General knowledge/tool/upload/approval management remains future work. Projects and Settings reserve space for future features and do not imply database storage, credential management, or tool execution.

API services are the boundary between UI state and HTTP transport. `WorkspaceService` owns catalogs, connection state, per-agent conversations and drafts, and pending chat state. This keeps failed messages recoverable even when the chat view is destroyed during navigation. Backend configuration survives view changes but is lost when the backend restarts. Conversation history is temporary UI state and is not a persistent conversation store.

The API base URL comes from a public runtime configuration file and an Angular injection token. Routes use URL hashes. These choices leave deployment addresses and desktop navigation outside individual view components.

`WritingApiService` supplies typed HTTP contracts. `WritingStateService` keeps a separate unsaved document per workflow across navigation, tracks manual subject/body edits, and binds asynchronous requests to their originating workflow/edit version. Replacement and draft-loading confirmations protect manual edits; revision-aware saves retain updates made while a request was pending. This browser state is lost on reload, while saved drafts/presets last until backend restart.

`DocumentExportService` snapshots the latest edited title, subject, and body independently from generation. TXT uses UTF-8; lazy-loaded pdfmake uses locally embedded Unicode fonts and paginated A4 output; lazy-loaded docx creates editable Word documents; Canvas 2D wraps and paginates PNG pages. The small formatting parser recognizes headings and bullets, keeps other input as plain text, and does not evaluate HTML or fetch remote resources. Safe filenames and clear conversion errors are applied across formats.

## Request lifecycle

1. The frontend loads agent and model catalogs from `/api`.
2. A user selects an agent/model and sends a message.
3. Spring validates the request and resolves the agent, its provider, and the selected compatible model.
4. `RuleService` validates rule assignments and composes immutable policy, sorted global rules, the existing agent prompt, and assigned agent rules in list order.
5. `KnowledgeService` validates that every referenced source exists and is explicitly associated with this agent. Project association alone grants no access.
6. `ToolRegistry` resolves enabled tool definitions and rejects unknown or duplicate IDs.
7. `KnowledgeRetriever` receives the agent, assigned source IDs, and user query. The installed no-op implementation returns no excerpts. The execution service rejects excerpts whose source IDs are absent from the agent's assignments.
8. `AIProvider` receives trusted instructions, the message, separate untrusted knowledge excerpts, and tool metadata. This path never invokes tools.
9. `MockAIProvider` returns a demonstration response, and the frontend appends it to the current conversation.

Configuration updates use a validated REST request so the editor and chat share the same in-memory agent definitions. Errors are returned as structured API responses and shown in the UI.

Writing generation follows the same shared configuration/knowledge/tool boundaries, with `WritingInstructionComposer` and `LanguageDetector` preparing the typed request. The provider returns validated `title`, `content`, and `subject` strings; the application preserves any email field outside the requested generation part. The result updates only the originating editable browser document. Saving drafts, exporting, previewing email, and delivery preparation are independent explicit actions; text generation invokes none of them.

## Email and recipient foundations

`EmailConnector` exposes public account metadata, disconnection, sending capability, and a future delivery method. Its mock adapter supplies two explicitly labeled `example.invalid` accounts, with separate contact/message/draft permissions and no send permission. It has no OAuth connection or tokens and always rejects delivery. ChatGPT email connections do not authorize SAI.

`EmailPreparationService` validates address syntax and stores an immutable exact-message preview. The future send seam requires explicit confirmation, a valid preview, current account send permission, connector support, an idempotency key, and duplicate-preview guards. Preparation states distinguish Draft, Ready to Send, Sending, Sent, and Failed. A mock API preview can be Ready to Send while declaring `sendingAvailable:false`; the Angular preview remains Draft with Send disabled. Attachments are planned and remain empty.

`RecipientLookup` is an optional explicit-action port. `RecipientCandidate` retains independent address/name/role classifications, optional source URL, retrieval context, and timestamp. The installed adapter returns only static Mock-classified invalid-domain fixtures; there is no recipient-search REST endpoint or live discovery UI. Future lookup must check authorized sources and must not present inferred addresses as verified.

These seams do not constitute production authorization. Durable delivery records, authenticated ownership, secure OAuth/token storage, and server-verifiable approvals are required before any real external integration.

## Security and configuration

- The backend binds to `127.0.0.1` by default; it has no authentication layer in this phase.
- External provider calls, terminal execution, unrestricted filesystem access, and background tool execution are absent.
- Packaged rule files are loaded from an explicit classpath allowlist. The preset API can edit only user-owned writing Markdown in a separate namespace, below immutable safety/factual instructions. Uploaded documents, retrieved content, web content, user messages, and tool results are untrusted data and cannot establish rule-authoring authority or operation permissions.
- `KnowledgeExcerpt` has a fixed `UNTRUSTED` classification. Retrieved text never enters the rule-composition API or becomes a system instruction.
- Instruction precedence is an explicit composition convention: earlier sections take priority. A text policy does not guarantee model compliance. The application enforces source associations, tool registration, enablement, and authorization in code.
- `ToolExecutionService` checks registration, per-agent enablement, and the server-owned authorization policy before calling an implementation. The installed policy never returns `ALLOWED`; sensitive capabilities report `APPROVAL_REQUIRED`, and other operations are denied. No approval workflow or grant store exists yet.
- Future sensitive-operation approvals must be verified by the server and bound to the exact agent, tool, arguments, and permitted resources, expire, and be consumed once. Prompt text, external content, and caller-supplied flags cannot grant approval.
- API keys are unnecessary for the mock provider. Future credentials belong in environment variables or a suitable secret store, never committed frontend assets or source files.
- Server configuration is supplied through Spring configuration and environment variables. The frontend uses a local development proxy.
- Request validation limits invalid input before it reaches business logic. This foundation is intended for local development, not public deployment.

## Future desktop integration

The API boundary and platform-neutral code leave room for a future Tauri shell. That phase must decide how the Java backend is bundled and started, how the desktop origin reaches the API, and how application lifecycle and permissions are managed. Tauri itself, desktop packaging, and native filesystem permissions are outside the current implementation.

## Deliberate limits and extension points

Persistence can replace in-memory repositories without changing provider interfaces. `DocumentStorage` has no implementation; future storage adapters must enforce upload limits and inspect the supplied bytes rather than trusting names or declared media types. `KnowledgeRetriever` is the future RAG port; no parser, embeddings, vector database, or actual document retrieval is implemented. Project associations are metadata until project membership and authorization exist.

Real providers can be added behind `AIProvider`, with provider-specific credentials and timeouts, while preserving separate instruction and untrusted-data channels. Tool implementations require explicit resource scopes, validation, isolation, and a real authorization/approval flow before execution is enabled. The local preset editor still needs authenticated rule ownership for multiuser deployment. Conversation persistence, projects, streaming responses, and deployment hardening remain later phases.

Phase 2 adds factual professional profiles, CV/cover-letter creation, job-advertisement inputs, reviewed application packages, attachments, and optional mock recipient assistance. Phase 3 adds authorized AI/email/contact/research integrations, persistence, and permission-controlled tools/browser automation. No fabricated qualifications, company research, live translation, OAuth, delivery, or portal submission is presented as working. See [Writing & Communication](writing.md) for the full Phase 1 scope, [REST API](api.md) for contracts, and [Verification](verification.md) for recorded checks.

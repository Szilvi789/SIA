# Module foundations

SAI (Szilvi AI) extends the existing Spring Boot backend and Angular frontend with rules, knowledge, and tools. These modules supply contracts, metadata, assignment validation, and execution boundaries reused by chat and the integrated Writing & Communication workspace. Writing Phase 1 adds protected Markdown preset authoring and editable drafts without adding live retrieval or executable tools. See [Writing & Communication](writing.md) for its full scope.

## How the modules work together

An agent selects a provider and model, assigns rule IDs and knowledge source IDs, and lists enabled tool IDs. `AgentService` validates those references before storing an update. Chat and writing execution resolve trusted instructions, validate knowledge access, obtain separately classified reference data, and advertise enabled tool metadata to `AIProvider`. Writing adds workflow/style/language composition through the same rule repository and shared security boundaries.

| Provider request field | Purpose | Trust and authority |
| --- | --- | --- |
| `instructions` | Application policy, global rules, agent prompt, and assigned rules; writing additionally composes protected core, workflow, preset, and language layers. | Immutable policy has priority. The local writing preset editor can author lower-priority user Markdown; authenticated multiuser ownership remains future work. |
| `message` | The user's current request. | User input cannot grant tool permissions or change immutable application policy. |
| `knowledgeContext` | Attributed excerpts returned by a retrieval adapter. | Every excerpt is fixed `UNTRUSTED` reference data; its text is never merged into system instructions. |
| `availableTools` | Definitions of tools enabled in the agent configuration. | Metadata advertises capabilities; permission and approval checks still precede execution. |
| `writingContext` | Optional structured writing workflow, preferences, email fields, and language metadata. | Task/email data remains separate from system instructions and cannot approve operations. The original six-argument provider request remains compatible. |

The installed retriever returns no excerpts, the production tool registry is empty, and the mock provider returns demonstration text. Neither chat nor writing calls `ToolExecutionService`. Writing samples do not perform actual translation or apply Markdown semantics.

## Rules Management

The `rules` package contains:

- `RuleDocument` and `RuleScope`: immutable Markdown instruction metadata and content with `GLOBAL` or `AGENT` scope.
- `RuleRepository` and `InMemoryRuleRepository`: provider-independent reads, allowlisted packaged resources, and a `saveUserOwned` seam restricted to user-owned writing preset Markdown.
- `ApplicationSecurityPolicy`: application-owned policy held outside the rule repository.
- `RuleService` and `ResolvedInstructions`: assignment validation and deterministic composition, including an ordered list of applied rule IDs.

The repository loads explicitly allowlisted files under `backend/src/main/resources/rules/`. The original five resources (`global-instructions.md`, `coding-rules.md`, `email-style.md`, `translation-rules.md`, and `documentation-guidelines.md`) remain. Ten writing resources add protected core, general/email/translation workflows, professional/friendly/diplomatic styles, and English/German/Hungarian output instructions. Their exact names and composition are documented in [Writing & Communication](writing.md).

IDs are logical identifiers; request values do not become file paths. Global rules apply automatically. Agent-scoped files may be assigned to multiple agents, and each agent may use multiple files. Canonical agent presets initially have no assigned agent rules; dedicated writing execution always selects its protected writing core separately.

Existing chat instruction sections are combined in this order, with earlier sections declared to take priority:

1. Immutable application security policy.
2. All global rules, sorted by rule ID.
3. The agent's existing `systemPrompt`.
4. Assigned agent rules in the agent's `ruleIds` order.

Unknown IDs, duplicates, and attempts to assign a global rule explicitly are rejected. Rule documents require a plain `.md` filename and nonblank content, limited to 65,536 characters. Composition records applied rule IDs and does not parse Markdown into executable behavior or attempt semantic conflict resolution. Provider adapters must preserve the declared priority. Server-side checks remain responsible for security even if a future model ignores instructions.

Writing composition inserts immutable `writing-core` after global rules and before the agent prompt/assignments. It then appends workflow, preset/style, output-language, and structured custom-preference layers. Task input, email contents, and retrieved knowledge remain separate untrusted data. `WritingInstructionComposer` is shared by generation and instruction preview, so the selected rules and declared precedence are inspectable.

The Angular Writing Presets editor and `/api/writing/presets` endpoints can create/update reusable preset metadata, structured preference defaults, and Markdown. `saveUserOwned` accepts only agent-scoped `writing-user-*` IDs, preserving packaged global/core resources. Presets cannot reference another preset's editable Markdown. This is local, in-memory authoring; general rule-catalog CRUD, rule version history, import/export, and authenticated multiuser ownership remain planned. Rule authors must be authorized separately from people uploading knowledge; a `.md` extension alone does not make uploaded text authoritative. See [REST API](api.md) for the implemented preset and preview contracts.

## Knowledge Management

The `knowledge` package separates document metadata, byte storage, and retrieval:

| Component | Current responsibility |
| --- | --- |
| `KnowledgeFormat` | Allow `.md`, `.pdf`, `.txt`, `.json`, and `.csv`, with canonical media types and extension validation. |
| `DocumentUpload` | Validate declared filename, format, media type, and nonnegative byte count for a future upload flow. |
| `KnowledgeSource` | Immutable source ID, filename, format, opaque storage key, byte count, and associations. |
| `KnowledgeAssociation` | Typed `AGENT` or `PROJECT` target reference. |
| `KnowledgeRepository` / `InMemoryKnowledgeRepository` | Store source metadata; the default repository starts empty and retains no document bytes. |
| `KnowledgeService` | Verify that assigned source IDs exist, are unique, and are explicitly associated with the current agent. |
| `DocumentStorage` | Provider-independent streaming `store`, `open`, and `delete` interface; no implementation is supplied. |
| `KnowledgeQuery` | Agent ID, immutable source references, and retrieval query. |
| `KnowledgeRetriever` / `NoOpKnowledgeRetriever` | Retrieval extension point and installed implementation returning an empty list without storage or network access. |
| `KnowledgeExcerpt` | Source ID and reference text, with application-fixed `UNTRUSTED` classification. |

Storage keys are opaque identifiers and are never interpreted as operating-system paths. Filenames cannot contain directory separators, colons, or control characters. Filename and media-type validation describe the upload claim; they do not inspect file contents or prove safety. A future upload adapter must enforce byte limits, inspect actual bytes, assign storage keys, validate associations, and establish ingestion state before sources become retrievable.

Agent access requires an explicit `AGENT` association. A source associated only with a project is rejected as an agent assignment, including when the project target string happens to equal the agent ID. Project membership, project CRUD, and project authorization are not implemented. Adding project-based access later requires an explicit membership and authorization policy.

The merged writing identity preserves existing agent associations: `translation-agent` and `email-assistant` canonicalize to `writing-agent` when comparing `AGENT` targets. These are API compatibility views of one shared configuration, not separate authorization scopes. Project targets are not canonicalized into agent access.

Queries accept at most 50 unique source references and 12,000 characters of query text, matching the current chat input limit. Excerpts require nonblank text and have a 16,000-character limit each. These bounds are metadata safeguards; a future retriever/provider adapter must also enforce aggregate context and provider token budgets.

Retrieval implementations must authorize access to source assignments and retain source attribution. Chat and writing validate assignments before retrieval and reject any returned excerpt attributed to a source absent from the agent's assignments. Reference text stays separate from trusted instructions even if it contains text such as "ignore previous instructions."

There is no upload endpoint, document parser, document storage adapter, real retrieval, embedding generation, or vector database. A future RAG implementation can replace `KnowledgeRetriever` and use `DocumentStorage` without binding stored documents to a particular AI provider.

## Tool Management

The `tools` package defines application-owned implementations and an explicit authorization boundary:

- `AgentTool` describes a definition and an execution method.
- `ToolDefinition` declares ID, name, description, and required `ToolPermission` values. Permission declarations are requirements rather than grants.
- `ToolRegistry` registers implementations once, rejects duplicate IDs, snapshots their definitions, and resolves enabled definitions in agent configuration order.
- `ToolInvocation` holds a tool ID and immutable argument map. Arguments are untrusted.
- `ToolResult` carries output that remains untrusted reference data.
- `ToolAuthorizationPolicy` returns `ALLOWED`, `DENIED`, or `APPROVAL_REQUIRED`.
- `ToolExecutionService` verifies registration, agent enablement, and the authorization decision before calling an implementation.
- `DenyByDefaultToolAuthorizationPolicy` never allows execution in this phase.

The default registry contains no tool implementations. Agent configuration accepts only registered, unique tool IDs, so the current application uses an empty `enabledTools` list. Web search, file operations, code execution, Git operations, and external API tools are future implementations rather than executable placeholders.

Permission categories anticipate external-content reads, file reads/writes, code execution, Git reads/modifications, and external APIs. The current policy reports `APPROVAL_REQUIRED` for a definition containing a sensitive permission and `DENIED` otherwise. Both decisions stop execution. No approval endpoint, grant store, allow decision, arbitrary command runner, or unrestricted filesystem adapter is installed.

Future callers of `ToolExecutionService` must load the agent from application-owned configuration rather than accepting a client-constructed agent as authority. Tool-specific argument validation, scoped resource access, timeouts, and isolation must be added before enabling an implementation. Any sensitive operation must use a server-verified grant bound to the exact agent, tool, arguments, and resource scope, with expiry and one-use consumption. Caller flags, model responses, documents, tool results, and prompt text cannot approve an operation.

## Backward-compatible agent configuration

Provider/model selection and `enabledTools` retain their existing names. `ruleIds` and `knowledgeSourceIds` extend `Agent`, its response DTO, and frontend types. Lists are defensively copied. The original Java agent constructor remains available and initializes the new lists as empty.

The existing agent update endpoint accepts both assignment lists as optional fields:

| Update value | Effect |
| --- | --- |
| Field omitted or `null` | Preserve that agent's current assignments. |
| `[]` | Clear that assignment list. |
| Nonempty list | Validate and replace the list; rules retain supplied order. |

The existing Angular agent editor preserves assignments it loaded when saving the form. Failed provider/model, rule, knowledge, or tool validation prevents the update from being saved. The canonical catalog contains Coding, Documentation, and Writing & Communication; legacy Translation/Email read/update/chat IDs remain accepted and share canonical writing configuration. Writing preset/draft endpoints and UI are implemented, while general knowledge/tool/rule-catalog management remains planned.

## Future integration sequence

1. Add persistence and authenticated ownership/authoring policies around existing repositories and services.
2. Extend the existing writing preset authoring surface into authorized general rule management as needed; add separate document-upload APIs/views with upload limits and actual byte inspection. Keep knowledge upload authority distinct from trusted rule-authoring authority.
3. Implement provider-independent document storage and parsing, then replace the no-op retriever with a bounded retrieval adapter. Add project membership checks before permitting project-derived access.
4. Add real provider adapters that preserve instruction, user-input, and untrusted-reference boundaries and enforce context budgets.
5. Add narrowly scoped tool adapters and server-owned permissions/approval grants before connecting a controlled tool-call loop to chat.

Agent/knowledge metadata, writing presets, and saved drafts are temporary in-memory state. There is no authentication, persistent document store, production AI integration, RAG implementation, general sensitive-operation approval workflow, or tool execution integration in chat/writing. Email has exact-preview/confirmation/idempotency preparation seams and a mock connector that never sends; live authorization and durable delivery remain planned. These foundations describe integration boundaries; they do not guarantee a future model will obey a textual policy.

# Model management and runtime selection

SAI extends its existing `ModelCatalog` and `AIProvider` boundary. A **provider** is an execution adapter (including its server configuration); a **model configuration** is a stable registry ID, an actual API model ID, and model-specific metadata. Several configurations may reference one provider. Changing a model's display name or API ID does not change its registry ID.

## Configure models and defaults

Open **Settings** to add or update models, disable providers/models, configure application defaults and tier mappings, and inspect usage per agent/model. Disabled entries retain references and usage history. Creating registry metadata does not install or connect an API adapter.

Metadata includes capabilities, optional context/output limits, supported parameters and reasoning values, optional token prices, currency, token pricing unit, and pricing timestamp. Unknown values remain unknown. Enter only verified values from the actual configured model and adapter. Capability declarations do not prove API compatibility; a future adapter must translate and validate the exact provider request.

**Economy**, **Balanced**, and **Advanced** are SAI presets. Global mappings point to enabled registry configurations; each agent can override these mappings. The seeded mappings select the existing Mock Precise, Mock Balanced, and Mock Creative demonstration templates. They make no claims about intelligence, latency, or market pricing. Remove a mapping to leave that tier unconfigured; attempting to use it produces a configuration error.

Open **My Agents** to choose an independent provider/model default, optional default tier, per-agent tier mappings, optional fallback and permission, model-specific generation parameters, and a request cost guardrail. A default tier takes precedence over the saved default model; clearing it restores direct model selection. Omitted `modelSettings` in a legacy update preserves the stored settings, just as omitted rule and knowledge assignments do. Updating one agent does not update others. Legacy Email and Translation agent IDs still refer to the shared Writing agent.

The editor shows only generation controls declared by the selected model. Supported controls can include maximum output tokens, temperature, top-p, reasoning effort, timeout, and permitted tools. Legacy temperature/output settings remain compatible; unsupported legacy controls are omitted from provider requests. Explicit new controls unsupported by the effective model fail validation, including when a temporary override is chosen. Token count and temperature are generation controls, not intelligence levels. Mock controls are demonstration configuration; there is no inference, real tool calling, or model reasoning.

Writing requires text and structured-output capabilities because its provider contract returns title, content, and subject. Chat requires text. Tool permissions can only narrow the agent's enabled tools; they cannot authorize execution. The existing deny-by-default tool authorization remains in place.

## Change the model during work

Chat and Writing share a compact selector with an agent-default option, three tiers, advanced selection of a specific registry model, provider/capability details, pricing information, and **Restore default**.

- A Chat override stays with the current conversation for that agent. Switching agents preserves each conversation's override. **New conversation** clears its override and creates a fresh usage correlation ID.
- A Writing override belongs to the document in that writing mode for this browser session. Switching writing modes preserves their separate selections. Saving a draft captures the effective configured model; opening it restores that model as an explicit task selection. **Restore default** follows the Writing agent's current configuration. Chat selection never supplies the Writing default.
- Neither action updates saved agent defaults. Browser reload clears temporary conversation/document state.

The backend resolves every request in this order:

1. Explicit request `modelId`, or request `tier`.
2. Task/conversation `overrideModelId`, or `overrideTier`.
3. Agent `modelSettings.defaultTier`, then agent `modelId`.
4. Application `defaultModelId`.

A tier uses the agent mapping first, then the global mapping. Missing, unknown, disabled, incompatible, or unsupported choices fail before generation. Selecting a specific model may select another provider; the provider comes from the resolved registry entry. The server sends the effective model and validated parameters through `ProviderRequest`, and returns `providerId`, `modelId`, `source`, and `execution` metadata. Execution metadata also identifies the API model ID, mock status, fallback status, and usage record.

Fallback requires an explicitly configured fallback model and saved permission; a request can additionally forbid it. The resolver validates fallback capabilities and parameters. It refuses a fallback whose price is unknown or higher than the requested model (mock fallback has no API charge). Provider failures that might already have incurred a charge must not be retried automatically. Select a different model explicitly when automatic fallback cannot establish safe cost compatibility.

## Usage, prices, and guardrails

Usage records contain agent, workflow, conversation/task correlation when supplied, timestamp, resolved provider/model, request status, mock classification, nullable input/output/cached token counts, and nullable estimated cost. They contain no prompts, generated content, or credentials. Each record retains a pricing timestamp and rate snapshot so subsequent price edits do not rewrite past estimates. Decimal arithmetic is used for stored estimates.

Real token counts must come from the adapter's provider response. SAI does not estimate tokens from character counts. Mock calls have unknown measured tokens and unknown billing estimates and are explicitly marked as mock. Their execution makes no external API call.

When actual token usage and complete pricing are available, estimated cost is `(input tokens × input price + output tokens × output price) / pricing unit`. Cached input prevents a reliable estimate when its price is unknown. Usage summaries keep currencies separate and retain unknown totals when some contributing values are unknown. Estimates are not provider billing statements.

The selector can show configured prices and an output-allowance estimate using the output cap; input cost remains additional. A complete request estimate is unavailable before token usage is known. An agent cost guardrail rejects a real-provider request with an unknown preflight estimate unless **Allow unknown cost** is explicitly enabled. That permission is not a guaranteed spending cap. Daily/monthly limits, tokenization-based preflight estimation, cached-token pricing, and provider billing reconciliation remain future work.

## Providers and credentials

Only `MockAIProvider` is installed. OpenAI, Anthropic, and Local appear as provider configuration targets with unavailable adapter status. Enabling a target or adding a model does not make external requests work. No API request is made without an installed, enabled, configured adapter. The mock provider works without credentials, and disabling it blocks execution unless permitted fallback applies.

There is no API-key input or credential endpoint. Future adapters should read keys from environment variables or an appropriate server secret store, implement `configured()`, and remain unavailable until explicitly configured. Do not put keys in model metadata, ordinary database tables, frontend runtime configuration, bundles, logs, or REST responses. A ChatGPT subscription or connected ChatGPT account supplies no reusable API credentials to SAI. No real provider integration or connection probe is claimed by this release.

`AIProvider.generateResult` adds nullable provider usage while preserving existing `generate` adapters. A future adapter uses `request.model().apiModelId()` and `request.parameters()`, translates supported parameters, preserves the separate instruction/input/knowledge channels, and reports actual usage. Model declarations never replace adapter-specific API validation.

## Storage by runtime mode

Demo mode uses the temporary in-memory repositories and requires no database. Personal mode replaces the same repository interfaces with PostgreSQL implementations, additive Flyway migrations, private credential-file configuration, and a persistent Docker volume outside the checkout. Models, provider flags, defaults, tiers, usage snapshots, agent configuration, private rules, drafts, and conversations survive personal-mode restarts. Synthetic seeds never overwrite user edits.

Personal filesystem data uses an external `SAI_DATA_DIR` base with a separate mode directory. Provider-specific data-sharing permission and per-request consent are required for future cloud adapters; local retrieval does not authorize external transmission. See [Public source and private data](private-data.md) for setup, migration between Windows/macOS, and boundaries. Saved conversation history does not automatically become a multi-turn provider prompt.

## API examples

`PUT /api/models/{id}` creates or replaces a full model configuration. `GET /api/models/{id}` reads it; `GET /api/models` lists all entries, including disabled ones. Disable entries rather than deleting references.

`GET`/`PUT /api/model-settings` reads or updates global defaults:

```json
{
  "defaultModelId": "mock-balanced",
  "tierMappings": {
    "ECONOMY": "mock-precise",
    "BALANCED": "mock-balanced",
    "ADVANCED": "mock-creative"
  }
}
```

Tier names are case insensitive and returned in uppercase. `GET /api/providers` and `GET /api/providers/{id}` expose public status. `PUT /api/providers/{id}` accepts `{"enabled":false}`. `GET /api/usage` returns records and `GET /api/usage/summary` returns per-agent/model/provider/currency groups.

The normal Chat request follows the saved agent default:

```json
{"agentId":"coding-agent","message":"Review this approach."}
```

A conversation override changes only this request's task context:

```json
{
  "agentId":"coding-agent",
  "message":"Review this approach.",
  "overrideTier":"BALANCED",
  "conversationId":"my-current-conversation",
  "allowFallback":false
}
```

Writing generation accepts the same selection fields alongside its existing workflow, input, preferences, and email payload. An empty or omitted `modelId` follows the Writing agent default.

## Windows PowerShell

Use two terminals from the repository root, with Java 21 and supported Node.js on PATH:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd start
```

Open `http://127.0.0.1:4200`. No keys or provider accounts are required for the mock demo. Run `.\mvnw.cmd verify` in `backend`, and `npm.cmd test` plus `npm.cmd run build` in `frontend` to verify changes.

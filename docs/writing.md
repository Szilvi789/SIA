# Writing & Communication

Storage is mode-specific: demo drafts/presets are temporary; personal drafts and user-owned Markdown persist in private PostgreSQL. Personal exports also retain a copy under the external private generated directory before browser download. Cloud request consent is separate from email delivery authorization. See [Public source and private data](private-data.md); in-memory descriptions below apply to demo mode.

Phase 1 adds one integrated writing agent to the existing SAI application. It uses the same agent configuration, rule repository, knowledge boundary, tool registry, model catalog, and interchangeable `AIProvider` interface as chat. It does not create seven separate agents or require a live AI/email account.

## Implemented workspace

Open Writing in the navigation. General Writing, Email, Translation, and Custom Document are usable workflows. CV / Résumé, Cover Letter, and Job Application Package are shown as planned and cannot generate content yet.

The workspace provides a multiline task editor, editable output, document title, Copy, Regenerate, Save Draft, and format selection for exports. Writing settings include reusable presets, input/output languages, translation behavior, formality, tone, length, audience, purpose, and custom instructions. Advanced settings remain separate from the main editors. English, German, and Hungarian output can be selected independently of the input language.

Email adds a mock From account selector, recipient name/address, CC, BCC, and independently editable subject/body. Subject-only generation preserves the exact supplied body; body-only generation preserves the exact subject. Complete generation may replace both. The preview shows the exact sender, recipients, subject, body, and absence of attachments. Send and Attach controls are disabled and labeled planned. Recipient assistance is also visibly planned; no contact, correspondence, or website search occurs.

The mock provider returns fixed, clearly labeled writing samples in the selected language and includes the original input unchanged. It does not actually translate, research, personalize prose, or interpret the Markdown rules. Automatic detection is a small replaceable keyword heuristic that can return Unknown or Mixed and supplies no invented confidence score. A manual input-language override is displayed as user-selected. These limitations are visible in the workspace.

## Agent compatibility

The canonical catalog now contains `coding-agent`, `documentation-agent`, and `writing-agent`. Translation and Email are workflows of the writing agent.

`translation-agent` and `email-assistant` remain accepted by the existing agent read/update/chat endpoints. `InMemoryAgentRepository` canonicalizes storage and projects the requested legacy ID in the response. Updating any writing alias changes the same canonical configuration. Legacy chat calls retain their original mock response templates; dedicated writing requests carry a structured writing context.

Knowledge-source associations for either legacy writing ID resolve to the canonical writing identity. Project associations still require a separate future authorization policy and grant no agent access today. The frontend normalizes legacy agent choices to writing while retaining the existing workspace and configuration contracts.

## Backend responsibilities

| Component | Responsibility |
| --- | --- |
| `WritingController` | Typed options, generation, instruction preview, preset, draft, and email preparation endpoints. |
| `WritingService` | Workflow validation, model/provider resolution, detection, instruction composition, authorized knowledge context, provider call, and response mapping. |
| `WritingInstructionComposer` | One deterministic composer shared by generation and preview. |
| `WritingPreferences` / `WritingContext` | Structured settings and separate untrusted task/email context. |
| `LanguageDetector` | Replaceable detection port, implemented by `DemoLanguageDetector` for now. |
| `WritingPresetService` / `WritingPresetRepository` | Reusable preset configuration, applicability checks, and in-memory metadata. |
| `DraftService` / `DraftRepository` | Editable snapshots, original task configuration, and optimistic revision checks. |
| `MockWritingRenderer` | Infrastructure-only fixed demo samples; `MockAIProvider` delegates to it when writing context is present. |
| `EmailConnector` / `EmailPreparationService` | Public account metadata, independent permissions, exact previews, confirmation checks, and delivery/duplicate-prevention seams. |
| `RecipientLookup` / `RecipientCandidate` | Optional future discovery and provenance contracts; only a static mock fixture is implemented. |

`ProviderRequest` adds optional `WritingContext` while preserving its original six-argument constructor. Existing chat callers continue supplying instructions, message, knowledge excerpts, and tool definitions as before. Writing providers receive the task separately and return structured `title`, `content`, and `subject` strings. The writing service validates that response and preserves ungenerated email fields. Provider adapters own provider-specific transport; workflow rules remain in application/domain code and Markdown configuration.

`WritingService` validates source assignments using `KnowledgeService`, retrieves through the existing no-op `KnowledgeRetriever`, rejects unassigned returned sources, and advertises only registered enabled tools. It does not execute tools. Generation has no dependency on draft persistence or delivery: producing text cannot save over a draft or send email.

## Markdown rules and reusable preferences

Packaged allowlisted Markdown remains under `backend/src/main/resources/rules/`; the existing rule system is extended rather than duplicated. Writing resources are:

```text
writing-core.md
writing-general.md
writing-email.md
writing-translation.md
writing-style-professional.md
writing-style-friendly.md
writing-style-diplomatic.md
writing-language-en.md
writing-language-de.md
writing-language-hu.md
```

The composer uses this precedence, with earlier sections winning conflicts:

1. Immutable application security policy.
2. All global rules, sorted by ID.
3. Immutable writing core: factual accuracy, privacy, untrusted-data handling, and sensitive-operation constraints.
4. Saved agent prompt and assigned agent rules in assignment order.
5. Selected workflow instructions.
6. Selected preset rules and editable preset Markdown, followed by structured style preferences and selected additional rules.
7. Output-language instructions.
8. Structured task preferences: translation behavior, formality, tone, length, audience, purpose, and custom instructions.

The current task, pasted source, email field values, and retrieved documents remain separate provider data. They are not a final system-instruction layer. Preview and generation use exactly the same composer and return the applied rule IDs. Duplicate rule IDs are included once in the writing composition.

The Writing Presets tab can create/edit a reusable preset, enable/disable it, choose its workflow, assign existing rule IDs, edit Markdown, and store structured preference defaults. The initial presets are Professional, Friendly Email, Diplomatic Email, and Natural Translation. The instruction preview reflects the currently selected workspace configuration; save and select an edited preset before previewing its effect.

Preset Markdown is stored by the existing `RuleRepository.saveUserOwned` seam as `writing-user-{presetId}`. The repository rejects writes outside this namespace or to global scope. Packaged rules and application policy are never writable through the preset API. Presets cannot reference another preset's editable Markdown. Rule IDs, names, text, and preference sizes are bounded and validated. Markdown is configuration text, never script/code execution or rendered active HTML.

Structured defaults and Markdown remain separate: language, tone, formality, and length use typed fields, while detailed style guidance lives in Markdown. The frontend copies reusable defaults into the request so the final chosen structured settings remain explicit. A `GENERAL` preset is accepted across supported workflows by the backend; workflow-specific presets are restricted to their own workflow. General rule-catalog management, import/export of preset definitions, and authorized multiuser rule ownership remain future work.

Textual precedence is an instruction convention, not a guarantee that a future model will comply. Server code must continue enforcing knowledge access, permissions, and sensitive operations independently of any generated text.

## Drafts and edit preservation

Each supported workflow retains a separate unsaved document in the Angular session service. Switching workflow or navigating away does not silently discard these documents. Loading a saved draft over unsaved edits requires confirmation. Regeneration requires confirmation before replacing the relevant manually edited subject/body. Pending operations are bound to their originating workflow and edit version so late responses cannot overwrite newer work.

Save Draft sends the latest edited content and subject alongside the original task, workflow, model, preferences, and email fields. The backend assigns an ID and timestamp and starts at revision 1. Updates require the current revision; stale/missing revisions are rejected without changing the saved snapshot. Generation never updates a draft automatically. Draft status remains `DRAFT` independently of delivery preparation.

Drafts, preset edits, mock account disconnections, email previews, and duplicate-attempt records are in memory. Backend restart resets them. Browser reload clears unsaved writing documents and chat history. There is no deletion endpoint, database, multiuser ownership, or synchronization store yet.

## Working document exports

`DocumentExportService` is frontend-only and independent of AI providers. Export captures the latest title, subject, and manually edited document before loading any converter; it never regenerates text or substitutes an older generated result. The browser downloads the returned artifact(s).

| Format | Implementation and scope |
| --- | --- |
| TXT | UTF-8 plain text containing the selected title/subject and exact edited body. |
| PDF | pdfmake with locally bundled Roboto Unicode fonts, automatic A4 pagination, title/subject styles, headings, bullets, and paragraphs. |
| DOCX | docx builds a Word document with title/heading styles, bullets, subject, and paragraphs. |
| PNG | Canvas 2D lays out readable text on 1240 × 1754 pixel pages with wrapping and page numbers; long documents produce separate numbered images. |

PDF/DOCX converters and PDF fonts are loaded lazily when needed. PDF and DOCX preserve a deliberately limited Markdown subset: headings beginning with one to three `#` characters and simple `-`, `*`, or `+` bullets; other content remains plain text. No HTML is evaluated, and exports fetch no remote images, fonts, or URLs. Filenames preserve useful Unicode text while removing path separators, control characters, reserved device names, and unsafe punctuation; empty titles use `sai-document`.

Formatting is a document baseline, not a full rich-text/Markdown editor or CV template engine. PNG requires Canvas 2D and browser fonts; glyph coverage and rendering can vary by platform. Multiple PNG pages are separate downloads rather than a ZIP archive, and browser multiple-download policies may apply. Conversion failures are reported instead of creating misleading downloads. Exported documents do not become email attachments automatically.

## Email and recipient integration boundaries

`MockEmailConnector` supplies two reserved `example.invalid` identities labeled Mock. They are not authorized accounts. The connector exposes public metadata and independent `READ_CONTACTS`, `READ_MESSAGES`, `MANAGE_DRAFTS`, and future `SEND_MESSAGES` permissions. Mock accounts lack send permission, and the connector refuses delivery under all circumstances. The backend supports mock disconnection; the current composer selects accounts and previews draft data without a connection-management UI.

`EmailPreparationService` can validate recipient syntax and a connected account, then create an immutable preview containing the exact sender, To/CC/BCC, subject, body, and empty attachment list. A validated API preview can be `READY_TO_SEND` while still reporting `sendingAvailable:false`. The current UI keeps the message Draft and disables Send. Generation and the review checkbox never invoke sending.

The future send seam requires explicit confirmation of an existing preview, a valid idempotency key, current account connection and send permission, connector support, and guards against repeated keys or repeated preview delivery. No successful mock send result is manufactured. These in-memory guards are architecture for a future adapter; production sending also requires authenticated user authorization, secure OAuth token storage, durable delivery records, and a recovery strategy for uncertain results.

`RecipientCandidate` separates address, name, and role classifications: Verified, Previous Correspondence, Publicly Published, Unverified, or Mock. It can retain a source URL, retrieval context, and timestamp. `MockRecipientLookup` requires explicit user action and returns only a static `example.invalid` fixture with all identities classified Mock. It searches no contacts, correspondence, documents, or websites, and has no REST endpoint or working search UI in Phase 1. Future lookup must never label inferred identities as verified or silently choose ambiguous candidates.

Live Gmail and other providers must use official authorization flows and encrypted server-side token storage. Plaintext passwords and tokens are absent from frontend models, drafts, source files, and mock accounts. ChatGPT's email connections are unrelated to SAI and confer no authorization here.

## Planned work

Phase 2 follows verification of this workspace:

- Reusable factual master profiles with missing-information handling, CV sections and ordering, and job-advertisement tailoring.
- Cover letters grounded in supplied qualifications and verified company information.
- Reviewed application packages containing independently editable CV/letter documents, exports, and optional email attachment preparation.
- Document attachments and optional recipient suggestions with clear mock/provenance labels before live providers exist.

Phase 3 supplies explicitly authorized external integrations:

- Real AI provider and language-detection adapters preserving instruction/data boundaries.
- Gmail OAuth and provider-independent email adapters with separate permissions, encrypted secrets, explicit delivery confirmation, and durable duplicate prevention.
- Authorized contact lookup, correspondence search, and company research with source attribution.
- Persistent repositories, authentication and ownership, bounded document storage, and future RAG adapters.
- Permission-controlled tools and browser-agent integration; automated job-portal submissions remain outside this iteration.

No CV facts, qualifications, company research, real recipient addresses, OAuth connection, or successful delivery should be inferred from the current mock fixtures.

## Local validation

Run from the repository root in separate Windows PowerShell terminals:

```powershell
cd backend
.\mvnw.cmd verify
```

```powershell
cd frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
```

On macOS/Linux use `sh mvnw verify` from `backend`, and `npm ci`, `npm test`, `npm run build` from `frontend`. A Java 21 JDK is the configured target; consult [Verification](verification.md) for the runtime used and recorded results. API shapes are documented in [REST API](api.md), and broader module boundaries in [Architecture](architecture.md).

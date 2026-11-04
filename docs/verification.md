# Implementation verification

## Public repository and private data separation — 2026-11-04

| Check | Result |
| --- | --- |
| Backend Maven `verify` | Passed: 211 discovered, 205 passed, 6 skipped; executable JAR packaged. Four skips require Windows symlink privileges; two are opt-in live PostgreSQL cases run separately below. |
| Live PostgreSQL 17.11 | Both integration tests passed against an isolated synthetic-only database: repository/pool restart, full personal application shutdown/restart, exact personalized Markdown/draft retention, synthetic seed preservation, revision conflicts, private history/permission storage. |
| Frontend tests and production build | 101 tests passed across 13 files; production build passed. Includes mode-change blocking, restored personal history, request consent, private-export transport, and existing writing/export functionality. |
| Local privacy protections | 20 tests passed using real pinned Gitleaks; pre-commit and pre-push hooks installed. Full tracked/index/worktree/history audit completed with zero findings. |
| Clean public source startup | Isolated local clone plus the current public working-copy overlay rebuilt and passed demo startup without database credentials, datasource, private files, dependency directories, or cached application artifacts in that clone. Changes remained uncommitted. |
| Storage and configuration | Tests cover external defaults, absolute-path validation, mode namespaces, worktrees/repository containment, opaque identifiers, bounded validated file bytes, private multipart locations, cloud locality/permission/source consent, and loopback enforcement. |

Verification used Windows, JDK 22.0.2 targeting release 21, Node.js 22.13.0, cached Maven 3.9.9, Docker PostgreSQL 17.11, Gitleaks 8.30.1, and pre-commit 4.7.0. Dependency downloads used normal TLS verification through the existing proxy/trusted Windows roots. No commits, pushes, history rewrites, or existing application-data deletions were performed. The stopped synthetic validation container/volume was retained as `sai-privacy-validation-872e60a8bfdb`; its temporary clone and credential file were also retained. Docker Desktop remains running after validation.

Managed Windows policy denied ACL changes; owner restriction falls back to inherited ACLs with a generic warning. Four filesystem symlink tests could not run under this account. Actual macOS execution and Java 21 runtime remain unverified. Secret scans cannot reliably identify all unmarked personal prose; local hooks can be deliberately bypassed. Personal API access is loopback-only and unauthenticated; browser downloads require a private Save As destination. Real AI/email adapters and automated backups remain future integrations. See [Private data](private-data.md) and [Privacy audit](privacy-audit.md).

The following sections retain earlier implementation results.

## Model management and runtime selection — 2026-11-04

| Check | Result |
| --- | --- |
| Backend Maven `verify` | Passed: 162 tests, zero failures/errors/skips; executable JAR packaged. |
| Frontend `npm.cmd test` | Passed: 91 tests across 11 files. |
| Frontend `npm.cmd run build` | Passed without budget warnings; document export converters remain lazy-loaded. |
| Runtime integration | HTTP tests verify independent Coding/Documentation/Writing defaults, temporary overrides, cross-provider dispatch, explicit API model IDs, uppercase tier mappings, legacy aliases, and effective response metadata. |
| Validation and fallback | Tests cover registry edits/disablement, unknown metadata, missing adapters/configuration, unsupported parameters/capabilities, explicit fallback permission, rejection of higher/unknown fallback prices, safe pre-dispatch fallback, and no retry after an ambiguous provider failure. |
| Usage and compatibility | Tests cover reported token accounting, timestamped decimal price snapshots, unknown mock usage, separate currency summaries, failure classification, preserved agent settings, and existing writing/export/email behavior. |

Verification used JDK 22.0.2 with Java release 21 compilation, Maven 3.9.9, and Node.js 22.13.0. The Maven Wrapper bootstrap was blocked by proxy authentication (HTTP 407); the existing cached Maven distribution and dependency repository were used offline for the successful `verify`. Angular verification required permission to launch its local esbuild/test workers. An actual Java 21 runtime, real API providers, and PostgreSQL remain unverified/unimplemented. No dependency versions changed. See [Model management](models.md) for the scope and persistence boundaries.

The following results describe earlier implementation stages.

Checks performed on Windows on 2026-11-03:

## Writing & Communication — Phase 1

| Check | Result |
| --- | --- |
| Backend `mvnw.cmd verify` | Passed; executable Spring Boot JAR packaged. |
| Backend tests | 139 passed, zero failures, errors, or skipped tests. Includes existing behavior, legacy agent aliases, writing HTTP contracts, composition, presets, drafts, and email restrictions. |
| Frontend `npm test` | 75 passed across nine files, including writing state/API/UI checks and real PDF/DOCX artifact tests. |
| Frontend `npm run build` | Passed without warnings; PDF/DOCX converters and PDF fonts remain lazy-loaded. |
| Live writing workspace | Browser generated a German mock sample from Hungarian input, detected Hungarian with a mock label, and saved manually edited output through the real draft API. |
| Edit preservation | Regeneration requested confirmation; cancelling retained the manually edited document. |
| Real browser exports | TXT, PDF, DOCX, and PNG downloaded successfully; signatures checked and TXT contained the latest edited text. Actual Canvas PNG rendering inspected. |
| Email boundary | Exact preview displayed a mock `example.invalid` sender; Send remained disabled. |
| Desktop/mobile browser | Screenshots inspected; 390 px mobile viewport had no horizontal overflow. No JavaScript exceptions observed. |

Verification used JDK 22.0.2 compiling for Java release 21 and Node.js 22.13.0. Running on an actual Java 21 runtime remains unverified. Checks used dedicated local preview ports, separate from the normal development servers. No real AI provider, real translation, external account connection, or email delivery was exercised. Drafts and presets remain volatile in-memory state.

The following sections retain results from earlier implementation stages; their counts describe those stages rather than the current suite.

## Earlier rules, knowledge, and tools extension

| Check | Result |
| --- | --- |
| Backend `mvnw.cmd verify` | Passed; executable Spring Boot JAR packaged. |
| Backend tests | 99 passed, zero failures, errors, or skipped tests, including the existing API/provider behavior checks. |
| Frontend `npm test` | 33 passed across four files, including all existing tests and a regression test for preserving module assignments. |
| Frontend `npm run build` | Passed without budget warnings. |
| Compatibility | Legacy agent constructors and update bodies work; omitted/null assignments are preserved and explicit empty lists clear them. |
| Security boundaries | Tests verify deterministic trusted-rule composition, untrusted excerpt isolation, source-association checks, no automatic tool invocation, and denial of unauthorized or pending-approval tool calls. |

No dependency versions were changed in that extension. Backend verification used the pinned wrapper and cached dependencies with JDK 22.0.2 compiling for Java release 21; frontend verification used Node.js 22.13.0. An actual Java 21 runtime remained unverified. That extension changed module contracts without changing the UI layout.

## Original application skeleton

| Check | Result |
| --- | --- |
| Git repository | Initialized at the workspace root on `main`; no initial commit created. |
| Backend `mvnw.cmd verify` | Passed; executable Spring Boot JAR packaged. |
| Backend tests | 26 passed, zero failures, errors, or skipped tests. |
| Maven Wrapper | Official wrapper 3.3.4 launches pinned Maven 3.9.9; distribution SHA-256 pinned. |
| Live backend HTTP | Health returned `UP`; four agents, three models, and an explicit mock chat response verified. |
| Frontend `npm run build` | Passed; production assets generated without budget warnings. |
| Frontend `npm test` | 32 passed across four component/service test files. |
| Frontend `npm ci` | Passed using the normalized public-registry lockfile and cached packages. |
| Live frontend and development proxy | Page and runtime configuration returned HTTP 200; proxied health, agent/model catalogs, and chat succeeded. |
| Desktop browser | Headless Microsoft Edge completed a chat through the real UI and backend; no JavaScript exceptions observed. |
| Mobile browser | Chat rendered at 390 px width without horizontal overflow or JavaScript exceptions; screenshot inspected. |

The backend compiles with Java release 21. The available verification runtime was JDK 22.0.2; running the application and tests on an actual Java 21 JDK remains unverified. Downloading Java 21 was blocked by the machine's corporate proxy.

Dependency access used the machine's configured Maven mirror and Windows certificate store. The Maven distribution was downloaded through authenticated Maven access, checked against the pinned SHA-256, and placed in an ignored local wrapper cache. A fresh public-network wrapper download was blocked in this environment. Corporate addresses and credentials are not part of the repository.

Frontend checks used Node.js 22.13.0. Initial dependency resolution crashed in the installed npm 11.3.0; generating the lockfile succeeded with npm 11.21.1 installed in an ignored workspace cache. A subsequent clean `npm ci` with the original npm 11.3.0 succeeded. Lockfile URLs use the public npm registry and retain package integrity hashes.

The original backend and frontend were run together for verification, then stopped. macOS/Linux execution and native desktop packaging were not tested. At that stage, agent configuration remained in memory and conversations/chat drafts remained in browser memory. Every response came from the mock provider. Current writing draft storage is described above and in [Writing & Communication](writing.md).

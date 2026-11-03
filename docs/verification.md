# Implementation verification

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

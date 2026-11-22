# Mod update test status

The app uses the existing update pipeline: live marketplace metadata is required for downloads; the API origin must be HTTPS for installation; downloaded identity and SHA-256 are checked before the trusted `.doommod` importer reviews the archive; update requires explicit confirmation and a disabled mod. The importer uses a journaled atomic file replacement and Room transaction, leaves the mod disabled, and recovery compares recorded old/new hashes. Existing settings/grants are preserved; newly declared capabilities remain subject to runtime declarations and approval.

The Android instrumentation suite already contains `ModImportTest.updateJournalRestoresOldOrKeepsCommittedNewPackage` and `compatibleUpdatePreservesSettingsAndCapabilityGrants`. Those instrumentation tests were not executed in this task. Unit/build results do not substitute for these lifecycle tests.

| Update case | Result |
|---|---|
| Compatible update, settings preservation, interrupted journal recovery | **PASS (instrumented module fixtures on A001)** — `:core-data:connectedDebugAndroidTest` ran 22 tests, 0 failures/errors/skips. `verifiedUpdatePreservesSettingsAndGrants` and `updateJournalRestoresOldOrKeepsCommittedNewPackage` passed using isolated databases/test fixtures. |
| Schema/checksum/truncation/cancellation failures | **PARTIAL** — existing importer tests were included in the 22-test instrumentation run; no live marketplace download interruption or corrupt server artifact was exercised. |
| Duplicate version, new capability, incompatible host, database failure, process kill | **NOT VERIFIED in this task** — retain as outstanding cases and add isolated tests before claiming full coverage. |
| Marketplace quarantine / moderation and real HTTPS update | **BLOCKED** — backend services unavailable in this task environment. |

No update behavior was weakened and no test mod was published.

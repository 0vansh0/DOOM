# P0/P1 Issue Register — Milestone 13B.5E

**Review date:** 2026-11-22 (Asia/Calcutta)  
**Sources reviewed:** release audit, release gate, regression status, known blockers, security findings/remediation tracker, and current marketplace/Android test output.

## Confirmed open P0/P1 product defects

**None confirmed in the reviewed source, regression reports, or tests executed for this workstream.** This is not a claim that every runtime path or device configuration was tested.

| ID | Severity | Component | Reproduction / evidence | Root cause | Fix status | Regression verification |
|---|---|---|---|---|---|---|
| DOOM-SEC-002 | Medium | Marketplace moderation | Concurrent approve/reject requests previously could both act on a pending submission. | Update lacked an atomic expected-state predicate. | Fixed before this workstream. | Current isolated integration race test passed: one 200, one 409, one persisted decision. |
| DOOM-REG-001 | Historical fix | Android auth session | Late login response after logout could restore a stale session. | Stale request response lacked generation invalidation. | Fixed; no new reproduction in this workstream. | Covered by the documented deterministic core-data regression; not rerun as part of the backend integration command. |
| TEST-13B5E-001 | Test fixture issue, not product defect | Marketplace integration test | The first new rejection test reused the exact package later used by the moderation-race test; upload correctly returned 409 duplicate submission. | Shared fixture bytes triggered existing owner+SHA duplicate protection. | Fixed by using a distinct `low-battery-card.doommod` fixture. | Full rerun passed 29/29 with zero failures/skips. |

## Open release-assurance risks (not reclassified as product P0/P1 defects)

- `RISK-SEC-004` — High release-assurance gap: protected CI execution and signed provenance are not demonstrated. Open.
- `RISK-SEC-005` — Physical security/recovery coverage is incomplete. Open/blocked.
- Independent publisher signature is absent; server-authenticated SHA-256 over HTTPS does not provide independent publisher provenance.
- Android marketplace install/update behavior was not physically verified in this run.
- Release signing identity is not configured and the default marketplace URL is `.invalid`.

These open assurance issues prevent stable distribution even though this review found no confirmed open P0/P1 product defect. No severe issue was downgraded to clear the gate.

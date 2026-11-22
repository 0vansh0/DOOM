# Private feedback triage

Assign sequential IDs such as BETA-0001 upon receipt. Record date, app build, affected scenario, priority, evidence location, reproducibility, owner and status. Keep any optional attachment in restricted storage. Link duplicates to the original ID. Keep identifiers stable across releases.

| Priority | Impact | Response |
|---|---|---|
| P0 | Security compromise, destructive behavior or serious data loss | Stop rollout immediately and notify the release owner privately |
| P1 | Crashes, widespread installation failures or broken core functionality | Hold rollout and reproduce on the affected build |
| P2 | Significant defect with a workaround | Document the workaround and schedule a fix |
| P3 | Minor visual defect or improvement | Review for a future build |

Move reports from New to Triaged, In progress, Ready for retest and Closed. Reproduce on a disposable install, record the exact fix build and repeat the original steps before closing. Reopen on recurrence. Keep unconfirmed observations distinct from reproduced defects.

## Report register

| ID | Date/build | Scenario and evidence | Priority | Reproduction | Owner | Status / retest |
|---|---|---|---|---|---|---|

No tester submissions have been entered.

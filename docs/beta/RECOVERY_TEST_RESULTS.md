# Recovery Test Results — 13B.5B

## Transaction behavior

The existing `ChangeTransactionCoordinator` persists a PREPARED transaction before a reversible write, verifies the applied value, and restores only when current state still matches DOOM's recorded applied value. A mismatch is retained as a conflict rather than overwriting a user's independent change. Safe Mode and current authorization are checked before write/restore.

Three deterministic regression cases were added to `ChangeTransactionCoordinatorTest`:

1. Cancellation after the adapter applied a write leaves a PREPARED record; reconciliation observes the actual value and marks it applied without replaying the write.
2. Cancellation after restore leaves a RESTORING record; reconciliation observes the original value and marks it restored without another write.
3. Revoked access before recovery does not issue a restore write and leaves the applied transaction available for later recovery.

These tests demonstrate the fake-adapter process-interruption paths; they do not guarantee recovery if Android or Shizuku is unavailable after a real process death.

## Execution

`core-domain:test` and the combined Android verification command passed after these cases were added. Physical privileged writes were not performed.

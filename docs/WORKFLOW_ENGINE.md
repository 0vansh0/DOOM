# DOOM workflow engine

## Opt-in compatibility

Definition 4 keeps its prior behavior when a trigger has no `workflow` object: the foreground runtime executes only that trigger's first action. Multi-action execution is explicitly opted into by adding a workflow object. This avoids silently changing existing V4 package behavior. Manifest schema 3, SDK API 2, Definition 3, and installed package records remain unchanged.

```json
"workflow": {
  "id": "charging-sequence",
  "executionMode": "sequential",
  "failurePolicy": "stopOnFailure",
  "timeoutMs": 15000,
  "maxConcurrency": 2
}
```

Workflow actions carry stable `id` values. Parallel workflows and `continueOnFailure` require each action after the first to set `independent: true`, declaring that it is safe to run without relying on prior action success. Parallel effects use the same condition-passing trigger and capability gate as sequential effects.

## Limits and policies

| Limit / policy | Behavior |
| --- | --- |
| 16 actions per workflow | Schema, CLI, parser and coordinator enforce the limit |
| 3 concurrent actions | Hard coordinator bound; `maxConcurrency` can lower it to 1 or 2 |
| 2 active workflows per mod | New starts are skipped with diagnostics when full |
| 30 second maximum timeout | A timeout cancels the workflow and releases its owned effects |
| `sequential` | Awaits each action result in declared order |
| `parallel` | Bounded structured fan-out; only explicitly independent later actions are accepted |
| `stopOnFailure` | Stops at first failed action; cancels peers and releases this workflow's resources |
| `continueOnFailure` | Records each failure and continues only for declared independent actions |

Android effect adapters are main-thread confined and synchronous. They do not run arbitrary developer code. The coordinator supports suspending trusted executors and tests the concurrency bound; current Android window operations remain short main-thread work. Cancellation is cooperative at coroutine suspension points.

## Ownership and cancellation

Every workflow session has a unique session ID in addition to the stable mod, workflow, and action IDs. An action registers resources immediately when allocated. Cancellation on mod disable, runtime stop, overlay permission loss, or safe mode closes resources owned by that mod/session. `overlay.dismiss` in a workflow is scoped to effects created by that workflow session. It cannot remove a different workflow's overlay.

Successful workflow completion does not end a visual effect. Overlay lifetime remains governed by its configured bounded `durationMs` (up to 10000 ms) or the existing default, charger-disconnect rules, explicit dismissal, or mod cleanup. Expiry removes the ownership record and closes the effect handle. A workflow timeout or stop-on-failure releases resources created by that workflow even if a visual action had already succeeded.

## Security checks

The trusted host resolves only registered action IDs, validates the action and supported parameters, confirms the mod is still eligible, rechecks both declared and approved capabilities, and checks current overlay authorization before every action. Unknown action types, unknown workflow policies, invalid action IDs, unsupported parameter keys, stale runtime sessions, and unapproved capabilities fail closed. No downloaded code or commands are evaluated.

The `charging-workflow` sample is packaged from `sample-mods/sdk2/charging-workflow` using `doom package`. It requests `battery.read` and `overlay.render`; the user must grant those capabilities and Android overlay access before activation.

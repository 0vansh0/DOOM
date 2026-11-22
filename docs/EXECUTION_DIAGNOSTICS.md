# Execution diagnostics

DOOM records local, structured runtime diagnostics with timestamp, mod ID, runtime session/workflow IDs when known, trigger or operation, severity, result, typed failure category, bounded duration and a sanitized message. Runtime event payloads, private package paths and system logs are not collected.

Failures and warnings are retained even when verbose diagnostic logging is off. Successful details and workflow start/finish markers are retained only as needed for interrupted-run reconciliation; verbose action successes follow the user's diagnostic logging preference. Both the structured table and the legacy execution log are capped at 200 rows. Clearing local diagnostics clears both tables. Export is an explicit user initiated SAF action and contains only API/capability statuses, mod IDs/versions, selected diagnostics, and unresolved recovery states.

The live diagnostics screen filters by mod ID, severity, failure category, time window and workflow/operation. The workflow monitor reports only observable active sessions and pending/running action identifiers. DOOM does not estimate CPU or battery consumption.

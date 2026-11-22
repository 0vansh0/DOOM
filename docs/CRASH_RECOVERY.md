# Crash and restart recovery

Workflow sessions are never replayed. A workflow start marker includes only mod, session and workflow identifiers. If startup finds a start marker without a terminal marker, it adds an `UNKNOWN / INTERRUPTED` diagnostic explaining that the process ended before completion and that actions were not replayed. This is evidence of an incomplete host session, not proof that a visual or system effect occurred.

The existing host recovery detector uses Android historical process-exit information when available and a durable startup marker. Repeated confirmed host failures can enter persistent safe mode; ordinary backgrounding or process death is not automatically classified as a crash. Android force-stop, unavailable exit history, device shutdown and process death before a marker reaches storage limit automatic recovery guarantees.

Privileged setting journals remain owned by the existing ChangeTransactionCoordinator. Startup reconciliation reads current state only when authorized, preserves conflicts/uncertain states, never replays writes, and does not erase recovery records. A restoration can remain unavailable while the required Android access or service is unavailable.

# Safety Center

The Device diagnostics screen includes the Safety Center: persistent global safe-mode status, per-mod desired and observed status, active workflow/action monitoring, suspended-mod retry/disable controls, a filtered diagnostic timeline and privileged recovery warnings. The existing Settings safe-mode control remains the single control for that preference.

Safe mode is stored in DataStore. Enabling it suspends third-party execution, cancels workflows and releases runtime-owned effects while preserving desired-enabled choices and privileged change journals. On deliberate exit, the existing foreground lifecycle rechecks installed status, grants and Android permissions before resuming eligible subscriptions. Diagnostics, settings and recovery remain accessible.

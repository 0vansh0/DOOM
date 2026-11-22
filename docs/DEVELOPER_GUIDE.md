# Runtime diagnostics for mod developers

DOOM's public declarative API remains unchanged. The host records stable result and failure categories around trusted actions; third-party packages cannot write diagnostic records, access system logs or supply executable error handlers. A capability denial or user cancellation is expected control flow and does not count toward a mod fault suspension. Repeated runtime failure, cleanup failure, timeout or resource-limit violation can suspend that mod after three qualifying failures in 60 seconds.

Use the Device diagnostics Safety Center to inspect the failure category and sanitized explanation. Correct the package or capability/permission setup before explicit retry. Desktop CLI simulation is deterministic but does not reproduce Android process death, adapter disconnect timing or overlay behavior.

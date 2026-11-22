# Privileged operations and diagnostics

Milestone 10C does not add privileged operations. Existing screen-timeout recovery remains app initiated through Android's Modify system settings access and its durable transaction journal; it is not an imported-mod action or Shizuku shell command. Safe mode does not erase these records. Unresolved, uncertain and conflict records remain visible in Settings and Safety Center and are included in an explicit local diagnostic export.

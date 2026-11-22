# Dynamic settings

V4 settings are data declarations rendered by DOOM's allowlisted Compose controls. The schema currently recognizes boolean, integer, number, duration, color, enum and bounded text values. Defaults and bounds are validated before saving; settings belong to one installed mod and are exposed to executions through validated snapshots/references.

No package can supply a layout, Compose code, script or custom control. Supported settings persist in the host database. Compatible package updates retain matching setting identifiers and validate stored values against the new schema; removed, renamed or newly invalid values must not be assumed to migrate automatically. Verify migrations using the importer/runtime and report incompatibilities explicitly.

See [dynamic settings system](../DYNAMIC_MOD_SETTINGS.md) for existing persistence and control details.

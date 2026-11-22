# SDK and package compatibility policy

Manifest Schema 3, SDK API 2, Definition V3 and Definition V4 are distinct version fields. V3 keeps its prior parser and behavior. V4 is selected explicitly by `definitionVersion: 4`; unsupported versions are rejected with diagnostics rather than reinterpreted. No version bump is made by adding templates or examples.

Each package declares a stable reverse-domain mod ID, semantic package version, supported Android range and capabilities under the manifest contract. Runtime compatibility also depends on trusted executors, current Android permissions/special access, hardware and user approval. Catalog presence alone does not imply implementation.

For updates, keep the mod ID, increment its package version, validate setting IDs/defaults and request approval for changed capabilities. The host preserves compatible stored settings by mod/setting identity; incompatible schemas or missing prerequisites can block activation. Definition 3 packages are not silently migrated to Definition 4.

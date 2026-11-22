# Mod package structure

A `.doommod` is a ZIP archive validated by the Android importer. A normal project contains `doom.json`, `mod.json`, declared assets only, and project-side README/scenario files. The current CLI excludes the README and `test-scenario.json` from the archive. The importer rejects unsupported entries, unsafe paths, duplicate names, malformed documents and unsupported executable payloads.

`doom.json` carries the package identity, package version, manifest schema version, SDK API version, compatibility and requested capabilities. `mod.json` contains the definition. Keep the stable mod ID when publishing updates and increase the semantic package version. An update is accepted only when the host importer and compatibility checks accept it.

Definition 3 remains supported. Definition 4 uses `definitionVersion: 4`, named trigger entries, condition trees, action lists, optional workflow policy and settings declarations. See the versioned JSON schemas and [compatibility policy](COMPATIBILITY_POLICY.md).

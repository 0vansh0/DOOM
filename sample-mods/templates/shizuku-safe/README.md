# Shizuku safety-boundary template

No Shizuku operation is available to imported mods. The catalog operation `shizuku.diagnostic.identity` is app-only (`modAccessible: false`); this template uses only an ordinary DOOM in-app preview and demonstrates safe fallback. It does not detect or query Shizuku from the mod.

Run `doom capabilities` to inspect trusted host metadata, then `doom validate .`, `doom test . .\test-scenario.json` and `doom package .`.

# Optional capability integration

Demonstrates separate in-app preview and haptics triggers. A tester can approve `ui.preview` while denying `haptics.vibrate`; the deterministic scenario records the denied action as suppressed. Denial is not an automatic grant.

Run `doom capabilities` to inspect the shipped catalog, then run `doom validate .`, `doom test . .\test-scenario.json` and `doom package .`.

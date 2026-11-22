# Mod testing guide

For every change run `doom validate`, `doom test`, and `doom package`; then run `doom inspect` on the output. Include scenarios for matched and suppressed events, condition boundaries, initial settings and denied capabilities. Ensure expected action IDs and order are explicit. A scenario pass proves the desktop declarative semantics only; it is not evidence of Android overlay, hardware, permission, lifecycle or battery behavior.

The repository also has Kotlin schema/runtime/importer conformance tests (`.\gradlew.bat :mod-engine:test`). The CLI fixtures and Kotlin importer tests share package/schema fixtures where configured. A package accepted by a portal validator still must pass the host importer. Record Android instrumentation and physical-device results separately; never mark unavailable hardware or a skipped integration test as passed.

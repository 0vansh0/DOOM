# Internal device coverage, 2026-11-21

| Environment | Executed | Outstanding |
|---|---|---|
| Nothing CMF, Android 16 / API 36, ADB model A001 | Installed debug beta.1 code 2 cold-launched MainActivity. ActivityManager reported OK and 1166 ms. The process existed and the activity held focus. | Signed release launch and upgrade |
| API 31–35 emulators and other OEM devices | No device result this run | Repeat launch, settings and upgrade scenarios when available |

For each candidate record variant, build hash, environment, steps, expected and observed results. Cross overlay access granted/denied with Shizuku absent/authorized/disconnected. Check preview, charging start/stop, safe mode and cleanup for one mod and several active mods. Exercise online catalog, offline cache, confirmed install and confirmed update. Compare pre/post mod IDs, settings and recovery records after same-signer update and restart. These combinations remain pending on the release candidate. See DEVICE_MATRIX.md for earlier runs.

On the debug A001 run, Discover displayed its empty offline cache message. Installed listed Battery Glow 1.0.0 as INSTALLED_DISABLED. Mod Details reported overlay.render denied and required DOOM approvals for battery.read, overlay.render and ui.preview. No grants or installation data were changed. Charging, external overlay execution, persistence across a release upgrade and Shizuku disconnect remain BLOCKED for the candidate.

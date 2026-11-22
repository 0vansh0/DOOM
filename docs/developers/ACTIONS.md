# Supported actions

Use the exact identifiers and parameter constraints in `registry.json` and `mod-v4.schema.json`:

- `overlay.show` and `overlay.preset.apply`: approved battery-ring/floating-card renderer; external overlay authorization and per-mod approval may be required.
- `overlay.dismiss`: dismisses effects owned by the relevant runtime/workflow scope.
- `ui.preview.show`: renders in DOOM while its UI is available.
- `haptics.pulse`: bounded pulse where vibrator hardware is available and capability is approved.
- `device.display.read`: parameterless non-unique display metadata read where registered and supported.

V4 actions execute in their declared workflow order. A missing workflow retains the established V4 first-action behavior. Explicit sequential/parallel workflows are bounded; parallel and continue-on-failure plans require independent-action declarations. Effects can outlive the action result for their declared bounded visual lifetime.

`app.launch.approved`, Shizuku operations and system-setting operations are not available to imported mod actions. Do not put commands, class names, arbitrary URLs or executable payloads in action parameters.

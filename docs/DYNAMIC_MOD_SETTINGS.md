# Dynamic mod settings (Definition 4)

Definition 4 packages may declare a `settings` object. Each property key is a stable setting ID and each value is a validated data schema. DOOM renders only built-in Compose controls; packages cannot provide layouts, code, callbacks, or executable UI.

```json
{
  "settings": {
    "enabled": { "type": "boolean", "label": "Enable glow", "default": true },
    "ringThickness": { "type": "integer", "label": "Ring thickness", "default": 7, "min": 1, "max": 20, "step": 1 },
    "duration": { "type": "duration", "label": "Display time", "default": 3000, "min": 500, "max": 10000, "step": 500, "unit": "ms" },
    "accent": { "type": "color", "label": "Accent color", "default": "#A78BFA" },
    "position": { "type": "enum", "label": "Position", "default": "center", "choices": ["center", "top", "bottom"] },
    "message": { "type": "text", "label": "Message", "default": "Charging started", "maxLength": 80 }
  }
}
```

Supported types are `boolean`, `integer`, `number`, `color`, `enum`, `text`, and `duration`. Numeric settings require finite `min`, `max`, and `default` values where the schema requires them; sliders may declare a positive `step`. Duration is numeric and requires an explicit supported unit (`ms` or `s`). Colors use `#RRGGBB` or `#AARRGGBB`. Enums have a nonempty unique choice list containing the default. Text is length-bounded. Definitions are limited to 24 settings per mod.

Compose controls are a switch, bounded slider, palette/hex color chooser, dropdown, bounded text field, and bounded duration input. Labels and descriptions are visible and exposed to accessibility services. Invalid edits are rejected before persistence.

Settings are isolated per installed mod and persisted in the existing Room settings record. The database adds only a nullable `settingDefinitions` column through migration 5→6; prior settings are retained. Removed or renamed keys are ignored and new or invalid stored values resolve to the declared default. Changing a value updates the next runtime event's immutable settings snapshot; it does not restart the mod or rewrite an already-created effect. In-app preview remains available without overlay authorization.

Actions can use a setting through a validated `{"$setting":"settingId"}` parameter reference. Conditions may compare supported metadata sources using `setting.settingId`. Values are resolved from a validated settings snapshot by trusted host code. Capability approval remains independent of settings.

Definition 3 is unchanged. Definition 4 receives optional labels and type-appropriate defaults for omitted optional presentation metadata so existing V4 definitions remain valid. No manifest or SDK version bump was needed; the additive contract retains manifest schema 3 and SDK API 2.

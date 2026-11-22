# V4 conditions

A trigger has a declarative condition expression. Use `all` and `any` groups and typed comparison leaves. Supported comparisons include numeric `below`, `above`, `lessThan`, `lessThanOrEqual`, `greaterThan`, `greaterThanOrEqual`, plus `equals` and `notEquals`, subject to source/value type validation. Supported sources include event metadata and validated setting references in the schema. Unknown operators, unknown fields, invalid types, unresolved settings and over-limit expressions are rejected; there is no expression language or JavaScript execution.

Nested expression and action limits are enforced by the schema/parser. Consult `mod-v4.schema.json` and `doom validate` for the exact accepted form. `doom test` evaluates deterministic injected scenarios and records actions; it does not execute real Android effects.

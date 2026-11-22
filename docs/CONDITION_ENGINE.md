# V4 condition engine

Condition values are typed and read from an immutable event snapshot. Supported sources are numeric `battery.level` (0–100), boolean `battery.charging`, and string `event.name`. Unknown/missing values evaluate false. Comparing unlike types evaluates false; relational operators apply only to numbers.

Leaf operators are `lessThan`, `lessThanOrEqual`, `greaterThan`, `greaterThanOrEqual`, `equals`, and `notEquals`. The established V4 spellings `below`, `above`, and `equals` remain accepted as compatibility aliases for numeric comparisons. Boolean and string sources allow equality/inequality only. Groups use `{ "op":"all", "conditions":[...] }` and `{ "op":"any", ... }`; groups must have at least one child.

Limits are maximum depth 4, 32 leaf conditions, 64 total nodes, and 16 triggers per mod. Android parsing, runtime evaluation, and CLI semantic validation enforce the same limits. The evaluator preflights the full tree before short-circuit evaluation so a large `all` or `any` tree cannot evade limits.

Battery threshold behavior uses a per-trigger rising edge: a condition fires once when it changes false→true, remains suppressed while true, and rearms after it evaluates false. For `battery.level < 20`, 21→19 fires, 19→18 does not, reaching 20 rearms, and a later drop to 19 fires again. This edge state is session-local and resets on deactivation/restart.

# Contribution workflow

Reproduce on a disposable fixture, record expected and observed behavior, and identify the affected module. For SDK changes add positive and negative shared fixtures and run Kotlin and CLI validation. Preserve legacy V3/V4 semantics. Introduce an explicit version for incompatible changes. For portal/backend changes test authorization on the server and additive migrations on a disposable database. Use the PR template to record exact results and limitations. Community packages follow the [quality checklist](MOD_QUALITY_CHECKLIST.md).

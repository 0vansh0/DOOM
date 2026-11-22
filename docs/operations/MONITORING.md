# Monitoring and Reliability Operations

The marketplace API emits structured request logs with request IDs and redacts credential/report fields. It exposes lightweight `/health` and dependency-aware `/ready`; the optional `/internal/metrics` endpoint is token protected and should remain on a private network. Counters are process-local and reset on restart.

Operational dashboards/alerts must cover API availability/readiness, request failures/latency, database connection/query failures, S3 failures, auth rate limiting, feedback submissions, package validation, and moderation decisions. Provider-side collection, durable metrics, alerting, and an external incident paging channel were not demonstrated as configured. Do not present them as live.

App crash diagnostics must be shared by the user through the existing explicit export/feedback flow. No automatic private-log, installed-app, device-identifier, or diagnostic upload is part of this maintenance design. Never log passwords, JWTs, authorization headers, feedback contents, private attachments, or raw package contents.

When readiness fails, inspect service health and dependency status through restricted operator access; public responses stay sanitized. Preserve logs and audit records according to approved retention policy.

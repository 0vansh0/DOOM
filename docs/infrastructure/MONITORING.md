# Monitoring and operational signals

Fastify retains structured logs and request IDs with sensitive fields redacted. The process now tracks bounded in-memory HTTP request/error counts and latency histograms plus readiness, upload, moderation and storage failure counters. `/internal/metrics` is registered only when a dedicated token is configured, checks the bearer token with a timing-safe comparison, and returns 404 for invalid credentials. Do not expose this route publicly; scrape it on a private network.

Metrics are process-local and reset on restart; they are not a durable monitoring backend or alerting system. Production still needs provider-side log retention, dashboards, alerts for `/ready` failures and service errors, PostgreSQL pool visibility, storage error alerts, and privacy/retention review. No telemetry service was connected in this milestone.

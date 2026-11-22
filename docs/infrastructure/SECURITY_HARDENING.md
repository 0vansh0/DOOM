# Infrastructure security hardening

The API container now uses a multi-stage Node 24.22.2 LTS build, prunes development dependencies, runs as the non-root `node` user and starts with an exec-form command. Startup migrations were removed. Local Compose remains loopback-bound and uses its existing development data volumes. Staging preparation uses a read-only root filesystem, bounded `/tmp`, dropped Linux capabilities, `no-new-privileges`, resource limits, and a readiness health check. The image uses a pinned version tag, not an immutable digest; target-platform digest pinning/scanning remains a deployment step.

The Nginx template at `deployment/nginx/marketplace.conf.template` prepares TLS 1.2/1.3, default-host rejection, HTTP redirect, request/body limits, timeouts, security headers and explicit proxy header replacement. It requires operator review of real addresses, DNS and certificates before use.

The backend redacts authorization, cookies, passwords, tokens, secrets, reports and error parameters. CORS origins, proxy trust, rate limits, DB pool/timeouts and TLS mode are configured per environment. Container vulnerability scanning and runtime policy must be selected and run in the target deployment environment; template hardening alone does not prove image security.

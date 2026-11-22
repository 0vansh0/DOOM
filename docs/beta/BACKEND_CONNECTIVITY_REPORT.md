# Backend Connectivity Report — Milestone 13B.5A

**Verification date:** 2026-11-22 (Asia/Calcutta)  
**API origin used by Android:** `https://localhost:9443/`  
**Android device:** Nothing A001 / Android 16 / API 36

## Topology and trust

- Docker Compose publishes PostgreSQL (`127.0.0.1:5438`), SeaweedFS S3 (`127.0.0.1:9000`) and the API (`127.0.0.1:8787`) on loopback only. Compose volumes were retained.
- A separate test API was bound to loopback port 8788 with its own database, JWT secret and isolated test buckets. The existing TLS gateway was configured to proxy only to that loopback API.
- The TLS gateway listens at `127.0.0.1:9443`. Its `localhost` certificate is verified against the checked-in public debug CA in host smoke tests and by Android's debug-only network security configuration. Hostname verification remains enabled; no trust-all client or cleartext credential path was used.
- `adb reverse tcp:9443 tcp:9443` connected the physical device's loopback HTTPS origin to the host gateway. This is a USB-only local development path; it is not a public service.
- Release marketplace configuration remains the non-routable `.invalid` placeholder. The debug CA and test endpoint are excluded from release resources/configuration.

## Health and dependencies

`node marketplace-api/scripts/13b5a-connectivity-check.mjs` used the debug CA, isolated database and explicitly configured test buckets. Result:

| Check | Result |
|---|---|
| `GET /health` | HTTP 200 `{"status":"ok"}` |
| `GET /ready` | HTTP 200 `{"status":"ready"}` |
| PostgreSQL | Connected; 3 migration records applied |
| Required quarantine and published buckets | Present |
| S3 put/get/delete roundtrip | PASS, 23 bytes read back; temporary object deleted |

Compose status: API healthy, PostgreSQL healthy, SeaweedFS running, storage initializer exited 0. No volumes or production data were removed.

## Android

- Android instrumentation test `MarketplaceHttpsDeviceTest.registersLogsInAndSubmitsConsentedFeedbackThroughTrustedHttps`: 1 passed, 0 failed on the connected A001. It exercised registration, logout, invalid credentials, login, the 900-second `expiresIn` window, missing-consent rejection, server-confirmed submission and HTTP 401 session clearing through `https://localhost:9443/`.
- `adb reverse` was active and the app reported the expected debug HTTPS origin. After the connected Gradle instrumentation task, the package was unexpectedly absent; the same debug APK was reinstalled with `adb install -r` and the launcher process was observed. No explicit data-clear command was used, but the previous private app data state is **UNKNOWN** and cannot be claimed preserved.
- The instrumentation test exercised data repositories, not the Compose screens. Live Discover browsing, package import/execution, update migration and Settings → Help & Feedback interaction remain unverified on-device.

## Reproducible check

Set `DATABASE_URL` to the isolated `doom_marketplace_13b5a_20261122` database and set `S3_ENDPOINT`, test bucket names and local development credentials in the current shell, then run:

```powershell
node marketplace-api/scripts/13b5a-connectivity-check.mjs
```

The script refuses a non-HTTPS/non-localhost API and refuses a database whose name is not the isolated test database. It never prints credentials.

# Local HTTPS Development Setup — 13B.5A

## Verified topology

```text
Nothing A001 --USB adb reverse tcp:9443--> host 127.0.0.1:9443 HTTPS gateway
                                               |
                                               +--> 127.0.0.1:8788 isolated Fastify test API
                                                            |
                                                            +--> local isolated PostgreSQL database
                                                            +--> SeaweedFS S3 test buckets
```

Compose API/PostgreSQL/S3 host ports are loopback-only. The gateway permits only the `localhost` host and loopback HTTP upstream. The HTTPS leaf certificate has `localhost` identity; the host smoke test pins the checked-in public debug CA, while the Android debug variant uses the same CA with ordinary chain and hostname verification. The private certificate key is in the local user certificate store/PFX location outside the repository. Never copy the key into source control or release resources.

## Android debug configuration

Use the existing Gradle property:

```powershell
.\gradlew.bat :app:assembleDebug -PdoomMarketplaceBaseUrl=https://localhost:9443/
& 'D:\platform-tools-latest-windows\platform-tools\adb.exe' reverse tcp:9443 tcp:9443
```

The reverse tunnel is local to the authorized USB-connected device. Do not change release networking, TLS trust, or credential origin restrictions for local development. Release currently uses the `.invalid` placeholder, so live release marketplace service is intentionally unavailable.

The gateway launcher accepts a local-only upstream override for the isolated test API. For example, `-Upstream http://127.0.0.1:8788/`; it rejects HTTPS/non-loopback upstream values. Keep the gateway and test API on loopback and stop them after use. Do not expose this test endpoint to a LAN or public network.

## Verified / not verified

- **PASS:** Android API requests over HTTPS, localhost hostname, trusted debug CA, USB reverse tunnel.
- **PASS:** Host debug CA/hostname verification and both health/readiness paths.
- **BLOCKED:** Developer Portal live API calls from the Codex in-app browser. CORS is correct for `http://localhost:5173`, but its TLS fetch returned a network error even though CA-pinned Node requests succeeded.
- **Not for release:** Debug CA resource and test URL are debug-only. Never use `curl -k`, trust-all clients, cleartext credentials or a release trust override.

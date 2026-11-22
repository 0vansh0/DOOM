# Signing Guide

Supply DOOM_KEYSTORE (absolute path outside the checkout), DOOM_STORE_PASSWORD, DOOM_KEY_ALIAS and DOOM_KEY_PASSWORD through a protected process environment. Use a trusted workstation and an approved existing key. Keep encrypted offline backups under separate access control and verify recovery by comparing the certificate SHA-256. Keep passwords in a separate vault. Avoid Gradle debug logging and build scans during signing.

Direct APK updates require the original app signing identity. Play accepts an AAB signed with the registered upload key and signs delivered APKs with the app signing key. A lost Play upload key can be reset through the authorized Play Console account. Losing a sole direct-distribution signing key can prevent future compatible updates. See https://developer.android.com/studio/publish/app-signing.

Run assembleRelease and bundleRelease without the verification flag to sign. Verify the APK using apksigner verify --verbose --print-certs and compare the fingerprint with the independently recorded channel identity. Verify the bundle using jarsigner -verify -verbose -certs and keytool -printcert -jarfile. Inspect entry coverage and signer identity before accepting a bundle.

## Unsigned verification

Run each command from the repository root. The property must be the literal lowercase value `true` and applies only to that invocation.

```powershell
.\gradlew.bat :app:assembleRelease -PdoomUnsignedVerification=true
.\gradlew.bat :app:bundleRelease -PdoomUnsignedVerification=true
```

The build omits the approved signing configuration in this mode, even if the four environment variables are present. Keep the property off in persistent Gradle configuration. When the signing identity is available, inject the four values from the protected environment, omit the property, and repeat signature, fingerprint and checksum verification on the resulting files.

Before signing, set the non-secret Gradle property doomApprovedSignerSha256 to the SHA-256 certificate fingerprint from the channel owner’s independent record. Use the app signing certificate for direct APK delivery and the registered upload certificate for Play uploads. The release gate verifies the selected certificate before packaging and rejects standard Android debug identities.

### Configure the approved certificate from PowerShell

Use the same shell that has the four signing variables. Read the approved certificate fingerprint from the direct APK channel record or the Play Console upload certificate for an upload build. File checksums in SHA256SUMS identify artifacts and cannot supply this value.

```powershell
$env:DOOM_APPROVED_SIGNER_SHA256 = (Read-Host 'Approved signing certificate SHA-256').Trim()
.\gradlew.bat :app:checkReleaseSigning :app:assembleRelease :app:bundleRelease
```

For subsequent shells using the same channel, persist the public fingerprint with `[Environment]::SetEnvironmentVariable('DOOM_APPROVED_SIGNER_SHA256', $env:DOOM_APPROVED_SIGNER_SHA256, 'User')` after a successful check. An explicit Gradle property takes precedence. Update a stale property before switching channels.

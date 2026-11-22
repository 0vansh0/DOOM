# Artifact integrity results

Generated locally on 2026-11-22 for beta.2 (code 3). Both artifacts are unsigned verification outputs; do not distribute them.

| Artifact | Path | Size | SHA-256 | Signature |
|---|---|---:|---|---|
| APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | 22,720,440 bytes | `CDEF06BB17970E591029371EC59305A3B4E88285C595C3A7EC888CE5D4E4CCCB` | Unsigned; `apksigner` says `DOES NOT VERIFY`. |
| AAB | `app/build/outputs/bundle/release/app-release.aab` | 7,864,955 bytes | `C0C1808D1D701617E0B59E819CA4ECFEE661356918DDB1C494077B684F37413F` | Unsigned; `jarsigner` says `jar is unsigned`. |

APK manifest inspection with Android SDK `apkanalyzer` confirmed package `org.doom.app`, versionCode 3, versionName `0.1.0-beta.2`, minSdk 31, targetSdk 35. Gradle output metadata records the same identity. The AAB contains `base/manifest/AndroidManifest.xml`; standalone AAB manifest decoding was unavailable in the installed SDK tools, so bundle identity is evidenced by same-invocation Gradle metadata and APK manifest, not a separate bundletool manifest dump.

Commands:

```powershell
.\gradlew.bat :app:assembleRelease -PdoomUnsignedVerification=true
.\gradlew.bat :app:bundleRelease -PdoomUnsignedVerification=true
& 'D:\Android\Sdk\build-tools\35.0.0\apksigner.bat' verify --verbose --print-certs app\build\outputs\apk\release\app-release-unsigned.apk
jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab
& 'D:\Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat' manifest print app\build\outputs\apk\release\app-release-unsigned.apk
Get-FileHash app\build\outputs\apk\release\app-release-unsigned.apk,app\build\outputs\bundle\release\app-release.aab -Algorithm SHA256
```

Source revision is unavailable because this checkout has no Git metadata. These hashes identify these unsigned local artifacts only.

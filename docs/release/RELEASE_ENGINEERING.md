# Release Engineering

Use JDK 17, the checked-in Gradle 8.9 wrapper, Android SDK 35 and the repository lockfiles. Record the source revision, installed build-tools and exact JDK. From the root run gradlew.bat :app:assembleRelease :app:bundleRelease -PdoomUnsignedVerification=true for non-distributable verification, then python tools/release_manifest.py. The candidate directory in app/build contains versioned copies and SHA256SUMS. For signed builds follow SIGNING_GUIDE.md. Archive the exact verified signed bytes and compute checksums again after signing. Independent byte-for-byte reproducibility remains unverified.

Local debug and internal QA can select doomMarketplaceBaseUrl with the debug build. Debug trust anchors live under app/src/debug. Beta and production use the reserved .invalid endpoint until an approved HTTPS origin is reviewed and configured in the release build type. Remote release catalog access is blocked.

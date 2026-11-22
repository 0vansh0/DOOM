# Release Checklist

Distribution remains BLOCKED. Confirm security and performance gate decisions, a trusted marketplace endpoint, approved signer, same-signer upgrade and rollback evidence, dependency notices, privacy-policy owner and support contact. Review the MIT license, screenshots, listing copy and launcher assets. The manifest uses a drawable icon and the platform theme. Store-ready adaptive icon, curated screenshots, support contact, public privacy URL and complete dependency attribution require owner review. Verify APK signature and bundle certificate, freeze the tested artifacts and independently compare checksums before any authorized transfer.

## Local verification on 2026-11-21

Debug APK, unsigned release APK and unsigned AAB assembled. Release lint passed. Gradle rejected ordinary release assembly with missing signing credentials. Android apksigner rejected the unsigned APK and jarsigner reported an unsigned bundle. The Nothing A001 accepted a same-signer debug update from code 1 to 2 and launched the main activity. Detailed data-preservation checks and signed-release upgrade checks are outstanding.

Engine 70 passed, CLI 59 passed, portal 4 passed and marketplace 10 passed with 8 integration cases skipped. Visual Builder initially failed one case due to a shared CLI build file lock and passed all 11 on isolated rerun. App debug unit test task reported NO-SOURCE. Hosted workflows and independent clean-build reproducibility remain unverified. External distribution is blocked.

## Signing verification on 2026-11-21

The unsigned APK and AAB verification build succeeded for org.doom.app version 0.1.0-beta.1, code 2. The APK is 22,589,364 bytes with SHA-256 c2ca7a7642640a5e8f01840fd28f22571c1237452af2037b7c3adcb26b8f15af. The AAB is 7,816,664 bytes with SHA-256 2d06acc4732d1e034937491bc3bb5b9c36c370412565c77d9bfd2b696ea42e87. Android apksigner and JDK jarsigner confirmed unsigned outputs. Neither has a signing certificate fingerprint.

The ordinary release gate rejected absent credentials and separately rejected the standard SDK debug identity. The approved-identity signing path and same-signer release update remain blocked pending channel credentials, an independently approved fingerprint and a prior signed release. External distribution remains blocked by the security and performance gates.

Domain 19/19, engine 70/70, CLI 59/59, builder 11/11 and portal 4/4 passed. Four npm audits reported zero known vulnerabilities. Marketplace integration with a disposable PostgreSQL database and memory object storage passed 18/18 after correcting a test that expected a listing with one published version remaining to be hidden. Device instrumentation and live S3 checks were not executed in this run.

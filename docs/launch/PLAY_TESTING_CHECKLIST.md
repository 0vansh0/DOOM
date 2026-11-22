# Google Play testing preparation

**Not submitted. Requirements reviewed 2026-11-22 against official Google/Android documentation.**

## Current hard blocker

The current candidate targets API 35. Google Play requires new apps and app updates to target Android 16/API 36 or higher starting 2026-08-31. Therefore the current candidate is not ready for Play submission. Migrate target SDK to 36 and test the resulting Android behavior before preparing a Play track. Google documents an extension date of 2026-11-01; it has passed as of this review.

## Before creating any Play release

- Confirm the developer account type, account ownership/verification status, package-name ownership and any applicable testing requirements.
- Verify `org.doom.app` has not been registered by another Play publisher. This repository has no Play Console state.
- Choose Play App Signing and identify which key is the upload key versus the Play app-signing key. Keep current channel identity decisions consistent; never silently replace a key.
- Upload only a signed AAB with monotonically increasing versionCode. Verify the uploaded artifact and expected certificate in Play Console. Do not upload unsigned builds.
- Complete app content, target API, permission declarations, foreground-service declarations, privacy policy, accurate Data safety answers, store listing, screenshots, support contact and content rating from actual behavior.
- Resolve the app account-deletion requirement before submission: current backend inspection found feedback deletion, but no self-service account deletion endpoint. Do not claim deletion is supported until implemented and tested.
- Internal testing supports up to 100 testers per app. Start internally before considering closed testing; do not create a track or add testers without approval.
- For personal developer accounts created after 2023-11-13, Google currently requires at least 12 testers continuously opted in to a closed test for 14 days before applying for production access. Confirm whether this applies to the actual owner account in Play Console; do not assume account category.
- Review device catalog and exclusions, accessibility, crash/ANR behavior and staged rollout monitoring. No Play Console account, test track, device catalog or store listing was inspected in this task.

Official references (requirements may change; recheck at submission):

- [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Set up internal, closed or open testing](https://support.google.com/googleplay/android-developer/answer/9845334)
- [Testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Android app signing and Play App Signing](https://developer.android.com/studio/publish/app-signing)
- [Google Play User Data, privacy policy and Data safety](https://support.google.com/googleplay/android-developer/answer/10144311)

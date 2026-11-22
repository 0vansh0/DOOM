# Publishing a DOOM Mod

1. Create and validate a package using the [DOOM CLI reference](CLI_REFERENCE.md) or [Visual Builder](VISUAL_BUILDER.md).
2. Sign in to the Developer Portal and choose **New submission**.
3. Optionally create a draft with the mod ID, then attach the `.doommod`; otherwise upload directly as a new submission.
4. Confirm the API reports `UPLOADED`. Open its details and run server validation. The API stores the package in private quarantine and moves a passing submission to `PENDING_REVIEW`.
5. A different reviewer must approve or reject the submission. Authors cannot review their own packages.
6. After approval, the owner selects a category and publishes. A published version is immutable. To release an update, submit a new package version and obtain a new review decision.

Use a valid semantic version and keep the package's manifest name, description, Android API, definition version, and capabilities accurate. Capabilities are derived from the validated package; the portal cannot edit them. Marketplace approval is a human review status, not a security guarantee. DOOM still requires users to review and authorize capabilities on device.

The current backend does not persist changelogs, screenshots, icons, support links, ratings, or download counts. Do not advertise these fields as part of a release until the API contract and storage model add them.

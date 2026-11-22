# Internal tester onboarding draft

DOOM supports Android API 31 and later. Use a disposable device for upgrade or recovery scenarios. Record model and Android version. After enrollment is authorized, obtain the build and its SHA-256 through the approved private channel. Ask the maintainer to verify the certificate if the source or identity is unclear.

For private installation, open the verified APK and review Android's confirmation. For Play, use the approved internal track. Update through the same channel with a higher version code. Stop on a signature conflict and preserve the installation. Start with the in-app preview, review each mod's capabilities, and grant only what the scenario needs. If an effect misbehaves, disable the mod or enter Safe Mode. Keep unresolved privileged changes visible in Recovery.

## Privacy and feedback

Participation and feedback are voluntary. This feedback process stores only information a tester chooses to submit. Marketplace browsing and downloading contact the configured server when available. Use BUG_REPORT_TEMPLATE.md for a defect or its short feedback form for suggestions. Review attachments first and remove names, account details, tokens, full paths and device identifiers. Diagnostic export and sharing are separate explicit choices. Avoid full system logs and installed-app lists. The maintainer reviews reports privately. An external feedback destination, support contact and retention policy still require owner approval.

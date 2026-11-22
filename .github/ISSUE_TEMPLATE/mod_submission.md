---
name: Mod submission review request
about: Request review of a declarative DOOM community mod
title: ""
labels: mod-submission
assignees: ""
---

## Mod identity and version

Provide the stable mod ID, package version, definition version and a test-only package/checksum reference. Do not attach private credentials.

## Validation evidence

- [ ] `doom validate` passed for source and archive.
- [ ] `doom test` scenarios and expected actions are included.
- [ ] Android importer compatibility was checked or explicitly marked untested.
- [ ] Capabilities and Android requirements are documented.
- [ ] No executable payload, shell command, private token or undeclared asset is included.
- [ ] Changelog, support/license information and known limitations are included.

This template requests human review; it does not approve or publish a package.

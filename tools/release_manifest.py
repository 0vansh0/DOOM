"""Collect the unsigned APK and bundle from the same Gradle invocation."""
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1]
outputs = root / "app/build/outputs"
metadata = json.loads((outputs / "apk/release/output-metadata.json").read_text())
item, = metadata["elements"]
assert metadata["applicationId"] == "org.doom.app"
assert item["outputFile"] == "app-release-unsigned.apk"
prefix = f"DOOM-{item['versionName']}-{item['versionCode']}-unsigned"
dest = root / "app/build/candidate"
dest.mkdir(exist_ok=True)
lines = []
for src in [outputs / "apk/release" / item["outputFile"], outputs / "bundle/release/app-release.aab"]:
    artifact = dest / (prefix + src.suffix)
    shutil.copyfile(src, artifact)
    lines.append(f"{hashlib.sha256(artifact.read_bytes()).hexdigest()}  {artifact.name}")
(dest / "SHA256SUMS").write_text("\n".join(lines) + "\n")
shutil.copyfile(outputs / "apk/release/output-metadata.json", dest / "version.json")
for src in ["docs/release/RELEASE_NOTES.md", "docs/security/BETA_SECURITY_GATE.md", "docs/beta/KNOWN_ISSUES.md"]:
    shutil.copy(root / src, dest)
results = [ET.parse(path).getroot() for path in (root / "mod-engine/build/test-results/test").glob("TEST-*.xml")]
if not results:
    raise RuntimeError("Run :mod-engine:test before collecting the candidate")
summary = {key: sum(int(suite.get(key, 0)) for suite in results)
           for key in ("tests", "failures", "errors", "skipped")}
(dest / "engine-tests.json").write_text(json.dumps(summary, indent=2))
print("\n".join(lines))

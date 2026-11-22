# DOOM product website (local preparation)

This is a static, dependency-free website draft. The landing-page demo switches between readable rules and the actual checked-in Definition V4 JSON, previews the three sample mods, and illustrates Battery Guardian's 20% threshold locally. It does not change device settings. A theme toggle, mobile menu and per-command copy controls work in the browser. The site has no deployment manifest, analytics, public download, custom domain or release link. Pages remain `noindex, nofollow` until owner approval of public copy, legal disclosures, support contact and deployment destination.

From this directory:

```powershell
npm test
npm run build
python -m http.server 4173 --directory dist
```

Open `http://127.0.0.1:4173/` for local preview. The build copies the developer quickstart, device compatibility document and three checked-in mod definitions into `dist/`. `dist/` is generated and ignored by Git. Do not deploy this draft or use the privacy draft as a public policy until owner/legal review is complete.

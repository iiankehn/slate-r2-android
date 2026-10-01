# Releasing Slate R2

R2 has an independent monthly release line using `r2-YYYY-MM` tags. It does not share R1 tags, APKs, signing configuration, updater manifests, or package identity.

## Required secrets

- `SLATE_R2_KEYSTORE_BASE64`
- `SLATE_R2_KEY_ALIAS`
- `SLATE_R2_KEYSTORE_PASSWORD`
- `SLATE_R2_KEY_PASSWORD`

The release workflow uses a hidden monotonic `SLATE_R2_VERSION_CODE`, while the public product version remains `R2`.

## Release gates

Before publication:

1. Run unit tests, lint, and release assembly.
2. Verify ARM64 and x86_64 compatibility.
3. Exercise phone, tablet, foldable, and Googlebook Android workspace profiles.
4. Test touch, stylus, pointer, and keyboard command paths relevant to the release.
5. Round-trip representative DOCX files and compare pagination-sensitive PDF/print output.
6. Verify recovery after interruption and installation over the previous official R2 build.
7. Publish accurate feature, limitation, and fidelity notes.

The workflow produces `Slate-R2-YYYY-MM.apk`, its SHA-256 file, and `slate-r2-update.json`.

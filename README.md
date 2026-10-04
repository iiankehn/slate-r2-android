# Slate Forge historical repository

Slate Notes and Slate Forge have been unified into **Slate**, one adaptive Android writing application maintained at [`iiankehn/slate-android`](https://github.com/iiankehn/slate-android).

This repository preserves the former standalone Forge code and its history. Active development, issues, documentation, CI, downloads, and monthly releases now belong to the unified repository.

## What changed

- Slate keeps the existing `com.iiankehn.slate` package and signing identity.
- Every new document begins adaptive and appears as a Note for ordinary writing.
- Page setup, sections, headers, footers, structured tables, or page breaks promote it to Forge.
- Length alone never selects Forge.
- The Forge-derived ribbon and page workspace is now the shared interface.
- `.slx` remains the portable rich-note format; `.slxf` preserves the full word-processing model.
- ARM64 and x86_64 compatibility are verified in the unified GitHub Actions build.

## Migrate from standalone Forge

The former Forge app uses package `com.iiankehn.slater2`, so Android cannot merge its private database directly into unified Slate.

1. Open each important document in standalone Forge.
2. Export full documents as `.slxf`; use `.slx` only when portable note compatibility is preferred.
3. Install unified Slate from the [official release page](https://github.com/iiankehn/slate-android/releases/latest).
4. Import the exported files and verify them before removing the old app.

Keep the exports until migration is confirmed. Existing Slate Notes users can install unified Slate in place without uninstalling.

## Current project

- [Unified source and documentation](https://github.com/iiankehn/slate-android)
- [Downloads](https://github.com/iiankehn/slate-android/releases/latest)
- [Issues](https://github.com/iiankehn/slate-android/issues/new/choose)
- [Slate website](https://slate.iiankehn.com/)

Do not open new product work in this historical repository.

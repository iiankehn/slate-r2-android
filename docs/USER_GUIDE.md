# Slate R2 development guide

There is no public R2 user release yet. This document describes the current development build.

## Install a development APK

Run `gradle --no-daemon assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`. The package is `com.iiankehn.slater2`, so it can coexist with Slate R1.

## Current behavior

The development build retains R1's local library, autosave/recovery foundation, rich-text editing, file adapters, and adaptive phone/large-window shell while the R2 document and layout engines are built behind it.

Do not treat the current editor's format fidelity or page behavior as the final R2 contract. The roadmap tracks the replacement of legacy editing paths with the paginated R2 engine.

## Reporting development problems

Include the commit, device, Android version, CPU architecture, form factor, input method, exact steps, expected result, and actual result. Remove private writing and personal data from attachments.

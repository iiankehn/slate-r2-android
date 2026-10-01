# Slate R2 development guide

There is no public R2 user release yet. This document describes the current development build.

## Install a development APK

Run `gradle --no-daemon assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`. The package is `com.iiankehn.slater2`, so it can coexist with Slate R1.

## Start center

R2 opens to a document-focused start center. Create a blank document, start from the report, letter, or résumé templates, open an existing DOCX/Markdown/text file, or return to a recent document. R2 does not use R1's notes-library interface.

## Word-processing workspace

The workspace provides File, Home, Insert, Layout, Review, and View ribbon tabs. The ribbon stays above the document when the software keyboard opens. The current build also includes a page canvas, ruler, word/page/character status, zoom controls, keyboard formatting shortcuts, and responsive navigation and formatting panels on larger windows.

Use the system Back action or **Start** to return from a document to the start center. File actions support opening documents, exporting text/Markdown/DOCX/PDF, sharing, printing, and checking for updates.

The current canvas uses the compatibility rich-text payload while the deterministic multi-page R2 editing and layout engines are completed. Page estimates and several advanced ribbon commands are therefore previews, not the final format-fidelity contract.

## Reporting development problems

Include the commit, device, Android version, CPU architecture, form factor, input method, exact steps, expected result, and actual result. Remove private writing and personal data from attachments.

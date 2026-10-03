# Slate R2 acceptance matrix

This matrix defines the minimum acceptance contract for every monthly R2 build. GitHub Actions performs the automated rows on every push. Physical-device smoke checks remain intentionally small because Slate is a local document editor rather than a hardware-sensitive browser.

| Target | Layout policy | Required input | Automated coverage | Release smoke check |
|---|---|---|---|---|
| Android phone | Compact ribbon, fit-width page, keyboard-safe canvas | Touch, software keyboard | Workspace policy, editing engine, pagination, import/export | Create, edit, close, reopen, export PDF |
| Android tablet | Persistent navigation, stacked pages, optional inspector | Touch, stylus, hardware keyboard | Workspace policy, page navigation, object commands | Resize picture, edit table, rotate device |
| Foldable | Width-class transition without document loss | Touch, hardware keyboard | Workspace policy transitions and durable R2 codec | Fold/unfold while editing and confirm caret/document survive |
| Googlebook Android | Full ribbon, navigation and inspector | Mouse/trackpad, hardware keyboard | Keyboard commands, object selection, ARM64/x86_64 ABI gate | Open DOCX, use shortcuts, print/export |
| ARM64 | Universal APK installation target | All supported | CI native-library inspection | Install/update over previous signed build |
| x86_64 | Universal APK installation target | Keyboard/pointer preferred | CI native-library inspection | Install/update on x86_64 Android environment |

## Automated release gates

- Unit tests for model validation, persistence, recovery payloads, editing, pagination, workspace policy, and interchange.
- Android lint and debug compilation.
- APK assembly and artifact upload.
- Native libraries, when present, must include both `arm64-v8a` and `x86_64`.
- Large-document pagination must be deterministic across repeated layout passes.

## Manual release smoke check

1. Install the candidate over the previous signed build.
2. Create a multi-page document with a list, table, picture, section break, header, and footer.
3. Close and reopen the document to verify recovery and persistence.
4. Export DOCX and PDF, then reopen the DOCX in Slate.
5. Confirm Back returns to the start center before leaving the app.


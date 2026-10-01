# Slate R2 privacy baseline

R2 is local-first. Documents, metadata, undo history, and recovery data stay in app-private storage unless the user explicitly chooses an import, export, print, or share action.

- No account is required.
- No ads, analytics, telemetry, behavioral experiments, or diagnostics upload.
- No location, contacts, microphone, or camera permission.
- Scoped Android content URIs are used instead of broad storage access.
- Android automatic backup remains disabled so private documents are not silently copied to cloud backup.
- Screenshot and recent-app preview blocking remains enabled while Slate is visible.
- Update checks are user initiated, restricted to the official R2 GitHub repository, checksum verified, and never run in the background.

Uninstalling R2 removes its app-private data. Users must export important documents before uninstalling.

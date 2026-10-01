# Slate R2 architecture

## Layers

1. **Document model** — platform-independent sections, blocks, text runs, page setup, styles, tables, images, and metadata.
2. **Editing engine** — immutable commands, selection, undo/redo, normalization, and transaction boundaries.
3. **Layout engine** — measurement, line breaking, pagination, columns, headers/footers, and print/PDF geometry in points.
4. **Persistence** — Room metadata, versioned document payloads, bounded recovery records, and atomic saves.
5. **Format adapters** — DOCX, Markdown, text, PDF, printing, and Android document-provider integration.
6. **Workspace UI** — Compose surfaces that adapt chrome without changing document semantics.

## Document units

The core model stores page dimensions, margins, indents, spacing, and object sizes in points. Display code converts points to pixels using zoom and density; export code consumes the same units directly. This prevents device density from changing pagination.

## Workspace profiles

| Window | Navigation | Inspector | Toolbar | Default page view |
|---|---|---|---|---|
| Phone | Destination screen | Modal sheet | Compact dock | Fit width |
| 600–839 dp | Persistent rail | Modal sheet | Scrollable ribbon | Fit width |
| 840–1199 dp | Persistent rail | Collapsible panel | Scrollable ribbon | Fit page |
| 1200+ dp | Persistent panel | Persistent panel | Full ribbon | Fit page or actual size |

Foldable and Googlebook identity are explicit signals, not guesses derived only from width. Hardware keyboard and pointer presence may change shortcuts and zoom defaults without changing saved content.

## ABI policy

The current Kotlin/Compose code is ABI-neutral. Native libraries may be introduced only when equivalent ARM64 and x86_64 artifacts are available and verified by CI.

## Compatibility bridge

The inherited rich-text model remains available during foundation work. New word-processing documents use `WordProcessingDocument`; migration is explicit and versioned rather than silently reinterpreting existing content.

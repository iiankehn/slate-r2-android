# Slate R2 architecture

## Layers

1. **Document model** — platform-independent sections, blocks, text runs, page setup, styles, tables, images, and metadata.
2. **Editing engine** — immutable commands, selection, undo/redo, normalization, and transaction boundaries.
3. **Layout engine** — measurement, line breaking, pagination, columns, headers/footers, and print/PDF geometry in points.
4. **Persistence** — Room metadata, versioned document payloads, bounded recovery records, and atomic saves.
5. **Format adapters** — DOCX, Markdown, text, PDF, printing, and Android document-provider integration.
6. **Workspace UI** — Compose surfaces that adapt chrome without changing document semantics.

The editing layer represents carets and selections with section, block, and UTF-16 offsets. Commands return new document snapshots rather than mutating blocks in place. A bounded session history groups one or more commands into an atomic undo step and clears redo history whenever a new edit branch begins.

## R2 workspace boundary

The application surface is independent from R1. It consists of a template-based start center and a document workspace with a command ribbon, page canvas, ruler, navigation pane, format inspector, and status bar. Phone layouts keep the ribbon visible above the IME; large windows add persistent side tools without changing the saved document.

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

The compatibility rich-text payload remains behind the new R2 workspace during foundation work so persistence, recovery, and basic interchange stay usable. It is an implementation bridge only: the R1 application surface is not retained. New word-processing documents use `WordProcessingDocument`; migration is explicit and versioned rather than silently reinterpreting existing content.

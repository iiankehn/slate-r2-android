# Slate R2 release notes

Slate R2 is in active foundation development and has not published an official release.

## Unreleased development build

- Added the independent adaptive R2 word-processing workspace and start center.
- Added a device-independent document model for sections, pages, columns, styled runs, lists, tables, images, headers, and footers.
- Added command-based editing with multi-paragraph replacement, formatting, named styles, lists, page breaks, page setup, and bounded undo/redo.
- Routed Compose and Android IME text changes through the R2 engine.
- Added deterministic pagination and shared page geometry for status, PDF, and Android printing.
- Added Room schema v3 with a bounded versioned R2 payload, recovery copies, corruption fallback, and automatic migration of existing rows.
- Expanded DOCX export to retain styled runs, tables, section dimensions, orientation, margins, and columns.
- Replaced text-only DOCX parsing with a bounded structural importer for runs, paragraph styles, tables, links, margins, orientation, columns, and page breaks.
- Added real table blocks and document-provider image insertion instead of inserting placeholder text.
- Added deterministic page thumbnails, page-aware canvas proportions, and live object counts in the inspector.
- Added text, Markdown, and basic DOCX import; text, Markdown, DOCX, and PDF export; share and print flows.
- Added adaptive phone, tablet, foldable, and Googlebook workspace policies plus keyboard shortcuts.
- Added CI checks for unit tests, lint, APK assembly, and ARM64/x86_64 native dependency coverage.
- Made the monthly workflow validate an unsigned build without failing when signing secrets are intentionally absent; publishing remains gated on all signing secrets.

Known blockers before the first official build are the editable multi-page canvas, direct table/image editing, richer DOCX import and embedded media, accessibility/performance audits, and the device acceptance matrix.

R2 releases will use monthly tags in the form `r2-YYYY-MM`. This naming is independent of Slate R1 and does not imply an upgrade sequence between the two products.

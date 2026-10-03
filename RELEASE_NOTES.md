# Slate R2 release notes

## Development update — object selection

- Added first-class table and picture selection state without polluting undo history.
- Added a clear Material 3 selection frame for touch and pointer workflows.
- Added Delete/Backspace removal when a selected object owns keyboard focus.
- Inserted objects now become selected immediately, and undo/redo restores their selection state.
- Moving the text caret clears object selection so text and object editing remain unambiguous.

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
- Added direct table-cell editing, row and column growth, object deletion, and undo/redo-backed object commands.
- Added real picture previews with size, wrapping, and accessibility-description controls.
- Added deterministic page thumbnails, page-aware canvas proportions, and live object counts in the inspector.
- Replaced the single-page editor with stacked editable page surfaces using deterministic layout ranges, page-aware object placement, clickable thumbnails, and active-page status.
- Added visible bulleted, numbered, and checklist markers with nine nesting levels, ribbon indent/outdent commands, paragraph indentation, and Tab/Shift+Tab keyboard behavior.
- Added next-page section breaks, inherited section setup, editable repeating headers and footers, and Backspace/Delete section-boundary removal.
- Added text, Markdown, and basic DOCX import; text, Markdown, DOCX, and PDF export; share and print flows.
- Added adaptive phone, tablet, foldable, and Googlebook workspace policies plus keyboard shortcuts.
- Added CI checks for unit tests, lint, APK assembly, and ARM64/x86_64 native dependency coverage.
- Made the monthly workflow validate an unsigned build without failing when signing secrets are intentionally absent; publishing remains gated on all signing secrets.

Known blockers before the first official build are precise page-line placement, advanced table and floating-object manipulation, richer DOCX import and embedded media, accessibility/performance audits, and the device acceptance matrix.

R2 releases will use monthly tags in the form `r2-YYYY-MM`. This naming is independent of Slate R1 and does not imply an upgrade sequence between the two products.

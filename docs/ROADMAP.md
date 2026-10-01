# Slate R2 roadmap

R2 uses capability milestones rather than treating its name as a sequence after R1.

## Foundation — in progress

- Independent application package, repository, updater, CI, and monthly release workflow
- Device-independent word-processing document model
- Page setup, paragraph/run styles, lists, tables, images, and metadata
- Adaptive workspace policy for all target form factors and inputs
- Independent start center and word-processing workspace replacing the R1-derived shell
- Persistent ribbon, page canvas, ruler, navigation, inspector, status, and zoom surfaces
- Tests for model invariants and workspace behavior

## Editing engine

- [x] Document positions, directional selections, and multi-paragraph commands
- [x] Immutable text, character-format, paragraph-style, and named-style operations
- [x] Transactional bounded undo/redo foundation
- [x] Durable recovery records and versioned R2 persistence with legacy migration
- [x] IME-safe diff routing plus keyboard formatting and undo/redo
- [x] Named style, list, page-break, and page-setup command routing
- Tabs, advanced list levels, section-boundary editing, and object selections

## Layout engine

- [x] Deterministic line breaking, pagination, columns, headers, and footers
- [x] Zoom, ruler, outline/navigation surfaces, and computed page counts
- [x] Tables and inline image measurement
- [x] Shared pagination geometry for status, PDF, and print
- Multi-page editable canvas, thumbnails, floating-object wrapping, and section-continuation rules

## Interchange

- [x] Versioned, bounded R2 storage payload
- [x] DOCX export for styled runs, tables, sections, page setup, and columns
- [x] Deterministic PDF and printing from the layout engine
- [x] Markdown and plain-text boundary adapters
- Rich DOCX import, embedded image relationships, headers/footers, and round-trip fidelity fixtures

## Release readiness

- Accessibility semantics audit and large-document performance profiling
- [x] ARM64 and x86_64 native-dependency validation in CI
- Phone, tablet, foldable, and Googlebook Android acceptance matrix
- Signed updater and monthly publication (requires repository signing secrets)

# Slate R2 roadmap

R2 uses capability milestones rather than treating its name as a sequence after R1.

Current development focus moves from core editing toward precise object and document layout behavior.

## Foundation — complete

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
- [x] Tab insertion, Tab/Shift+Tab list nesting, checklist paragraphs, and multilevel markers
- [x] Next-page section insertion, inherited setup, editable headers/footers, and boundary deletion
- [x] Object selection, focus treatment, and keyboard deletion for tables and pictures
- [x] Cross-section range replacement and interactive checklist state

## Layout engine

- [x] Deterministic line breaking, pagination, columns, headers, and footers
- [x] Zoom, ruler, outline/navigation surfaces, and computed page counts
- [x] Tables and inline image measurement
- [x] Page thumbnails and page-size-aware canvas proportions
- [x] Structured table and document-provider image insertion
- [x] Editable table cells, table growth, image previews, sizing, wrapping, descriptions, and object deletion
- [x] Shared pagination geometry for status, PDF, and print
- [x] Stacked editable pages, page-aware text ranges and objects, thumbnail navigation, and active-page status
- [x] Section-aware repeated headers and footers with phone dialog and desktop inspector editing
- [x] Direct picture resize handle and continuous/next/odd/even section starts
- [x] Floating picture positioning, overlay modes, and square-wrap text exclusion

## Interchange

- [x] Versioned, bounded R2 storage payload
- [x] DOCX export for styled runs, tables, sections, page setup, and columns
- [x] Deterministic PDF and printing from the layout engine
- [x] Markdown and plain-text boundary adapters
- [x] Structural DOCX import for styled runs, tables, links, and page setup
- [x] DOCX numbering definitions, repeating table headers, embedded image relationships, and header/footer parts
- [x] Imported embedded-picture extraction into durable private app storage
- Broader third-party DOCX fidelity fixtures remain an ongoing compatibility track

## Release readiness

- [x] Accessibility semantics pass and deterministic large-document profiling fixture
- [x] ARM64 and x86_64 native-dependency validation in CI
- [x] Phone, tablet, foldable, and Googlebook Android acceptance matrix
- Signed updater and monthly publication (requires repository signing secrets)

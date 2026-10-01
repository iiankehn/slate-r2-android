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
- Durable transaction recovery and versioned R2 persistence
- IME-safe text editing and keyboard command routing
- Named style application, paragraph controls, lists, and tabs

## Layout engine

- Line breaking, pagination, sections, columns, headers, and footers
- Zoom, rulers, page thumbnails, outline, and navigation
- Tables and image measurement with text wrapping
- Shared on-screen, PDF, and print geometry

## Interchange

- Versioned R2 storage payload
- Higher-fidelity DOCX import/export with explicit compatibility reporting
- Deterministic PDF and printing
- Markdown and plain-text boundary adapters

## Release readiness

- Accessibility and large-document performance
- ARM64 and x86_64 CI/device validation
- Phone, tablet, foldable, and Googlebook Android acceptance matrix
- Signed updater and monthly release publication

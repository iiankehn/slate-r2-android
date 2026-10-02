# Slate R2 feature matrix

Status values describe the development tree, not a public release.

| Area | Foundation | Target |
|---|---|---|
| Start center | Implemented with blank/template/open/recent flows | Search, pinned templates, cloud-provider shortcuts |
| Local documents and recovery | Room v3, versioned R2 payload, migration, bounded checkpoints | Transaction-level crash simulations |
| Page setup | Interactive size, margins, orientation, and 1–4 columns | Custom sizes and section continuation |
| Editing commands | Multi-paragraph replace/delete/format commands implemented | Tables, objects, and section-boundary editing |
| Selection and history | UI-routed logical selections with transactional undo/redo | Object and cross-section selections |
| Paragraphs and character runs | IME diffs route through the R2 engine | Tabs, language runs, and advanced typography |
| Named styles | Heading, quote, and normal command application | Style gallery, inheritance, document themes |
| Lists | Bulleted and numbered list commands | Visible markers, multilevel lists, checklists |
| Tables | Structured insertion, direct cell editing, row/column growth, deletion, undo/redo, DOCX import/export, layout measurement | Row/column removal, merge, repeat headers, resize handles, keyboard navigation |
| Images | Android document-provider insertion, durable URI permission, inline preview, resizing, wrapping modes, accessibility descriptions, deletion, undo/redo | Crop, drag positioning, and true floating-object layout |
| Pagination | Deterministic stacked editable pages, page-aware text ranges and objects, clickable thumbnails, columns, headers/footers, tables, and inline images | Precise floating-object placement and section continuation |
| DOCX | Structural paragraphs/runs/styles/tables/page setup/columns/link import and structured export | Embedded media, numbering definitions, headers/footers, fidelity fixtures |
| PDF and printing | Shared deterministic layout geometry | Font embedding and image rasterization |
| Ribbon and page workspace | Independent R2 surface implemented | Connect every advanced command to the editing engine |
| Phone workspace | Compact persistent ribbon and fit-width page | Complete compact word-processing workflow |
| Tablet/foldable | Stacked multi-page editor, ruler, persistent clickable navigation | Collapsible inspector and drag-based page objects |
| Googlebook Android | Full-width ribbon, navigation, inspector, shortcuts | Desktop-class editing and pointer behavior |
| ARM64/x86_64 | CI rejects native dependencies missing either target ABI | Physical-device acceptance coverage |

## Current limitations

The canvas now renders deterministic stacked pages with separately editable text ranges, page-aware objects, clickable thumbnails, and live current-page status while preserving one global document history. Tables support direct cell editing and growth, while pictures render from document-provider URIs and expose size, wrapping, accessibility-description, and deletion controls. Exact visual line placement, drag handles, table merge/removal commands, and true floating-object placement remain unfinished. DOCX import preserves the supported structural subset but embedded media, numbering definitions, and headers/footers still require fidelity work. These are release blockers, not hidden compatibility claims.

## Input contract

Every essential command must be available through touch and hardware keyboard. Pointer input provides hover, context menus, precise selection, and drag handles. Stylus input provides precise selection and future ink/annotation paths without replacing standard editing.

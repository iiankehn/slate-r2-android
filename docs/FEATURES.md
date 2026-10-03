# Slate R2 feature matrix

Status values describe the development tree, not a public release.

| Area | Foundation | Target |
|---|---|---|
| Start center | Implemented with blank/template/open/recent flows | Search, pinned templates, cloud-provider shortcuts |
| Local documents and recovery | Room v3, versioned R2 payload, migration, bounded checkpoints | Transaction-level crash simulations |
| Page setup | Interactive standard/custom sizes, margins, orientation, 1–4 columns, continuous/next/odd/even section starts, inherited setup, and removable boundaries | Section-specific footnote and line-number controls |
| Editing commands | Multi-paragraph and cross-section replace/delete/format commands, section insertion/boundary deletion, interactive checklists, and table/picture object selection with keyboard deletion | Tracked changes and comments |
| Selection and history | UI-routed logical selections with transactional undo/redo | Object and cross-section selections |
| Paragraphs and character runs | IME diffs route through the R2 engine; plain-text tabs and paragraph alignment render in the page editor | Tab stops, language runs, and advanced typography |
| Named styles | Heading, quote, and normal command application | Style gallery, inheritance, document themes |
| Lists | Visible bullet, numbered, and tappable checklist markers; nine nesting levels; ribbon indent/outdent; Tab/Shift+Tab nesting; undo/redo; DOCX numbering-definition export | Custom numbering-format editor and third-party numbering fidelity |
| Tables | Structured insertion, direct cell editing, row/column growth and removal, repeating headers, selectable object frame, keyboard deletion, undo/redo, DOCX import/export, layout measurement | Cell merge and richer keyboard cell navigation |
| Images | Android document-provider insertion, durable URI permission, inline preview, selectable object frame, keyboard deletion, drag resizing, wrapping modes, accessibility descriptions, DOCX embedding, PDF rasterization, and undo/redo | Crop, drag positioning, imported-media extraction, and true floating-object layout |
| Pagination | Deterministic stacked editable pages, page-aware text ranges and objects, clickable thumbnails, columns, editable repeated headers/footers, tables, inline images, and continuous/next/odd/even sections | Precise floating-object placement |
| DOCX | Structural paragraphs/runs/styles/tables/page setup/columns/link import; numbering, embedded-media, repeating-table-header, and header/footer export; header/footer import | Imported-media extraction and broader fidelity fixtures |
| PDF and printing | Shared deterministic layout geometry with image rasterization | Explicit font-file embedding controls |
| Ribbon and page workspace | Independent R2 surface implemented | Connect every advanced command to the editing engine |
| Phone workspace | Compact persistent ribbon and fit-width page | Complete compact word-processing workflow |
| Tablet/foldable | Stacked multi-page editor, ruler, persistent clickable navigation | Collapsible inspector and drag-based page objects |
| Googlebook Android | Full-width ribbon, navigation, inspector, shortcuts | Desktop-class editing and pointer behavior |
| ARM64/x86_64 | CI rejects native dependencies missing either target ABI | Physical-device acceptance coverage |

## Current limitations

The canvas renders deterministic stacked pages with separately editable text ranges, page-aware objects, clickable thumbnails, live current-page status, custom page sizes, all common section-start modes, and editable repeated headers and footers while preserving one global document history. Tables and pictures have selectable object frames with touch/mouse focus and hardware-keyboard deletion; tables support direct cell editing, growth, removal, and repeating headers, while pictures expose drag resizing, wrapping, accessibility descriptions, DOCX embedding, PDF rasterization, and deletion controls. True floating-object placement, text exclusion around positioned objects, table cell merging, and extraction of embedded pictures from imported DOCX files remain unfinished. Signed publication also remains dependent on repository signing secrets.

## Input contract

Every essential command must be available through touch and hardware keyboard. Pointer input provides hover, context menus, precise selection, and drag handles. Stylus input provides precise selection and future ink/annotation paths without replacing standard editing.

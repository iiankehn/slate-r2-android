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
| Tables | Structured 2×2 insertion, model validation, DOCX import/export, layout measurement | Cell editing, resize, merge, repeat headers, keyboard navigation |
| Images | Android document-provider insertion, durable URI permission, layout objects | Inline rendering, resize, crop, wrapping, positioning, accessibility text |
| Pagination | Deterministic pages, columns, headers/footers, tables, and inline images | Editable multi-page canvas and floating objects |
| DOCX | Structural paragraphs/runs/styles/tables/page setup/columns/link import and structured export | Embedded media, numbering definitions, headers/footers, fidelity fixtures |
| PDF and printing | Shared deterministic layout geometry | Font embedding and image rasterization |
| Ribbon and page workspace | Independent R2 surface implemented | Connect every advanced command to the editing engine |
| Phone workspace | Compact persistent ribbon and fit-width page | Complete compact word-processing workflow |
| Tablet/foldable | Page canvas, ruler, persistent navigation | Multi-page editor and collapsible inspector |
| Googlebook Android | Full-width ribbon, navigation, inspector, shortcuts | Desktop-class editing and pointer behavior |
| ARM64/x86_64 | CI rejects native dependencies missing either target ABI | Physical-device acceptance coverage |

## Current limitations

The current canvas is a single editable page surface even when the layout engine reports multiple pages; the navigation pane now exposes deterministic page thumbnails. Tables and images can be inserted and are represented as structured objects, but do not yet have direct-manipulation editors. DOCX import preserves the supported structural subset but embedded media, numbering definitions, and headers/footers still require fidelity work. These are release blockers, not hidden compatibility claims.

## Input contract

Every essential command must be available through touch and hardware keyboard. Pointer input provides hover, context menus, precise selection, and drag handles. Stylus input provides precise selection and future ink/annotation paths without replacing standard editing.

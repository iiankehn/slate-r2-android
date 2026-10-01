# Slate R2 feature matrix

Status values describe the development tree, not a public release.

| Area | Foundation | Target |
|---|---|---|
| Start center | Implemented with blank/template/open/recent flows | Search, pinned templates, cloud-provider shortcuts |
| Local documents and recovery | Implemented behind the R2 workspace | R2 document payloads and atomic recovery |
| Page setup | Model implemented | Interactive size, margins, orientation, columns |
| Editing commands | Multi-paragraph replace/delete/format commands implemented | Tables, objects, and section-boundary editing |
| Selection and history | Logical positions plus transactional undo/redo implemented | UI integration and durable recovery transactions |
| Paragraphs and character runs | Model and multi-block transformations implemented | Complete IME composition and selection engine |
| Named styles | Model and command application implemented | Style gallery, inheritance, document themes |
| Lists | Model implemented | Bullets, numbering, multilevel lists, checklists |
| Tables | Validated model implemented | Resize, merge, repeat headers, keyboard navigation |
| Images | Model implemented | Resize, crop, wrapping, positioning, accessibility text |
| Pagination | Architecture defined | Deterministic on-screen, PDF, and print layout |
| DOCX | Basic inherited adapter | Higher-fidelity OOXML import/export |
| PDF and printing | Basic inherited path | Page-accurate output from the layout engine |
| Ribbon and page workspace | Independent R2 surface implemented | Connect every advanced command to the editing engine |
| Phone workspace | Compact persistent ribbon and fit-width page | Complete compact word-processing workflow |
| Tablet/foldable | Page canvas, ruler, persistent navigation | Multi-page editor and collapsible inspector |
| Googlebook Android | Full-width ribbon, navigation, inspector, shortcuts | Desktop-class editing and pointer behavior |
| ARM64/x86_64 | ABI-neutral foundation | Verified release coverage |

## Input contract

Every essential command must be available through touch and hardware keyboard. Pointer input provides hover, context menus, precise selection, and drag handles. Stylus input provides precise selection and future ink/annotation paths without replacing standard editing.

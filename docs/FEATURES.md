# Slate R2 feature matrix

Status values describe the development tree, not a public release.

| Area | Foundation | Target |
|---|---|---|
| Local library and recovery | Inherited and compiling | R2 document payloads and atomic recovery |
| Page setup | Model implemented | Interactive size, margins, orientation, columns |
| Paragraphs and character runs | Model implemented | Complete selection and editing engine |
| Named styles | Model implemented | Style gallery, inheritance, document themes |
| Lists | Model implemented | Bullets, numbering, multilevel lists, checklists |
| Tables | Validated model implemented | Resize, merge, repeat headers, keyboard navigation |
| Images | Model implemented | Resize, crop, wrapping, positioning, accessibility text |
| Pagination | Architecture defined | Deterministic on-screen, PDF, and print layout |
| DOCX | Basic inherited adapter | Higher-fidelity OOXML import/export |
| PDF and printing | Basic inherited path | Page-accurate output from the layout engine |
| Phone workspace | R1-derived shell | Complete compact word-processing workflow |
| Tablet/foldable | Workspace policy implemented | Multi-pane editor, inspector, rulers |
| Googlebook Android | Workspace policy implemented | Full ribbon and desktop-class input behavior |
| ARM64/x86_64 | ABI-neutral foundation | Verified release coverage |

## Input contract

Every essential command must be available through touch and hardware keyboard. Pointer input provides hover, context menus, precise selection, and drag handles. Stylus input provides precise selection and future ink/annotation paths without replacing standard editing.

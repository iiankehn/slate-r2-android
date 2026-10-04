# Slate document formats

Slate uses two complementary, local-first document packages. Format versions are independent of the apps' monthly releases and commit-style build identifiers.

## `.slx` — shared Slate document

`.slx` is the interoperable rich-text format for **Slate Notes (R1)** and **Slate Forge (R2)**. It is the handoff format and the safest choice when a document must remain editable in either product.

Version 1 preserves:

- UTF-8 document text;
- bold, italic, underline, headings, quotations, links, tables and image markers;
- normalized formatting ranges and link/image metadata;
- embedded media with MIME type, extension and SHA-256 checksum;
- source product, source document identifier and revision metadata; and
- creation/update timestamps and compatibility warnings.

An `.slx` file is a bounded ZIP package containing `manifest.bin`, `document.bin`, and optional `assets/` entries. Importers reject unsafe paths, duplicate entries, oversized expanded content, unsupported versions and failed asset checksums.

Slate Notes can hand an `.slx` document directly to Slate Forge with **Continue in Slate Forge**. The transfer uses an explicit package-targeted Android intent, a narrow `FileProvider` cache path, and temporary read permission. No shared database, account, server, telemetry or cloud service is involved.

## `.slxf` — Slate Forge document

`.slxf` is the lossless native format for **Slate Forge**. It retains the complete Forge model:

- pages, custom page sizes, orientation and margins;
- sections and section-start behavior;
- multiple columns and column spacing;
- named paragraph styles and character formatting;
- nested bulleted, numbered and checklist paragraphs;
- tables with merged-cell metadata;
- headers and footers;
- inline and floating images with wrapping and offsets; and
- document author, subject, keywords and timestamps.

An `.slxf` package contains `manifest.bin`, `forge.bin`, and optional checked `assets/` entries. Slate Forge can also export a compatible `.slx` copy. Features outside the shared model are simplified only in that exported copy; the original `.slxf` document is never modified.

## Compatibility contract

| Direction | Behavior |
|---|---|
| Notes `.slx` → Forge | Rich text and embedded assets import without conversion through DOCX. |
| Forge → `.slx` | Shared content is preserved; Forge-only page layout is intentionally omitted. |
| Forge `.slxf` → Forge | Complete, lossless round trip. |
| Forge `.slxf` → Notes | Not opened directly; export an `.slx` compatibility copy first. |

Readers accept known older schema versions and reject unknown newer versions instead of guessing. New optional capabilities should be added without reinterpreting existing fields.

## MIME types

- `.slx`: `application/vnd.core.slate.slx`
- `.slxf`: `application/vnd.core.slate.slxf`


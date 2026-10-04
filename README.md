# Slate Forge by CORE

Slate Forge (R2) is a native, local-first word processor for Android. It is designed for phones, tablets, foldables, and Googlebook Android devices, with first-class touch, stylus, mouse, trackpad, and hardware-keyboard input.

> Slate Forge is a separate product, not the next version of Slate Notes. The two applications use different packages, repositories, release lines, and update channels and can be installed together.

## Project status

The Slate R2 baseline is feature-complete and in release-candidate validation. The first official signed monthly release is pending repository signing credentials and the documented installation smoke check.

Visit the [Slate website](https://slate.iiankehn.com/) to compare the distinct R1 and R2 products, open their repositories, and find official downloads.

The R2 baseline includes:

- a distinct `com.iiankehn.slater2` Android application;
- native Kotlin and Jetpack Compose targeting Android 12 and newer;
- an independent R2 start center and responsive word-processing workspace;
- a keyboard-safe command ribbon, page canvas, ruler, navigation pane, format inspector, and document status bar;
- Room v3 persistence with versioned R2 payloads, automatic legacy-row promotion, and bounded recovery checkpoints;
- a device-independent paginated document model with sections, page setup, margins, columns, paragraphs, text runs, lists, tables, and images;
- an immutable editing engine with logical document positions, multi-paragraph selections, formatting commands, and transactional undo/redo;
- IME-safe text-diff routing, real page-break/list/page-setup commands, and Ctrl+B/I/U/Z/Y shortcuts;
- deterministic pagination shared by page counts, PDF, and Android printing;
- structured DOCX export for styled runs, tables, sections, page setup, and columns;
- deterministic workspace profiles for phone, tablet, foldable, and Googlebook Android layouts;
- explicit touch, stylus, mouse/trackpad, and hardware-keyboard capability modeling;
- architecture-neutral Kotlin code suitable for ARM64 and x86_64 devices;
- independent CI and monthly R2 release automation.
- lossless Forge-native `.slxf` documents plus shared `.slx` rich-text interchange with Slate Notes;
- local **Continue in Slate Forge** handoff through scoped Android content URIs.

## Product direction

R2 is intended to become a complete word processor rather than a larger notes app. Its work includes:

- page layout, pagination, sections, headers, footers, columns, and print-aware units;
- named styles, advanced paragraph controls, typography, lists, tabs, and spacing;
- resizable tables and images with text wrapping;
- deeper document navigation, live outline mapping, functional rulers and inspectors, and an extensible ribbon;
- higher-fidelity DOCX interchange and deterministic PDF/print output;
- responsive workspaces across all supported form factors and input methods.

See [Product](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md), [Features](docs/FEATURES.md), [Slate formats](docs/SLATE_FORMATS.md), [Visual identity](docs/BRAND.md), and [Roadmap](docs/ROADMAP.md).

## Build

The project uses JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.3.10, and Android SDK 36.

```shell
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions runs the same verification for every push and pull request and checks that any packaged native library supplies both ARM64 and x86_64 variants.

## Supported architecture

R2's application code is ABI-neutral. CI inspects the built APK and requires every packaged native dependency to include both ARM64 and x86_64 variants.

## Privacy baseline

Documents remain in app-private local storage unless the user explicitly imports, exports, shares, or prints them. R2 requires no account and includes no advertising, behavioral analytics, telemetry, or background update polling. See [Privacy](docs/PRIVACY.md).

## Contributing and reporting

- Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
- Use [GitHub Issues](https://github.com/iiankehn/slate-r2-android/issues/new/choose) for reproducible bugs and R2 feature proposals.
- Read [SECURITY.md](SECURITY.md) before reporting a vulnerability.

Never attach private writing, credentials, signing material, or unredacted personal information to a public issue.

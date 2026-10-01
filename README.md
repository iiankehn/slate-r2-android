# Slate R2 by CORE

Slate R2 is a native, local-first word processor for Android. It is designed for phones, tablets, foldables, and Googlebook Android devices, with first-class touch, stylus, mouse, trackpad, and hardware-keyboard input.

> R2 is a separate product, not the next version of Slate R1. The two applications use different packages, repositories, release lines, and update channels and can be installed together.

## Project status

R2 is in foundation development. There is no official R2 release yet.

The current foundation includes:

- a distinct `com.iiankehn.slater2` Android application;
- native Kotlin and Jetpack Compose targeting Android 12 and newer;
- the proven local library, recovery, file interchange, and adaptive UI foundation from R1;
- a device-independent paginated document model with sections, page setup, margins, columns, paragraphs, text runs, lists, tables, and images;
- deterministic workspace profiles for phone, tablet, foldable, and Googlebook Android layouts;
- explicit touch, stylus, mouse/trackpad, and hardware-keyboard capability modeling;
- architecture-neutral Kotlin code suitable for ARM64 and x86_64 devices;
- independent CI and monthly R2 release automation.

## Product direction

R2 is intended to become a complete word processor rather than a larger notes app. Its work includes:

- page layout, pagination, sections, headers, footers, columns, and print-aware units;
- named styles, advanced paragraph controls, typography, lists, tabs, and spacing;
- resizable tables and images with text wrapping;
- document navigation, outline, rulers, inspectors, and a scalable ribbon;
- higher-fidelity DOCX interchange and deterministic PDF/print output;
- responsive workspaces across all supported form factors and input methods.

See [Product](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md), [Features](docs/FEATURES.md), and [Roadmap](docs/ROADMAP.md).

## Build

The project uses JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.3.10, and Android SDK 36.

```shell
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions runs the same verification for every push and pull request.

## Supported architecture

R2 currently contains no native ABI-specific libraries, so the Kotlin/Compose application is portable across ARM64 and x86_64 Android runtimes. Any future native dependency must support both targets before it can be merged.

## Privacy baseline

Documents remain in app-private local storage unless the user explicitly imports, exports, shares, or prints them. R2 requires no account and includes no advertising, behavioral analytics, telemetry, or background update polling. See [Privacy](docs/PRIVACY.md).

## Contributing and reporting

- Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
- Use [GitHub Issues](https://github.com/iiankehn/slate-r2-android/issues/new/choose) for reproducible bugs and R2 feature proposals.
- Read [SECURITY.md](SECURITY.md) before reporting a vulnerability.

Never attach private writing, credentials, signing material, or unredacted personal information to a public issue.

# Contributing to Slate R2

Slate R2 is a local-first Android word processor. Contributions must preserve its separation from Slate R1 and support the complete R2 platform contract.

## Before changing code

1. Read [docs/PRODUCT.md](docs/PRODUCT.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), and [docs/ROADMAP.md](docs/ROADMAP.md).
2. Search existing issues and pull requests.
3. Keep document-model code independent from Compose, display density, and Android widget state.
4. Avoid dependencies that exclude ARM64 or x86_64.

## Required verification

```shell
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Pull requests should explain affected form factors, input methods, document compatibility, recovery behavior, and any format-fidelity tradeoffs.

Do not add accounts, advertising, telemetry, background document uploads, or mandatory network services.

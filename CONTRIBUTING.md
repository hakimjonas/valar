# Contributing to Valar

Thanks for your interest in contributing to Valar.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/valar.git`
3. Create a branch: `git checkout -b your-feature-branch`

## Development Setup

Requirements:
- JDK 21+
- sbt 1.10+

Build and test:
```bash
sbt compile
sbt test
```

Run tests for a specific module:
```bash
sbt valarCore/test
sbt valarMunit/test
sbt valarTranslator/test
```

Format code:
```bash
sbt scalafmtAll
```

Check formatting:
```bash
sbt scalafmtCheckAll
```

## Project Structure

```
valar/
  valar-core/        # Core validation library
  valar-munit/       # MUnit testing utilities
  valar-translator/  # i18n support
  valar-benchmarks/  # JMH benchmarks
```

## Submitting Changes

1. Ensure all tests pass: `sbt test`
2. Check formatting: `sbt scalafmtCheckAll`
3. Write clear commit messages
4. Open a pull request against `main`

## Guidelines

- Keep changes focused. One feature or fix per PR.
- Add tests for new functionality.
- Update documentation if needed.
- Follow existing code style.

## Running Benchmarks

```bash
sbt "valarBenchmarks / Jmh / run"
```

## Questions

Open an issue for questions or discussion.

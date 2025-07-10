# Valar Benchmarking Suite

Valar includes a comprehensive benchmarking strategy to ensure optimal performance across platforms and use cases.

## Benchmark Tiers

The benchmarking suite is organized into four tiers of increasing sophistication:

### Tier 1: Core Performance Benchmarks

These benchmarks cover the essential validation paths:

- Synchronous validation of simple and nested case classes
- Asynchronous validation with a mix of sync and async rules
- Valid and invalid validation scenarios

### Tier 2: Extended Benchmark Suite

These benchmarks cover more sophisticated validation scenarios:

- **Macro Derivation Overhead**: Measures the performance impact of macro-generated validators
- **Error Accumulation Scaling**: Tests performance with many validation errors
- **Observer Pattern**: Measures the overhead of using the observer pattern
- **Translator Integration**: Benchmarks i18n translation integration

### Tier 3: Cross-Platform Benchmarks

These benchmarks ensure consistent performance across platforms:

- **Native Benchmarks**: Custom timing-based benchmarks for Scala Native
- **Performance Regression Detection**: Automated benchmarking in CI
- **Platform Comparison Reports**: JVM vs Native performance analysis

### Tier 4: Advanced Benchmarking

These benchmarks measure advanced performance characteristics:

- **Memory Allocation Profiling**: Tracks GC pressure and allocation patterns
- **Concurrency Benchmarks**: Tests async validation performance
- **Stress Testing**: Measures performance with deeply nested structures

## Running Benchmarks

### JVM Benchmarks

The JVM benchmarks use JMH (Java Microbenchmark Harness) for accurate measurements:

```bash
sbt "valarBenchmarks / Jmh / run"
```

To run specific benchmarks, use a pattern:

```bash
sbt "valarBenchmarks / Jmh / run .*syncSimple.*"
```

### Native Benchmarks

The Native benchmarks use a custom timing-based framework:

```bash
sbt valarBenchmarksNative/run
```

### CI Integration

The benchmarks are integrated into CI via GitHub Actions:

- Benchmarks run automatically on PRs and main branch pushes
- Performance regressions are detected by comparing with previous runs
- Platform comparison reports are generated

## Benchmark Results

Benchmark results are available as artifacts in GitHub Actions:

- **JVM Results**: JSON format with detailed JMH metrics
- **Native Results**: CSV format with timing measurements
- **Platform Comparison**: HTML report comparing JVM and Native performance

## Adding New Benchmarks

To add new benchmarks:

1. For JVM benchmarks, add methods with `@Benchmark` annotation to one of the benchmark classes
2. For Native benchmarks, add methods to the appropriate benchmark group in `NativeBenchmarks.scala`
3. Run the benchmarks locally to verify they work correctly
4. Submit a PR to include the new benchmarks

## Performance Regression Policy

A performance regression is detected when:

- A benchmark is at least 10% slower than the previous run
- The regression is consistent across multiple runs

When a regression is detected:

1. The CI build will fail with details about the regression
2. The regression should be investigated and fixed before merging

## Benchmark Design Principles

Valar's benchmarks follow these principles:

1. **Realistic Scenarios**: Benchmarks reflect real-world usage patterns
2. **Comprehensive Coverage**: All critical paths are benchmarked
3. **Cross-Platform**: Both JVM and Native platforms are tested
4. **Regression Detection**: Changes that degrade performance are caught early
5. **Reproducibility**: Benchmarks are stable and produce consistent results
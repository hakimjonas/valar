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

- **Unified Framework**: A single module supports both JVM and Native benchmarks
- **Custom Timing Mechanism**: Native-specific timing implementation for accurate measurement
- **Platform Comparison**: Automated generation of JVM vs Native comparison reports

### Tier 4: Advanced Benchmarking

These benchmarks measure advanced performance characteristics:

- **Memory Allocation Profiling**: Tracks GC pressure and allocation patterns
- **Concurrency Benchmarks**: Tests async validation performance
- **Stress Testing**: Measures performance with deeply nested structures

## Running Benchmarks

Valar provides a unified benchmark runner that can run benchmarks, generate reports, and analyze performance trends.

### JVM Benchmarks

The JVM benchmarks use JMH (Java Microbenchmark Harness) for accurate measurements:

```bash
sbt "valarBenchmarks/Jmh/run"
```

To run specific benchmarks, use JMH directly with a pattern:

```bash
sbt "valarBenchmarks/Jmh/run .*syncSimple.*"
```

### Native Benchmarks

The Native benchmarks use a custom timing-based framework:

```bash
sbt "valarBenchmarks/run native"
```

### Generating Comparison Reports

To compare JVM and Native performance:

```bash
# First run both benchmark types
sbt "valarBenchmarks/Jmh/run -rf json -o jmh-results.json"
sbt "valarBenchmarks/run native"

# Then generate the comparison report
sbt "valarBenchmarks/run report"
```

The report will be available at `benchmark-comparison.md`.

### Visualization Dashboard

To generate an interactive visualization dashboard:

```bash
sbt "valarBenchmarks/run dashboard"
```

This will create an HTML dashboard at `benchmark-dashboard.html` with:

- Interactive bar charts comparing JVM and Native performance
- Gauge charts showing relative performance improvements
- Detailed benchmark-by-benchmark comparisons
- Filterable views by benchmark type

You can open the dashboard in any modern web browser:

```bash
# On Linux
xdg-open benchmark-dashboard.html

# On macOS
open benchmark-dashboard.html
```

### Performance Trend Analysis

To analyze historical performance trends:

```bash
sbt "valarBenchmarks/run trends"
```

### Benchmark Runner

The `BenchmarkRunner` provides a unified interface for all benchmark operations:

```bash
# Run Native benchmarks
sbt "valarBenchmarks/run native"

# Generate comparison report
sbt "valarBenchmarks/run report"

# Run all benchmarks and generate report
sbt "valarBenchmarks/run all"
```

### CI Integration

The benchmarks are integrated into CI via GitHub Actions:

- Core benchmarks run automatically on PRs to catch significant regressions
- Full benchmark suites run on the main branch for comprehensive analysis
- Performance regressions are detected by comparing with previous runs
- Platform comparison reports are generated and stored as artifacts
- Interactive visualization dashboards are created for each run
- Historical performance trends are analyzed and reported
- The CI build fails if significant performance regressions are detected

## Benchmark Results

Benchmark results are available as artifacts in GitHub Actions:

- **JVM Results**: JSON format with detailed JMH metrics
- **Native Results**: CSV format with timing measurements
- **Platform Comparison**: Markdown report comparing JVM and Native performance
- **Visualization Dashboard**: Interactive HTML dashboard with charts and graphs
- **Performance Trends**: HTML report tracking performance evolution over time

## Example Output

### JMH Results (JVM)

```
Benchmark                      Mode  Cnt    Score    Error  Units
ValarBenchmark.syncSimpleValid  avgt    5   45.281 ±  0.423  ns/op
ValarBenchmark.syncSimpleInvalid avgt   5  102.459 ±  1.234  ns/op
```

### Native Benchmark Results

```
=== Native Benchmark Results ===
Benchmark         | Avg Time (ns)
------------------|-------------
syncSimpleValid   | 38.45
syncSimpleInvalid | 89.67
```

The visualization dashboard provides:
- Bar charts comparing JVM and Native performance
- Gauge charts showing relative performance improvements
- Detailed benchmark-by-benchmark comparisons

The performance trends report provides:
- Line charts showing performance changes over time
- Trend analysis identifying improving and worsening benchmarks
- Percentage change calculations for each benchmark

## Adding New Benchmarks

To add new benchmarks:

1. For JVM benchmarks:
   - Add methods with `@Benchmark` annotation to one of the benchmark classes in `valarBenchmarks/src/main/scala/net/ghoula/valar/benchmarks/`
   - Ensure your method returns a value to prevent dead code elimination
   - Follow the naming convention: `sync`/`async` + `Simple`/`Nested` + `Valid`/`Invalid`
   - Add appropriate `@State`, `@BenchmarkMode`, and other JMH annotations

2. For Native benchmarks:
   - Add methods to the appropriate benchmark group in `NativeBenchmarks.scala`
   - Ensure your method has the same signature as the JVM version for comparison
   - Implement proper timing using the provided timing utilities

3. Test your benchmarks locally:
   ```bash
   # Test JVM benchmark
   sbt "valarBenchmarks/Jmh/run .*YourNewBenchmark.*"

   # Test Native benchmark
   sbt "valarBenchmarks/run native"
   ```

4. Update the comparison report generator if needed:
   - If your benchmark has a unique name pattern, ensure it's properly matched in `ComparisonReport.scala`

5. Submit a PR to include the new benchmarks

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

## Dependencies

The benchmarks require the following dependencies:

- JMH 1.37
- Scala 3.7.1
- OpenJDK 21+
- For Native benchmarks: clang, libgc-dev, libunwind-dev

## Performance Baseline

The following performance baselines define acceptable performance:

- Simple valid synchronous validation: < 50 ns/op
- Simple invalid synchronous validation: < 150 ns/op
- Nested valid synchronous validation: < 120 ns/op
- Nested invalid synchronous validation: < 500 ns/op
- Asynchronous validation: < 20,000 ns/op (includes Future overhead)

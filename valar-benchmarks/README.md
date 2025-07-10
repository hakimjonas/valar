# Valar Benchmarks
# Valar Benchmarking Suite
# Valar Benchmarking Suite

This module contains performance benchmarks for the Valar validation library, measuring both JVM and Native platform performance.

## Quick Start

The easiest way to run benchmarks is using the provided shell script:

```bash
# Make the script executable
chmod +x benchmark.sh

# Run the complete benchmark suite
./benchmark.sh
```

This will:
1. Run JVM benchmarks using JMH
2. Run Native benchmarks
3. Generate comparison reports
4. Create visualization dashboards
5. Analyze historical performance trends

## Using the Benchmark Script

The `benchmark.sh` script provides a simplified interface:

```bash
# Show help with all options
./benchmark.sh --help

# Run only JVM benchmarks
./benchmark.sh jvm

# Run only Native benchmarks
./benchmark.sh native

# Generate only the comparison report
./benchmark.sh report
```

### Script Options

```
Options:
  -h, --help           Show this help message
  -o, --output DIR     Save results to directory DIR
  --skip-jmh           Skip JVM benchmarks
  --skip-native        Skip Native benchmarks
  -i, --iterations N   Run Native benchmarks with N iterations
  -v, --verbose        Enable verbose output
```

## Running with SBT Directly

You can also run benchmarks directly with SBT:

```bash
# Run the complete benchmark suite
sbt 'valarBenchmarks/run'

# Run only JVM benchmarks
sbt 'valarBenchmarks/run jvm'

# Run only Native benchmarks
sbt 'valarBenchmarks/run native'

# Run with options
sbt 'valarBenchmarks/run all --skip-jmh --output-dir results'
```

## Generated Reports

After running the benchmarks, the following reports are generated:

- `benchmark-comparison.md`: Cross-platform comparison between JVM and Native
- `benchmark-dashboard.html`: Interactive visualization dashboard
- `benchmark-trends.html`: Historical performance trends analysis

## Advanced: Manual JMH Execution

If you need more control over the JMH benchmark parameters, you can run JMH directly:

```bash
sbt 'valarBenchmarks / Jmh / run -f 1 -wi 5 -i 5 -rf json -rff jvm-benchmark-results.json'
```

Then run the rest of the suite:

```bash
sbt 'valarBenchmarks/run --skip-jmh'
```

## Troubleshooting

If you encounter issues:

1. Try running with `--verbose` for more detailed output
2. Make sure you have sufficient memory available (JMH needs at least 2GB)
3. If JMH hangs, you can skip it with `--skip-jmh` and run it separately
4. Use `./benchmark.sh --help` to see all available options
This project contains benchmarking utilities for the Valar validation library, providing tools to measure performance across JVM and Native platforms.

## Quick Start

Run the complete benchmarking workflow with a single command:

```bash
sbt 'valarBenchmarks / runMain net.ghoula.valar.benchmarks.BenchmarkCommand'
```

This will execute all benchmarks and generate all reports in the correct sequence.

## Benchmark Components

The benchmarking suite is composed of several components:

1. **JVM Benchmarks**: Using JMH (Java Microbenchmark Harness)
2. **Native Benchmarks**: Using custom timing logic for Scala Native
3. **Comparison Reports**: Markdown reports comparing JVM and Native performance
4. **Visualization Dashboard**: Interactive HTML charts and graphs
5. **Historical Trends**: Analysis of performance changes over time

## Running Individual Components

You can run individual components of the benchmarking suite:

```bash
# Run JVM benchmarks only
sbt 'valarBenchmarks / Jmh / run'

# Run Native benchmarks only
sbt 'valarBenchmarks / runMain net.ghoula.valar.benchmarks.BenchmarkRunner native'

# Generate comparison report
sbt 'valarBenchmarks / runMain net.ghoula.valar.benchmarks.BenchmarkRunner report'

# Generate visualization dashboard
sbt 'valarBenchmarks / runMain net.ghoula.valar.benchmarks.BenchmarkRunner dashboard'

# Analyze historical trends
sbt 'valarBenchmarks / runMain net.ghoula.valar.benchmarks.BenchmarkRunner trends'

# Run all components (same as using BenchmarkCommand)
sbt 'valarBenchmarks / runMain net.ghoula.valar.benchmarks.BenchmarkRunner all'
```

## Generated Reports

After running the benchmarks, you'll find these reports in the project root:

- `benchmark-comparison.md`: Detailed comparison between JVM and Native performance
- `benchmark-dashboard.html`: Interactive visualizations of benchmark results
- `benchmark-trends.html`: Analysis of performance changes over time

## Benchmark History

Historical benchmark data is stored in the `benchmark-history` directory. This data is used to generate performance trend reports.

## Adding New Benchmarks

To add new benchmarks:

1. For JVM benchmarks, add new methods to the JMH benchmark classes
2. For Native benchmarks, add new methods to the `NativeBenchmarks.scala` file

Ensure that benchmark names are consistent across platforms for accurate comparisons.
This module contains benchmarks for the Valar validation library. The benchmarks measure the performance of critical validation paths to help identify performance characteristics and potential optimizations.

## Overview

Valar includes a comprehensive benchmarking strategy to ensure optimal performance across platforms and use cases. The benchmarking suite is organized into four tiers of increasing sophistication:

### Tier 1: Core Performance Benchmarks

These benchmarks cover the essential validation paths:

- **Synchronous validation** of simple and nested case classes
- **Asynchronous validation** with a mix of sync and async rules
- **Valid and invalid data paths** to understand performance differences

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

## Benchmark Results

Based on the latest run (JDK 21.0.7, OpenJDK 64-Bit Server VM):

| Benchmark            | Mode | Score      | Error       | Units |
|----------------------|------|------------|-------------|-------|
| `syncSimpleValid`    | avgt | 44.628     | ± 6.746     | ns/op |
| `syncSimpleInvalid`  | avgt | 149.155    | ± 7.124     | ns/op |
| `syncNestedValid`    | avgt | 108.968    | ± 7.300     | ns/op |
| `syncNestedInvalid`  | avgt | 449.783    | ± 18.373    | ns/op |
| `asyncSimpleValid`   | avgt | 13,212.036 | ± 1,114.597 | ns/op |
| `asyncSimpleInvalid` | avgt | 13,465.022 | ± 214.379   | ns/op |
| `asyncNestedValid`   | avgt | 14,513.056 | ± 1,023.942 | ns/op |
| `asyncNestedInvalid` | avgt | 15,432.503 | ± 2,592.103 | ns/op |

## Performance Analysis

### 🚀 Synchronous Performance is Excellent

The validation for simple, valid objects completes in **~45 nanoseconds**. This is incredibly fast and proves that for the "happy path," the library adds negligible overhead. The slightly higher numbers for invalid and nested cases (~150–450 ns) are also excellent and are expected, as they account for:

- Creation of `ValidationError` objects for invalid cases
- Recursive validation calls for nested structures
- Error accumulation logic

**Key takeaway**: Synchronous validation is extremely fast with minimal overhead.

### ⚡ Asynchronous Performance is As Expected

The async benchmarks show results in the **~13–16 microsecond range** (13,000-16,000 ns). This is excellent and exactly what we should expect. The "cost" here is not from our validation logic but from the inherent overhead of:

- Creating `Future` instances
- Managing the `ExecutionContext`
- The `Await.result` call in the benchmark (blocking on async results)

**Key takeaway**: Our async logic is efficient and correctly builds on Scala's non-blocking primitives without introducing performance bottlenecks.

### Summary

- **Sync validation**: Negligible overhead, perfect for high-throughput scenarios
- **Async validation**: Adds only the expected Future abstraction overhead
- **Valid vs Invalid**: Invalid cases show expected slight overhead due to error object creation
- **Simple vs Nested**: Nested validation scales linearly with complexity

The results confirm that Valar introduces no significant performance penalties beyond what's inherent to the chosen execution model (sync vs. async).

## Running Benchmarks

Valar provides a unified benchmark runner that can run benchmarks, generate reports, and analyze performance trends.
# Valar Benchmarking Suite

This module contains performance benchmarks for the Valar validation library, measuring both JVM and Native platform performance.

## Running Benchmarks

You can run the complete benchmark suite with a single command:

```bash
sbt 'valarBenchmarks/run'
```

This will:
1. Run JVM benchmarks using JMH
2. Run Native benchmarks
3. Generate comparison reports

## Command-line Options

The benchmark command supports several options:

```bash
sbt 'valarBenchmarks/run [options]'
```

Available options:

- `--skip-jmh`: Skip JVM benchmarks
- `--skip-native`: Skip Native benchmarks
- `--output-dir <path>`: Directory for output files (default: ".")
- `--iterations <number>`: Number of iterations for Native benchmarks (default: 1000)
- `--verbose`: Enable verbose output

## Examples

```bash
# Run only Native benchmarks, skip JMH
sbt 'valarBenchmarks/run --skip-jmh'

# Run with more iterations for Native benchmarks
sbt 'valarBenchmarks/run --iterations 5000'

# Save results to a specific directory
sbt 'valarBenchmarks/run --output-dir ./benchmark-results'
```

## Troubleshooting

If you encounter issues with JMH benchmarks hanging or entering infinite loops, try running JMH directly with:

```bash
sbt 'valarBenchmarks/Jmh/run -f 1 -wi 5 -i 5 -rf json -rff jvm-benchmark-results.json net.ghoula.valar.benchmarks.ValarBenchmark.*'
```

Then run the rest of the suite:

```bash
sbt 'valarBenchmarks/run --skip-jmh'
```

## JMH Options Explained

When running JMH benchmarks directly, you can customize various parameters:

- `-f <num>`: Number of forks to use
- `-wi <num>`: Number of warmup iterations
- `-i <num>`: Number of measurement iterations
- `-w <time>`: Warmup time per iteration
- `-r <time>`: Measurement time per iteration
- `-rf <format>`: Results format (json, csv, etc.)
- `-rff <file>`: Results file path

Example with extensive customization:

```bash
sbt 'valarBenchmarks/Jmh/run -f 2 -wi 10 -i 10 -w 2s -r 2s -rf json -rff results.json net.ghoula.valar.benchmarks.ValarBenchmark.*'
```
### JVM Benchmarks

The JVM benchmarks use JMH (Java Microbenchmark Harness) for accurate measurements:

```bash
sbt "valarBenchmarks / run jvm"
```

To run specific benchmarks, use JMH directly with a pattern:

```bash
sbt "valarBenchmarks / Jmh / run .*syncSimple.*"

# Run only sync benchmarks
sbt "valarBenchmarks / Jmh / run .*sync.*"

# Run only async benchmarks
sbt "valarBenchmarks / Jmh / run .*async.*"

# Run only valid cases
sbt "valarBenchmarks / Jmh / run .*Valid.*"
```

### Native Benchmarks

The Native benchmarks use a custom timing-based framework:

```bash
sbt "valarBenchmarks / run native"
```

### Generating Reports

To generate a cross-platform comparison report:

```bash
sbt "valarBenchmarks / run report"
```

### Visualization Dashboard

To generate an interactive visualization dashboard:

```bash
sbt "valarBenchmarks / run dashboard"
```

### Performance Trend Analysis

To analyze historical performance trends:

```bash
sbt "valarBenchmarks / run trends"
```

### Customize JMH Parameters
```bash
# Run with custom iterations and warmup
sbt "valarBenchmarks / Jmh / run -i 10 -wi 5 -f 2"

# Run with different output format
sbt "valarBenchmarks / Jmh / run -rf json"

# List available benchmarks
sbt "valarBenchmarks / Jmh / run -l"
```
## Benchmark Configuration
The benchmarks are configured with:
- **Warmup**: five iterations, 1 second each
- **Measurement**: five iterations, 1 second each
- **Fork**: 1 fork
- **Mode**: Average time (ns/op)
- **Threads**: 1 thread

## Test Data
The benchmarks use the following test models:
``` scala
case class SimpleUser(name: String, age: Int)
case class NestedCompany(name: String, owner: SimpleUser)
```
With validation rules:
- must be non-empty `name`
- must be non-negative `age`

## Understanding Results
- **ns/op**: Nanoseconds per operation (lower is better)
- **Error**: 99.9% confidence interval
- **Mode avgt**: Average time across all iterations

## Profiling
For deeper performance analysis, you can use JMH's built-in profilers:
``` bash
# CPU profiling
sbt "valarBenchmarks / Jmh / run -prof comp"

# Memory allocation profiling  
sbt "valarBenchmarks / Jmh / run -prof gc"

# Stack profiling
sbt "valarBenchmarks / Jmh / run -prof stack"
```
## Adding New Benchmarks
To add new benchmarks:
1. Add your benchmark method to `ValarBenchmark.scala`
2. Annotate it with `@Benchmark`
3. Ensure it returns a meaningful value to prevent dead code elimination
4. Follow the existing naming conventions (`sync`/`async` + `Simple`/`Nested` + `Valid`/`Invalid`)

## Dependencies
- JMH 1.37
- Scala 3.7.1
- OpenJDK 21+

## CI Integration

The benchmarks are integrated into CI via GitHub Actions:

- Benchmarks run automatically on PRs and main branch pushes
- Performance regressions are detected by comparing with previous runs
- Platform comparison reports are generated automatically
- Interactive visualization dashboards are created for each run
- Historical performance trends are analyzed and reported
- The CI build fails if significant performance regressions are detected

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

## Notes
- Results may vary based on JVM version, hardware, and system load
- Always run benchmarks multiple times to ensure consistency
- Consider JVM warm-up effects when interpreting results
- The async benchmarks include overhead, which inflates the numbers compared to pure async execution `Await.result`

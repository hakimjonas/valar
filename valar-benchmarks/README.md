# Valar Benchmarks

This module contains JMH (Java Microbenchmark Harness) benchmarks for the Valar validation library. The benchmarks measure the performance of critical validation paths to help identify performance characteristics and potential optimizations.

## Overview

The benchmark suite covers:
- **Synchronous validation** of simple and nested case classes
- **Asynchronous validation** with a mix of sync and async rules
- **Valid and invalid data paths** to understand performance differences

## Benchmark Results

Based on the latest run (JDK 21.0.8, OpenJDK 64-Bit Server VM):

| Benchmark            | Mode | Score      | Error        | Units |
|----------------------|------|------------|--------------|-------|
| `syncSimpleValid`    | avgt | 60.858     | ± 0.743      | ns/op |
| `syncSimpleInvalid`  | avgt | 171.782    | ± 1.806      | ns/op |
| `syncNestedValid`    | avgt | 115.872    | ± 3.227      | ns/op |
| `syncNestedInvalid`  | avgt | 431.439    | ± 10.251     | ns/op |
| `asyncSimpleValid`   | avgt | 17,656.897 | ± 444.704    | ns/op |
| `asyncSimpleInvalid` | avgt | 17,704.613 | ± 254.509    | ns/op |
| `asyncNestedValid`   | avgt | 21,292.686 | ± 542.063    | ns/op |
| `asyncNestedInvalid` | avgt | 26,748.345 | ± 10,286.717 | ns/op |

## Performance Analysis

### Synchronous Validation Performance

The validation for simple, valid objects completes in **~61 nanoseconds**. Invalid and nested cases show higher execution times (~172–431 ns), which can be attributed to:

- Creation of `ValidationError` objects for invalid cases
- Recursive validation calls for nested structures
- Error accumulation logic

### Asynchronous Validation Performance

The async benchmarks show results in the **~18–27 microsecond range** (18,000–27,000 ns). The overhead observed includes:

- Creating `Future` instances
- Managing the `ExecutionContext`
- The `Await.result` call in the benchmark (blocking on async results)

### Summary

The benchmark results show the following patterns:

- **Sync validation**: Execution times range from 61ns for simple valid cases to 431ns for nested invalid cases
- **Async validation**: Execution times range from 17.7μs to 26.7μs across different scenarios
- **Valid vs Invalid**: Invalid cases consistently show higher execution times due to error object creation
- **Simple vs Nested**: Nested validation shows increased execution time proportional to structural complexity

These measurements represent the current performance characteristics under the specified test conditions.

## Running Benchmarks

### Run All Benchmarks
```bash
sbt "valarBenchmarks / Jmh / run"
```
```
### Run Specific Benchmarks
``` bash
# Run only sync benchmarks
sbt "valarBenchmarks / Jmh / run .*sync.*"

# Run only async benchmarks
sbt "valarBenchmarks / Jmh / run .*async.*"

# Run only valid cases
sbt "valarBenchmarks / Jmh / run .*Valid.*"
```
### Customize Benchmark Parameters
``` bash
# Run with custom iterations and warmup
sbt "valarBenchmarks / Jmh / run -i 10 -wi 5 -f 2"

# Run with different output format
sbt "valarBenchmarks / Jmh / run -rf json"
```
### List Available Benchmarks
``` bash
sbt "valarBenchmarks / Jmh / run -l"
```
## Benchmark Configuration
The benchmarks are configured with:
- : five iterations, 1 second each **Warmup**
- : five iterations, 1 second each **Measurement**
- : 1 fork **Fork**
- : Average time (ns/op) **Mode**
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
4. Follow the existing naming conventions (`sync`/`async` + `Simple`/`Nested` + /`Invalid`) `Valid`

## Dependencies
- JMH 1.37
- Scala 3.7.2
- OpenJDK 21+

## Notes
- Results may vary based on JVM version, hardware, and system load
- Always run benchmarks multiple times to ensure consistency
- Consider JVM warm-up effects when interpreting results
- The async benchmarks include overhead, which inflates the numbers compared to pure async execution `Await.result`

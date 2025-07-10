# Valar Performance Comparison Report

Generated on 2025-07-10T17:50:57.226817794

## Performance Comparison: JVM vs Native

| Benchmark | JVM (ns) | Native (ns) | Native vs JVM |
|-----------|----------|-------------|---------------|
| deepNestingPerformance | 984,55 | 13265,16 | 1247,3% slower |
| deepNestingPerformanceWithErrors | 3524,83 | 11170,57 | 216,9% slower |
| errorAccumulationScaling | 45162,74 | 118193,44 | 161,7% slower |
| errorAccumulationScalingValid | 1164,98 | 9401,31 | 707,0% slower |
| macroDerivationOverhead | 475,60 | 8723,70 | 1734,2% slower |
| macroDerivationOverheadInvalid | 1161,04 | 7889,84 | 579,6% slower |
| noObserverBaseline | 53,85 | 428,62 | 695,9% slower |
| observerPattern | 61,94 | 1443,45 | 2230,4% slower |
| observerPatternInvalid | 219,29 | 1947,03 | 787,9% slower |
| syncNestedInvalid | 458,29 | 16655,43 | 3534,3% slower |
| syncNestedValid | 171,21 | 11599,04 | 6674,8% slower |
| syncSimpleInvalid | 201,19 | 10999,90 | 5367,4% slower |
| syncSimpleValid | 54,14 | 9642,17 | 17710,7% slower |
| translatorIntegration | 269,69 | 2509,59 | 830,5% slower |
| translatorIntegrationValid | 58,73 | 491,40 | 736,7% slower |

## JVM-only Benchmarks

| Benchmark | Time (ns) |
|-----------|-----------|
| asyncNestedInvalid | 19599,79 |
| asyncNestedValid | 17211,78 |
| asyncSimpleInvalid | 15035,76 |
| asyncSimpleValid | 14507,01 |
| concurrentValidation | 27678,34 |
| concurrentValidationWithErrors | 27201,75 |
| memoryAllocationProfile | 149,79 |
| memoryAllocationProfileInvalid | 218,89 |

## Summary

This report compares the performance of Valar validation operations across JVM and Native platforms.
Lower execution times are better. The 'Native vs JVM' column shows the relative performance difference.

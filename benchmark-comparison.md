# Valar Performance Comparison Report

Generated on 2025-07-10T15:29:00.557186581

## Performance Comparison: JVM vs Native

| Benchmark | JVM (ns) | Native (ns) | Native vs JVM |
|-----------|----------|-------------|---------------|
| deepNestingPerformance | 1102,66 | 12670,68 | 1049,1% slower |
| deepNestingPerformanceWithErrors | 3441,46 | 13443,36 | 290,6% slower |
| errorAccumulationScaling | 41496,64 | 54143,73 | 30,5% slower |
| errorAccumulationScalingValid | 1290,68 | 6484,05 | 402,4% slower |
| macroDerivationOverhead | 455,91 | 3813,12 | 736,4% slower |
| macroDerivationOverheadInvalid | 1186,58 | 7367,06 | 520,9% slower |
| noObserverBaseline | 49,62 | 229,45 | 362,4% slower |
| observerPattern | 58,32 | 692,16 | 1086,9% slower |
| observerPatternInvalid | 190,20 | 514,53 | 170,5% slower |
| syncNestedInvalid | 367,62 | 6549,01 | 1681,5% slower |
| syncNestedValid | 123,51 | 8899,19 | 7105,2% slower |
| syncSimpleInvalid | 183,39 | 2742,79 | 1395,6% slower |
| syncSimpleValid | 50,08 | 4197,82 | 8281,5% slower |
| translatorIntegration | 283,45 | 1251,93 | 341,7% slower |
| translatorIntegrationValid | 51,03 | 245,58 | 381,3% slower |

## JVM-only Benchmarks

| Benchmark | Time (ns) |
|-----------|-----------|
| asyncNestedInvalid | 20584,24 |
| asyncNestedValid | 18132,53 |
| asyncSimpleInvalid | 15821,97 |
| asyncSimpleValid | 15226,03 |
| concurrentValidation | 40152,86 |
| concurrentValidationWithErrors | 38096,54 |
| memoryAllocationProfile | 153,31 |
| memoryAllocationProfileInvalid | 223,06 |

## Summary

This report compares the performance of Valar validation operations across JVM and Native platforms.
Lower execution times are better. The 'Native vs JVM' column shows the relative performance difference.

#!/bin/bash

echo "🚀 Valar Complete Benchmark Suite"
echo "================================="

# Step 1: Run JMH benchmarks properly
echo "Step 1: Running JMH benchmarks..."
sbt "valarBenchmarks/Jmh/run -rf json -rff jvm-benchmark-results.json"

# Step 2: Run other benchmarks
echo "Step 2: Running native and other benchmarks..."  
sbt "valarBenchmarks/run all --skip-jmh"

echo "🎉 All benchmarks completed!"
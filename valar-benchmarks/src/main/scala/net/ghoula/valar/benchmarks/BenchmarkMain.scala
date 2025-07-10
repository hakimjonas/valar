package net.ghoula.valar.benchmarks

/** Main entry point for Valar benchmarks.
  *
  * This is the default main class that will be run when executing the jar file.
  */
object BenchmarkMain {
  def main(args: Array[String]): Unit = {
    println("Valar Benchmarking Suite")
    println("======================")
    println()
    println("Options:")
    println("  jvm       Run JVM benchmarks")
    println("  native    Run Native benchmarks")
    println("  report    Generate comparison report")
    println("  dashboard Generate visualization dashboard")
    println("  trends    Analyze performance trends")
    println("  all       Run full benchmark suite (default)")
    println()
    println("Additional options:")
    println("  --skip-jmh        Skip JVM benchmarks")
    println("  --skip-native     Skip Native benchmarks")
    println("  --output-dir DIR  Output directory for results")
    println("  --iterations N    Number of Native benchmark iterations")
    println("  --verbose         Enable verbose output")
    println()

    // Run the benchmark suite
    BenchmarkCommand.main(args)
  }
}

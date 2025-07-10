package net.ghoula.valar.benchmarks

/** Unified benchmark runner for both JVM and Native platforms.
  *
  * This runner provides a single entry point for running benchmarks on either platform. It
  * delegates to the appropriate benchmark implementation based on the platform argument.
  *
  * Usage:
  *   - JVM: `sbt 'valarBenchmarks / run jvm'`
  *   - Native: `sbt 'valarBenchmarks / run native'`
  *   - All: `sbt 'valarBenchmarks / run all'`
  */
object BenchmarkRunner {
  def main(args: Array[String]): Unit = {
    val platform = args.headOption.getOrElse("all")

    platform match {
      case "jvm" =>
        println("Running JVM benchmarks with JMH...")
        // Could shell out to JMH or provide instructions
        println("Run: sbt 'valarBenchmarks / Jmh / run'")

      case "native" =>
        println("Running Native benchmarks...")
        dNativeBenchmarks.main(args.tail)

      case "all" =>
        println("Running both JVM and Native benchmarks...")
        println("JVM: sbt 'valarBenchmarks / Jmh / run'")
        println("Native: sbt 'valarBenchmarks / run native'")

      case _ =>
        println("Usage: sbt 'valarBenchmarks / run [jvm|native|all]'")
    }
  }
}

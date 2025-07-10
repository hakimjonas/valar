package net.ghoula.valar.benchmarks

import scala.util.{Failure, Success, Try}

/** A simplified entry point for all benchmarking operations.
  *
  * This object provides a command-line interface for running different types of benchmarks.
  */
object BenchmarkSuite {

  /** Available benchmark types */
  sealed trait BenchmarkType
  case object JvmBenchmark extends BenchmarkType
  case object NativeBenchmark extends BenchmarkType
  case object ComparisonReportType extends BenchmarkType
  case object Dashboard extends BenchmarkType
  case object TrendAnalysis extends BenchmarkType
  case object FullSuite extends BenchmarkType

  /** Main entry point that runs benchmarks based on the specified type.
    *
    * @param args
    *   Command-line arguments with benchmark type as first argument
    */
  def main(args: Array[String]): Unit = {
    val benchmarkType = parseBenchmarkType(args.headOption.getOrElse("all"))

    println("Valar Benchmarking Suite")
    println("=======================\n")

    runBenchmark(benchmarkType, args.tail) match {
      case Success(message) =>
        println("\n✅ Benchmark completed successfully.")
        println(message)
        System.exit(0)
      case Failure(error) =>
        println(s"\n❌ Benchmark failed: ${error.getMessage}")
        error.printStackTrace()
        System.exit(1)
    }
  }

  /** Parse the benchmark type from a string argument.
    *
    * @param arg
    *   The benchmark type as a string
    * @return
    *   The corresponding BenchmarkType
    */
  private def parseBenchmarkType(arg: String): BenchmarkType = arg match {
    case "jvm" => JvmBenchmark
    case "native" => NativeBenchmark
    case "report" => ComparisonReportType
    case "dashboard" => Dashboard
    case "trends" => TrendAnalysis
    case "all" => FullSuite
    case _ =>
      println(s"Unknown benchmark type: $arg, defaulting to 'all'")
      FullSuite
  }

  /** Run the specified benchmark type.
    *
    * @param benchmarkType
    *   The type of benchmark to run
    * @param args
    *   Additional arguments for the benchmark
    * @return
    *   A Try containing the result message
    */
  def runBenchmark(benchmarkType: BenchmarkType, args: Array[String] = Array.empty): Try[String] = {
    benchmarkType match {
      case JvmBenchmark =>
        println("Running JVM benchmarks with JMH...")
        // Create a BenchmarkCommand.BenchmarkConfig with default values
        val config = BenchmarkCommand.BenchmarkConfig(
          skipJmh = false,
          skipNative = true,
          outputDir = ".",
          iterations = 1000,
          verbose = false
        )
        BenchmarkCommand.runJmhBenchmarks(config)

      case NativeBenchmark =>
        println("Running Native benchmarks...")
        Try {
          NativeBenchmarks.main(args)
          "Native benchmarks completed successfully"
        }

      case ComparisonReportType =>
        println("Generating cross-platform comparison report...")
        ComparisonReport.generateReport().map { reportPath =>
          s"Report generated successfully: $reportPath"
        }

      case Dashboard =>
        println("Generating benchmark visualization dashboard...")
        visualization.BenchmarkDashboard.generateDashboard().map { dashboardPath =>
          s"Dashboard generated successfully: $dashboardPath"
        }

      case TrendAnalysis =>
        println("Analyzing historical performance trends...")
        trends.HistoricalTrends.analyzePerformanceTrends().map { reportPath =>
          s"Trend analysis report generated successfully: $reportPath"
        }

      case FullSuite =>
        println("Running full benchmark suite...")
        // Convert BenchmarkConfig to BenchmarkCommand.BenchmarkConfig
        val localConfig = BenchmarkConfig.fromArgs(args)
        val config = BenchmarkCommand.BenchmarkConfig(
          skipJmh = localConfig.skipJmh,
          skipNative = localConfig.skipNative,
          outputDir = localConfig.outputDir,
          iterations = localConfig.iterations,
          verbose = localConfig.verbose
        )
        BenchmarkCommand.runFullBenchmarkSuite(config)
    }
  }
}

/** A simple configuration class for benchmark options.
  *
  * This is a simplified version of what's in BenchmarkCommand.
  */
case class BenchmarkConfig(
  skipJmh: Boolean = false,
  skipNative: Boolean = false,
  outputDir: String = ".",
  iterations: Int = 1000,
  verbose: Boolean = false
)

/** Companion object for BenchmarkConfig */
object BenchmarkConfig {

  /** Create a BenchmarkConfig from command-line arguments
    *
    * @param args
    *   Command-line arguments
    * @return
    *   A BenchmarkConfig instance
    */
  def fromArgs(args: Array[String]): BenchmarkConfig = {
    // Tail-recursive implementation to avoid mutable state
    @scala.annotation.tailrec
    def parseArg(index: Int, currentConfig: BenchmarkConfig): BenchmarkConfig = {
      if (index >= args.length) {
        currentConfig
      } else {
        args(index) match {
          case "--skip-jmh" =>
            parseArg(index + 1, currentConfig.copy(skipJmh = true))
          case "--skip-native" =>
            parseArg(index + 1, currentConfig.copy(skipNative = true))
          case "--output-dir" if index + 1 < args.length =>
            parseArg(index + 2, currentConfig.copy(outputDir = args(index + 1)))
          case "--iterations" if index + 1 < args.length =>
            val iterations = args(index + 1).toIntOption.getOrElse(1000)
            parseArg(index + 2, currentConfig.copy(iterations = iterations))
          case "--verbose" =>
            parseArg(index + 1, currentConfig.copy(verbose = true))
          case _ =>
            parseArg(index + 1, currentConfig)
        }
      }
    }

    parseArg(0, BenchmarkConfig())
  }
}

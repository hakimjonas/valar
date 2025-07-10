package net.ghoula.valar.benchmarks

import java.io.File
import scala.util.{Failure, Success, Try}

/** A runner for JMH benchmarks that handles execution and result collection.
  *
  * This utility simplifies running JMH benchmarks from code rather than from the command line. It
  * also provides a single entry point for running benchmarks on either platform, generating
  * reports, visualizing results, and analyzing performance trends.
  *
  * Usage:
  *   - JVM: `sbt 'valarBenchmarks / run jvm'`
  *   - Native: `sbt 'valarBenchmarks / run native'`
  *   - Report: `sbt 'valarBenchmarks / run report'`
  *   - Dashboard: `sbt 'valarBenchmarks / run dashboard'`
  *   - Trends: `sbt 'valarBenchmarks / run trends'`
  *   - All: `sbt 'valarBenchmarks / run all'`
  */
object BenchmarkRunner {

  /** Main entry point that runs the JMH benchmarks.
    *
    * @return
    *   A Try containing the path to the output JSON file
    */
  def runJmhBenchmarks(): Try[String] = {
    println("🚀 Running JMH benchmarks...")
    println("This may take several minutes...")

    // Run the JMH benchmark and return the result
    runJmhBenchmark().map { resultPath =>
      println("✅ JMH benchmarks completed successfully")
      println(s"📊 Results saved to: $resultPath")
      resultPath
    }
  }

  /** Run the JMH benchmarks using JMH API directly.
    *
    * @return
    *   Try containing the path to the output JSON file or an error
    */
  private def runJmhBenchmark(): Try[String] = {
    val outputFile = "jvm-benchmark-results.json"

    // Create and start progress indicator
    val progressIndicator = ProgressIndicator.create()
    val runningProgress = progressIndicator.start()

    val benchmarkResult = Try {
      import org.openjdk.jmh.runner.Runner
      import org.openjdk.jmh.runner.options.OptionsBuilder
      import org.openjdk.jmh.results.format.ResultFormatType

      // Configure JMH options
      val options = new OptionsBuilder()
        .include(".*ValarBenchmark.*") // Include main benchmark class
        .include(".*ExtendedBenchmarks.*") // Include extended benchmarks
        .include(".*AdvancedBenchmarks.*") // Include advanced benchmarks
        .warmupIterations(5)
        .measurementIterations(5)
        .forks(1)
        .resultFormat(ResultFormatType.JSON)
        .result(outputFile)
        .build()

      // Run the benchmarks
      val runner = new Runner(options)
      runner.run()

      outputFile
    }.flatMap { resultPath =>
      // Verify the output file exists
      val resultFile = new File(resultPath)
      if (resultFile.exists()) {
        Success(resultPath)
      } else {
        // Try to find the file in common locations
        val possiblePaths = Seq(
          resultPath,
          s"valar-benchmarks/$resultPath",
          s"target/$resultPath"
        ).map(new File(_))

        possiblePaths.find(_.exists()) match {
          case Some(file) =>
            // Copy the file to the expected location
            val targetFile = new File(resultPath)
            Try {
              java.nio.file.Files
                .copy(file.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
              resultPath
            }
          case None =>
            Failure(
              new RuntimeException(
                s"Benchmark did not produce expected output file: $resultPath. Checked: ${possiblePaths.map(_.getAbsolutePath).mkString(", ")}"
              )
            )
        }
      }
    }

    // Stop the progress indicator
    runningProgress.stop()

    benchmarkResult
  }

  /** A functional progress indicator for long-running operations. */
  case class ProgressIndicator(indicators: Vector[String] = Vector("||", "/", "--", "\\")) {

    def start(): RunningProgress = {
      val shouldStop = new java.util.concurrent.atomic.AtomicBoolean(false)
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)

      val thread = new Thread(() => {
        def displayProgress(): Unit = {
          if (!shouldStop.get()) {
            val currentCount = counter.getAndIncrement()
            print(s"\rRunning JMH benchmarks ${indicators(currentCount % indicators.size)}")
            Thread.sleep(500)
            displayProgress() // Tail recursion instead of while loop with var
          } else {
            println() // Add a newline after stopping
          }
        }

        displayProgress()
      })

      thread.setDaemon(true)
      thread.start()

      RunningProgress(shouldStop, Option(thread))
    }
  }

  object ProgressIndicator {
    def create(): ProgressIndicator = ProgressIndicator()
  }

  /** Represents a running progress indicator that can be stopped. */
  case class RunningProgress(
    shouldStop: java.util.concurrent.atomic.AtomicBoolean,
    thread: Option[Thread]
  ) {
    def stop(): Unit = {
      shouldStop.set(true)
      thread.foreach { t =>
        Try(t.join(1000)) // Wait up to 1 second for the thread to finish
      }
    }
  }

  /** Main entry point that runs benchmarks based on the specified platform.
    *
    * @param args
    *   Command-line arguments with platform as first argument
    */
  def main(args: Array[String]): Unit = {
    val platform = args.headOption.getOrElse("all")

    platform match {
      case "jvm" =>
        println("Running JVM benchmarks with JMH...")
        runJmhBenchmarks() match {
          case Success(path) =>
            println(s"JMH benchmarks completed successfully: $path")
          case Failure(e) =>
            println(s"JMH benchmarks failed: ${e.getMessage}")
            e.printStackTrace()
        }

      case "native" =>
        println("Running Native benchmarks...")
        NativeBenchmarks.main(args.tail)

      case "report" =>
        println("Generating cross-platform comparison report...")
        ComparisonReport.generateReport() match {
          case Success(reportPath) =>
            println(s"Report generated successfully: $reportPath")
          case Failure(e) =>
            println(s"Failed to generate report: ${e.getMessage}")
            e.printStackTrace()
        }

      case "dashboard" =>
        // Placeholder for dashboard generation
        println("Generating benchmark visualization dashboard...")
        println("Dashboard functionality not yet implemented")

      case "trends" =>
        // Placeholder for trend analysis
        println("Analyzing historical performance trends...")
        println("Trend analysis functionality not yet implemented")

      case "all" =>
        println("Running all benchmarks and generating all reports...")
        BenchmarkCommand.runFullBenchmarkSuite() match {
          case Success(summary) =>
            println(summary)
          case Failure(e) =>
            println(s"Benchmark suite failed: ${e.getMessage}")
            e.printStackTrace()
        }

      case _ =>
        println("Usage: sbt 'valarBenchmarks / run [jvm|native|report|dashboard|trends|all]'")
    }
  }
}

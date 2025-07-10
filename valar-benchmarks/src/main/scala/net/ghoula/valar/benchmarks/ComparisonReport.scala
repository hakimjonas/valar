package net.ghoula.valar.benchmarks

import java.io.File
import scala.collection.mutable.Map as MutableMap
import scala.io.Source
import scala.util.{Failure, Success, Try}

/** Utility for generating cross-platform benchmark comparison reports.
  *
  * This utility parses JMH JSON output and Native CSV output, and generates a comparison report in
  * Markdown format.
  */
object ComparisonReport {

  /** Generates a comparison report between JVM and Native benchmark results.
    *
    * @return
    *   Success with the path to the generated report, or Failure with an exception
    */
  def generateReport(): Try[String] = {
    for {
      jvmResults <- loadJvmResults()
      nativeResults <- loadNativeResults()
      reportPath <- generateMarkdownReport(jvmResults, nativeResults)
    } yield reportPath
  }

  /** Loads JVM benchmark results from the JMH JSON output file.
    *
    * @return
    *   A map of benchmark names to execution times in nanoseconds
    */
  private def loadJvmResults(): Try[Map[String, Double]] = {
    import scala.io.Source

    // Check multiple possible locations for the results file
    val possiblePaths = Seq(
      "jvm-benchmark-results.json",
      "valar-benchmarks/jvm-benchmark-results.json",
      "./jvm-benchmark-results.json",
      new File(System.getProperty("user.dir")).getParent + "/jvm-benchmark-results.json"
    )

    val jsonFile = possiblePaths.map(new File(_)).find(_.exists())

    jsonFile match {
      case Some(file) =>
        println(s"Found JVM results at: ${file.getAbsolutePath}")
        Try {
          val jsonString = Source.fromFile(file).mkString
          val results = MutableMap[String, Double]()

          // Simple regex-based JSON parsing
          val pattern = """"benchmark"\s*:\s*"([^"]+)"[^}]+"primaryMetric"\s*:\s*\{[^}]+"score"\s*:\s*([0-9.]+)""".r

          for (m <- pattern.findAllMatchIn(jsonString)) {
            val benchmarkName = m.group(1).split('.').last
            val score = m.group(2).toDouble
            results(benchmarkName) = score
          }

          results.toMap
        }
      case None =>
        scala.util.Failure(
          new IllegalStateException(
            s"JVM benchmark results file not found. Searched: ${possiblePaths.mkString(", ")}"
          )
        )
    }
  }

  /** Loads Native benchmark results from the CSV output file.
    *
    * @return
    *   A map of benchmark names to execution times in nanoseconds
    */
  private def loadNativeResults(): Try[Map[String, Double]] = {
    val csvFile = new File("native-benchmark-results.csv")
    if (!csvFile.exists()) {
      scala.util.Failure(
        new IllegalStateException("Native benchmark results file not found. Run Native benchmarks first.")
      )
    } else {
      Try {
        val results = MutableMap[String, Double]()
        val lines = Source.fromFile(csvFile).getLines().toSeq

        // Skip header line
        for (line <- lines.drop(1)) {
          val parts = line.split(',')
          if (parts.length >= 2) {
            val benchmarkName = parts(0)
            val score = parts(1).toDouble
            results(benchmarkName) = score
          }
        }

        results.toMap
      }
    }
  }

  /** Generates a Markdown report comparing JVM and Native benchmark results.
    *
    * @param jvmResults
    *   Map of JVM benchmark names to execution times
    * @param nativeResults
    *   Map of Native benchmark names to execution times
    * @return
    *   Path to the generated report file
    */
  private def generateMarkdownReport(
    jvmResults: Map[String, Double],
    nativeResults: Map[String, Double]
  ): Try[String] = Try {
    val reportPath = "benchmark-comparison.md"
    val reportFile = new java.io.PrintWriter(reportPath)

    try {
      reportFile.println("# Valar Performance Comparison Report")
      reportFile.println(s"\nGenerated on ${java.time.LocalDateTime.now()}")

      reportFile.println("\n## Performance Comparison: JVM vs Native")
      reportFile.println("\n| Benchmark | JVM (ns) | Native (ns) | Native vs JVM |")
      reportFile.println("|-----------|----------|-------------|---------------|")

      // Find common benchmarks
      val commonBenchmarks = jvmResults.keySet.intersect(nativeResults.keySet)

      for (benchmark <- commonBenchmarks.toSeq.sorted) {
        val jvmTime = jvmResults(benchmark)
        val nativeTime = nativeResults(benchmark)
        val ratio = nativeTime / jvmTime
        val improvement =
          if (ratio < 1.0)
            f"${(1.0 - ratio) * 100}%.1f%% faster"
          else
            f"${(ratio - 1.0) * 100}%.1f%% slower"

        reportFile.println(f"| $benchmark | $jvmTime%.2f | $nativeTime%.2f | $improvement |")
      }

      // JVM-only benchmarks
      val jvmOnly = jvmResults.keySet -- nativeResults.keySet
      if (jvmOnly.nonEmpty) {
        reportFile.println("\n## JVM-only Benchmarks")
        reportFile.println("\n| Benchmark | Time (ns) |")
        reportFile.println("|-----------|-----------|")

        for (benchmark <- jvmOnly.toSeq.sorted) {
          reportFile.println(f"| $benchmark | ${jvmResults(benchmark)}%.2f |")
        }
      }

      // Native-only benchmarks
      val nativeOnly = nativeResults.keySet -- jvmResults.keySet
      if (nativeOnly.nonEmpty) {
        reportFile.println("\n## Native-only Benchmarks")
        reportFile.println("\n| Benchmark | Time (ns) |")
        reportFile.println("|-----------|-----------|")

        for (benchmark <- nativeOnly.toSeq.sorted) {
          reportFile.println(f"| $benchmark | ${nativeResults(benchmark)}%.2f |")
        }
      }

      reportFile.println("\n## Summary")
      reportFile.println(
        "\nThis report compares the performance of Valar validation operations across JVM and Native platforms."
      )
      reportFile.println(
        "Lower execution times are better. The 'Native vs JVM' column shows the relative performance difference."
      )

    } finally {
      reportFile.close()
    }

    println(s"Comparison report generated: $reportPath")
    reportPath
  }

  /** Main entry point for running the comparison report generator directly.
    *
    * @param args
    *   Command-line arguments (not used)
    */
  def main(args: Array[String]): Unit = {
    generateReport() match {
      case Success(reportPath) =>
        println(s"Successfully generated comparison report: $reportPath")
      case Failure(e) =>
        println(s"Failed to generate comparison report: ${e.getMessage}")
        e.printStackTrace()
    }
  }
}

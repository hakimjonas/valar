package net.ghoula.valar.benchmarks.trends

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import scala.collection.mutable.{ArrayBuffer, Map as MutableMap}
import scala.io.Source
import scala.util.{Failure, Success, Try}

/** Analyzes historical benchmark performance trends over time.
  *
  * This utility tracks benchmark results across multiple runs, detects performance trends, and
  * generates reports to visualize performance evolution over time.
  */
object HistoricalTrends {

  // Directory for storing historical benchmark data
  private val historyDir = "benchmark-history"

  // Maximum number of historical data points to keep
  private val maxHistoryEntries = 10

  /** Analyzes performance trends across multiple benchmark runs.
    *
    * @return
    *   Success with the path to the generated trend report, or Failure with an exception
    */
  def analyzePerformanceTrends(): Try[String] = {
    for {
      _ <- ensureHistoryDirExists()
      _ <- archiveCurrentResults()
      historyData <- loadHistoricalData()
      reportPath <- generateTrendReport(historyData)
    } yield reportPath
  }

  /** Ensures the history directory exists.
    *
    * @return
    *   Success if the directory exists or was created, Failure otherwise
    */
  private def ensureHistoryDirExists(): Try[Unit] = {
    val dir = new File(historyDir)
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        Failure(new IllegalStateException(s"Failed to create history directory: $historyDir"))
      } else {
        Success(())
      }
    } else {
      Success(())
    }
  }

  /** Archives the current benchmark results for future trend analysis.
    *
    * @return
    *   Success if archiving was successful, Failure otherwise
    */
  private def archiveCurrentResults(): Try[Unit] = Try {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    // Archive JVM results if they exist
    val jvmFile = new File("jvm-benchmark-results.json")
    if (jvmFile.exists()) {
      Files.copy(
        jvmFile.toPath(),
        Paths.get(s"$historyDir/jvm-results-$timestamp.json"),
        StandardCopyOption.REPLACE_EXISTING
      )
    }

    // Archive Native results if they exist
    val nativeFile = new File("native-benchmark-results.csv")
    if (nativeFile.exists()) {
      Files.copy(
        nativeFile.toPath(),
        Paths.get(s"$historyDir/native-results-$timestamp.csv"),
        StandardCopyOption.REPLACE_EXISTING
      )
    }

    // Clean up old history entries if we have too many
    cleanupOldHistoryEntries()
  }

  /** Cleans up old history entries to maintain a reasonable history size.
    */
  private def cleanupOldHistoryEntries(): Unit = {
    val historyFolder = new File(historyDir)
    val historyFilesOpt = Option(historyFolder.listFiles())

    historyFilesOpt.foreach { historyFiles =>
      if (historyFiles.length > maxHistoryEntries * 2) {
        // Group files by type (JVM/Native)
        val jvmFiles = historyFiles.filter(_.getName.startsWith("jvm-")).sortBy(_.lastModified())
        val nativeFiles = historyFiles.filter(_.getName.startsWith("native-")).sortBy(_.lastModified())

        // Delete oldest files if we have too many
        if (jvmFiles.length > maxHistoryEntries) {
          jvmFiles.take(jvmFiles.length - maxHistoryEntries).foreach(_.delete())
        }

        if (nativeFiles.length > maxHistoryEntries) {
          nativeFiles.take(nativeFiles.length - maxHistoryEntries).foreach(_.delete())
        }
      }
    }
  }

  /** Loads historical benchmark data from archived files.
    *
    * @return
    *   A map of benchmark names to lists of historical data points
    */
  private def loadHistoricalData(): Try[Map[String, List[HistoricalDataPoint]]] = {
    val historyFolder = new File(historyDir)
    val historyFilesOpt = Option(historyFolder.listFiles())

    historyFilesOpt match {
      case None | Some(Array()) =>
        Failure(new IllegalStateException("No historical benchmark data found. Run benchmarks first."))
      case Some(historyFiles) =>
        Try {
          val jvmFiles = historyFiles.filter(_.getName.startsWith("jvm-")).sortBy(_.lastModified())
          val nativeFiles = historyFiles.filter(_.getName.startsWith("native-")).sortBy(_.lastModified())

          // Load JVM historical data
          val jvmHistoricalData = MutableMap[String, ArrayBuffer[HistoricalDataPoint]]()
          for (file <- jvmFiles) {
            val timestamp = extractTimestampFromFilename(file.getName)
            val results = loadJvmResultsFromFile(file)

            for ((benchmark, score) <- results) {
              if (!jvmHistoricalData.contains(benchmark)) {
                jvmHistoricalData(benchmark) = ArrayBuffer[HistoricalDataPoint]()
              }
              jvmHistoricalData(benchmark) += HistoricalDataPoint(timestamp, "JVM", score)
            }
          }

          // Load Native historical data
          val nativeHistoricalData = MutableMap[String, ArrayBuffer[HistoricalDataPoint]]()
          for (file <- nativeFiles) {
            val timestamp = extractTimestampFromFilename(file.getName)
            val results = loadNativeResultsFromFile(file)

            for ((benchmark, score) <- results) {
              if (!nativeHistoricalData.contains(benchmark)) {
                nativeHistoricalData(benchmark) = ArrayBuffer[HistoricalDataPoint]()
              }
              nativeHistoricalData(benchmark) += HistoricalDataPoint(timestamp, "Native", score)
            }
          }

          // Merge JVM and Native data
          val mergedData = MutableMap[String, ArrayBuffer[HistoricalDataPoint]]()

          for ((benchmark, dataPoints) <- jvmHistoricalData) {
            mergedData(benchmark) = dataPoints
          }

          for ((benchmark, dataPoints) <- nativeHistoricalData) {
            if (mergedData.contains(benchmark)) {
              mergedData(benchmark) ++= dataPoints
            } else {
              mergedData(benchmark) = dataPoints
            }
          }

          // Convert to immutable map with sorted data points
          mergedData.map { case (benchmark, dataPoints) =>
            (benchmark, dataPoints.sortBy(_.timestamp).toList)
          }.toMap
        }
    }
  }

  /** Extracts a timestamp from a filename.
    *
    * @param filename
    *   The filename containing a timestamp in the format "yyyyMMdd_HHmmss"
    * @return
    *   The extracted timestamp as a LocalDateTime
    */
  private def extractTimestampFromFilename(filename: String): LocalDateTime = {
    val pattern = """.*-(\d{8}_\d{6})\..*""".r

    filename match {
      case pattern(timestamp) =>
        LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
      case _ =>
        // Default to epoch start if we can't parse the timestamp
        LocalDateTime.ofEpochSecond(0, 0, ZoneId.systemDefault().getRules.getOffset(LocalDateTime.now()))
    }
  }

  /** Loads JVM benchmark results from a JSON file.
    *
    * @param file
    *   The JSON file containing JVM benchmark results
    * @return
    *   A map of benchmark names to execution times
    */
  private def loadJvmResultsFromFile(file: File): Map[String, Double] = {
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

  /** Loads Native benchmark results from a CSV file.
    *
    * @param file
    *   The CSV file containing Native benchmark results
    * @return
    *   A map of benchmark names to execution times
    */
  private def loadNativeResultsFromFile(file: File): Map[String, Double] = {
    val results = MutableMap[String, Double]()
    val lines = Source.fromFile(file).getLines().toSeq

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

  /** Generates a trend analysis report from historical benchmark data.
    *
    * @param historyData
    *   Map of benchmark names to lists of historical data points
    * @return
    *   Path to the generated report file
    */
  private def generateTrendReport(historyData: Map[String, List[HistoricalDataPoint]]): Try[String] = Try {
    val reportPath = "benchmark-trends.html"
    val reportFile = new PrintWriter(reportPath)

    try {
      // Prepare data for charts
      val benchmarks = historyData.keys.toSeq.sorted

      // Generate HTML with embedded Chart.js
      reportFile.println(s"""
        |<!DOCTYPE html>
        |<html>
        |<head>
        |  <title>Valar Benchmark Trends</title>
        |  <meta charset="UTF-8">
        |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
        |  <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
        |  <style>
        |    body {
        |      font-family: Arial, sans-serif;
        |      margin: 20px;
        |      background-color: #f5f5f5;
        |    }
        |    .container {
        |      max-width: 1200px;
        |      margin: 0 auto;
        |      background-color: white;
        |      padding: 20px;
        |      border-radius: 8px;
        |      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        |    }
        |    h1, h2 {
        |      color: #333;
        |      text-align: center;
        |    }
        |    .chart-container {
        |      position: relative;
        |      margin: 20px auto;
        |      height: 400px;
        |    }
        |    .card {
        |      background-color: white;
        |      border-radius: 8px;
        |      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        |      padding: 15px;
        |      margin-bottom: 20px;
        |    }
        |    .summary {
        |      text-align: center;
        |      margin: 20px 0;
        |      padding: 15px;
        |      background-color: #f9f9f9;
        |      border-radius: 8px;
        |    }
        |    .trend-info {
        |      margin: 10px 0;
        |      padding: 10px;
        |      border-left: 4px solid #ccc;
        |    }
        |    .trend-improving {
        |      border-left-color: green;
        |    }
        |    .trend-worsening {
        |      border-left-color: red;
        |    }
        |    .trend-stable {
        |      border-left-color: blue;
        |    }
        |  </style>
        |</head>
        |<body>
        |  <div class="container">
        |    <h1>Valar Benchmark Trends</h1>
        |    <p class="summary">Generated on ${LocalDateTime
          .now()
          .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}</p>
        |    
        |    <div class="card">
        |      <h2>Performance Trends Summary</h2>
        |      <div class="summary">
        |        <p>Analyzing performance trends across ${historyData.values
          .map(_.size)
          .sum} data points for ${historyData.size} benchmarks.</p>
        |      </div>
        |      
        |      <div class="trend-analysis">
        |        ${generateTrendAnalysis(historyData)}
        |      </div>
        |    </div>
        |    
        |    ${benchmarks
          .map(benchmark => generateBenchmarkTrendChart(benchmark, historyData(benchmark)))
          .mkString("\n")}
        |    
        |  </div>
        |</body>
        |</html>
      """.stripMargin)
    } finally {
      reportFile.close()
    }

    println(s"Trend report generated: $reportPath")
    reportPath
  }

  /** Generates trend analysis HTML for the historical data.
    *
    * @param historyData
    *   Map of benchmark names to lists of historical data points
    * @return
    *   HTML string with trend analysis
    */
  private def generateTrendAnalysis(historyData: Map[String, List[HistoricalDataPoint]]): String = {
    val trends = historyData.map { case (benchmark, dataPoints) =>
      val jvmPoints = dataPoints.filter(_.platform == "JVM").sortBy(_.timestamp)
      val nativePoints = dataPoints.filter(_.platform == "Native").sortBy(_.timestamp)

      val jvmTrend = calculateTrend(jvmPoints)
      val nativeTrend = calculateTrend(nativePoints)

      (benchmark, jvmTrend, nativeTrend)
    }

    val improvingBenchmarks = trends.filter { case (_, jvmTrend, nativeTrend) =>
      jvmTrend < 0 || nativeTrend < 0
    }

    val worseningBenchmarks = trends.filter { case (_, jvmTrend, nativeTrend) =>
      jvmTrend > 0 || nativeTrend > 0
    }

    val stableBenchmarks = trends.filter { case (_, jvmTrend, nativeTrend) =>
      jvmTrend == 0 && nativeTrend == 0
    }

    val sb = new StringBuilder()

    if (improvingBenchmarks.nonEmpty) {
      sb.append("<div class=\"trend-info trend-improving\">")
      sb.append(s"<h3>Improving Benchmarks (${improvingBenchmarks.size})</h3>")
      sb.append("<ul>")
      improvingBenchmarks.foreach { case (benchmark, jvmTrend, nativeTrend) =>
        sb.append("<li>")
        sb.append(s"<strong>$benchmark</strong>: ")
        if (jvmTrend < 0) sb.append(s"JVM improving by ${formatTrend(jvmTrend)}% per run. ")
        if (nativeTrend < 0) sb.append(s"Native improving by ${formatTrend(nativeTrend)}% per run.")
        sb.append("</li>")
      }
      sb.append("</ul>")
      sb.append("</div>")
    }

    if (worseningBenchmarks.nonEmpty) {
      sb.append("<div class=\"trend-info trend-worsening\">")
      sb.append(s"<h3>Worsening Benchmarks (${worseningBenchmarks.size})</h3>")
      sb.append("<ul>")
      worseningBenchmarks.foreach { case (benchmark, jvmTrend, nativeTrend) =>
        sb.append("<li>")
        sb.append(s"<strong>$benchmark</strong>: ")
        if (jvmTrend > 0) sb.append(s"JVM worsening by ${formatTrend(jvmTrend)}% per run. ")
        if (nativeTrend > 0) sb.append(s"Native worsening by ${formatTrend(nativeTrend)}% per run.")
        sb.append("</li>")
      }
      sb.append("</ul>")
      sb.append("</div>")
    }

    if (stableBenchmarks.nonEmpty) {
      sb.append("<div class=\"trend-info trend-stable\">")
      sb.append(s"<h3>Stable Benchmarks (${stableBenchmarks.size})</h3>")
      sb.append("<p>These benchmarks show no significant trend over time.</p>")
      sb.append("</div>")
    }

    sb.toString()
  }

  /** Calculates the trend (slope) of benchmark performance over time.
    *
    * @param dataPoints
    *   List of historical data points
    * @return
    *   The trend as a percentage change per run (negative is improving)
    */
  private def calculateTrend(dataPoints: List[HistoricalDataPoint]): Double = {
    if (dataPoints.size < 2) {
      0.0
    } else {
      // Simple linear regression to find the slope
      val n = dataPoints.size
      val x = (0 until n).map(_.toDouble).toArray // Use run index as x
      val y = dataPoints.map(_.score).toArray

      // Calculate means
      val meanX = x.sum / n
      val meanY = y.sum / n

      // Calculate slope
      val numerator = (0 until n).map(i => (x(i) - meanX) * (y(i) - meanY)).sum
      val denominator = (0 until n).map(i => math.pow(x(i) - meanX, 2)).sum

      if (denominator == 0) {
        0.0
      } else {
        val slope = numerator / denominator

        // Convert to percentage change per run
        if (meanY == 0) {
          0.0
        } else {
          (slope / meanY) * 100
        }
      }
    }
  }

  /** Formats a trend value for display.
    *
    * @param trend
    *   The trend value
    * @return
    *   Formatted trend string
    */
  private def formatTrend(trend: Double): String = {
    String.format("%.2f", math.abs(trend))
  }

  /** Generates HTML for a benchmark trend chart.
    *
    * @param benchmark
    *   The benchmark name
    * @param dataPoints
    *   List of historical data points for the benchmark
    * @return
    *   HTML string with the chart
    */
  private def generateBenchmarkTrendChart(benchmark: String, dataPoints: List[HistoricalDataPoint]): String = {
    val jvmPoints = dataPoints.filter(_.platform == "JVM").sortBy(_.timestamp)
    val nativePoints = dataPoints.filter(_.platform == "Native").sortBy(_.timestamp)

    val chartId = s"chart_${benchmark.replaceAll("[^a-zA-Z0-9]", "_")}"

    // Data is directly used in the chart configuration below

    s"""
      |<div class="card">
      |  <h2>$benchmark</h2>
      |  <div class="chart-container">
      |    <canvas id="$chartId"></canvas>
      |  </div>
      |  
      |  <script>
      |    // Trend Chart for $benchmark
      |    const ctx_$chartId = document.getElementById('$chartId').getContext('2d');
      |    new Chart(ctx_$chartId, {
      |      type: 'line',
      |      data: {
      |        datasets: [
      |          {
      |            label: 'JVM',
      |            data: [${
        if (jvmPoints.nonEmpty) {
          jvmPoints
            .map(p => s"{x: '${p.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}', y: ${p.score}}")
            .mkString(", ")
        } else ""
      }],
      |            borderColor: 'rgba(54, 162, 235, 1)',
      |            backgroundColor: 'rgba(54, 162, 235, 0.2)',
      |            tension: 0.1
      |          },
      |          {
      |            label: 'Native',
      |            data: [${
        if (nativePoints.nonEmpty) {
          nativePoints
            .map(p => s"{x: '${p.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}', y: ${p.score}}")
            .mkString(", ")
        } else ""
      }],
      |            borderColor: 'rgba(255, 99, 132, 1)',
      |            backgroundColor: 'rgba(255, 99, 132, 0.2)',
      |            tension: 0.1
      |          }
      |        ]
      |      },
      |      options: {
      |        responsive: true,
      |        maintainAspectRatio: false,
      |        scales: {
      |          x: {
      |            type: 'time',
      |            time: {
      |              unit: 'day',
      |              displayFormats: {
      |                day: 'MMM dd'
      |              }
      |            },
      |            title: {
      |              display: true,
      |              text: 'Date'
      |            }
      |          },
      |          y: {
      |            beginAtZero: false,
      |            title: {
      |              display: true,
      |              text: 'Time (ns)'
      |            }
      |          }
      |        }
      |      }
      |    });
      |  </script>
      |</div>
    """.stripMargin
  }

  /** Main entry point for running the trend analysis directly.
    *
    * @param args
    *   Command-line arguments (not used)
    */
  def main(args: Array[String]): Unit = {
    analyzePerformanceTrends() match {
      case Success(reportPath) =>
        println(s"Successfully generated trend analysis report: $reportPath")
      case Failure(e) =>
        println(s"Failed to generate trend analysis report: ${e.getMessage}")
        e.printStackTrace()
    }
  }
}

/** Represents a single historical data point for a benchmark.
  *
  * @param timestamp
  *   When the benchmark was run
  * @param platform
  *   The platform (JVM or Native)
  * @param score
  *   The benchmark score (execution time in nanoseconds)
  */
case class HistoricalDataPoint(timestamp: LocalDateTime, platform: String, score: Double)

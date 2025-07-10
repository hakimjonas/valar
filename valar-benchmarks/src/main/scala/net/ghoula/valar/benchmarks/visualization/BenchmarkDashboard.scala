package net.ghoula.valar.benchmarks.visualization

import java.io.{File, PrintWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable.Map as MutableMap
import scala.io.Source
import scala.util.{Failure, Success, Try}

/** Generates HTML dashboard with interactive visualizations for benchmark results.
  *
  * This utility creates HTML reports with embedded charts using JavaScript libraries to visualize
  * benchmark performance data across platforms and over time.
  */
object BenchmarkDashboard {

  /** Generates an HTML dashboard with interactive visualizations.
    *
    * @return
    *   Success with the path to the generated dashboard, or Failure with an exception
    */
  def generateDashboard(): Try[String] = {
    for {
      jvmResults <- loadJvmResults()
      nativeResults <- loadNativeResults()
      dashboardPath <- generateHtmlDashboard(jvmResults, nativeResults)
    } yield dashboardPath
  }

  /** Loads JVM benchmark results from the JMH JSON output file.
    *
    * @return
    *   A map of benchmark names to execution times in nanoseconds
    */
  private def loadJvmResults(): Try[Map[String, Double]] = {
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
        Failure(
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
      Failure(new IllegalStateException("Native benchmark results file not found. Run Native benchmarks first."))
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

  /** Generates an HTML dashboard with interactive charts.
    *
    * @param jvmResults
    *   Map of JVM benchmark names to execution times
    * @param nativeResults
    *   Map of Native benchmark names to execution times
    * @return
    *   Path to the generated dashboard file
    */
  private def generateHtmlDashboard(
    jvmResults: Map[String, Double],
    nativeResults: Map[String, Double]
  ): Try[String] = Try {
    val dashboardPath = "benchmark-dashboard.html"
    val dashboardFile = new PrintWriter(dashboardPath)

    try {
      // Find common benchmarks for comparison
      val commonBenchmarks = jvmResults.keySet.intersect(nativeResults.keySet).toSeq.sorted

      // Prepare data for charts
      val benchmarkNames = commonBenchmarks.map(name => s"'$name'").mkString(", ")
      val jvmData = commonBenchmarks.map(name => jvmResults.getOrElse(name, 0.0)).mkString(", ")
      val nativeData = commonBenchmarks.map(name => nativeResults.getOrElse(name, 0.0)).mkString(", ")

      // Calculate improvement percentages for the gauge chart
      val improvements = commonBenchmarks.map { name =>
        val jvmTime = jvmResults.getOrElse(name, 0.0)
        val nativeTime = nativeResults.getOrElse(name, 0.0)
        if (jvmTime > 0) {
          ((jvmTime - nativeTime) / jvmTime) * 100
        } else {
          0.0
        }
      }
      val avgImprovement = if (improvements.nonEmpty) improvements.sum / improvements.size else 0.0

      // Generate HTML with embedded Chart.js
      dashboardFile.println(s"""
        |<!DOCTYPE html>
        |<html>
        |<head>
        |  <title>Valar Benchmark Dashboard</title>
        |  <meta charset="UTF-8">
        |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
        |  <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
        |  <script src="https://cdn.jsdelivr.net/npm/chartjs-gauge@0.3.0/dist/chartjs-gauge.min.js"></script>
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
        |    h1 {
        |      color: #333;
        |      text-align: center;
        |    }
        |    .chart-container {
        |      position: relative;
        |      margin: 20px auto;
        |      height: 400px;
        |    }
        |    .row {
        |      display: flex;
        |      flex-wrap: wrap;
        |      margin: 0 -10px;
        |    }
        |    .column {
        |      flex: 1;
        |      padding: 0 10px;
        |      min-width: 300px;
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
        |  </style>
        |</head>
        |<body>
        |  <div class="container">
        |    <h1>Valar Benchmark Dashboard</h1>
        |    <p class="summary">Generated on ${LocalDateTime
          .now()
          .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}</p>
        |    
        |    <div class="row">
        |      <div class="column">
        |        <div class="card">
        |          <h2>Platform Comparison</h2>
        |          <div class="chart-container">
        |            <canvas id="platformComparisonChart"></canvas>
        |          </div>
        |        </div>
        |      </div>
        |      
        |      <div class="column">
        |        <div class="card">
        |          <h2>Performance Improvement</h2>
        |          <div class="chart-container" style="height: 300px;">
        |            <canvas id="gaugeChart"></canvas>
        |          </div>
        |          <div class="summary">
        |            <p>Average Native vs JVM: ${if (avgImprovement > 0) "+" else ""}${"%.2f".format(
          avgImprovement
        )}%</p>
        |            <p>${
          if (avgImprovement > 0) "Native is faster"
          else if (avgImprovement < 0) "JVM is faster"
          else "Performance is equivalent"
        }</p>
        |          </div>
        |        </div>
        |      </div>
        |    </div>
        |    
        |    <div class="card">
        |      <h2>Benchmark Details</h2>
        |      <div class="chart-container">
        |        <canvas id="detailsChart"></canvas>
        |      </div>
        |    </div>
        |    
        |    <script>
        |      // Platform Comparison Chart
        |      const platformCtx = document.getElementById('platformComparisonChart').getContext('2d');
        |      new Chart(platformCtx, {
        |        type: 'bar',
        |        data: {
        |          labels: ['JVM', 'Native'],
        |          datasets: [{
        |            label: 'Average Execution Time (ns)',
        |            data: [
        |              ${if (jvmResults.nonEmpty) jvmResults.values.sum / jvmResults.size else 0},
        |              ${if (nativeResults.nonEmpty) nativeResults.values.sum / nativeResults.size else 0}
        |            ],
        |            backgroundColor: [
        |              'rgba(54, 162, 235, 0.7)',
        |              'rgba(255, 99, 132, 0.7)'
        |            ],
        |            borderColor: [
        |              'rgba(54, 162, 235, 1)',
        |              'rgba(255, 99, 132, 1)'
        |            ],
        |            borderWidth: 1
        |          }]
        |        },
        |        options: {
        |          responsive: true,
        |          maintainAspectRatio: false,
        |          scales: {
        |            y: {
        |              beginAtZero: true,
        |              title: {
        |                display: true,
        |                text: 'Average Time (ns)'
        |              }
        |            }
        |          }
        |        }
        |      });
        |      
        |      // Gauge Chart for Performance Improvement
        |      const gaugeCtx = document.getElementById('gaugeChart').getContext('2d');
        |      new Chart(gaugeCtx, {
        |        type: 'gauge',
        |        data: {
        |          datasets: [{
        |            value: ${avgImprovement},
        |            minValue: -50,
        |            maxValue: 50,
        |            backgroundColor: ['red', 'orange', 'yellow', 'green'],
        |            data: [-50, -25, 0, 25, 50]
        |          }]
        |        },
        |        options: {
        |          responsive: true,
        |          maintainAspectRatio: false,
        |          title: {
        |            display: true,
        |            text: 'Native Performance vs JVM (%)'
        |          }
        |        }
        |      });
        |      
        |      // Detailed Benchmark Comparison
        |      const detailsCtx = document.getElementById('detailsChart').getContext('2d');
        |      new Chart(detailsCtx, {
        |        type: 'bar',
        |        data: {
        |          labels: [${benchmarkNames}],
        |          datasets: [
        |            {
        |              label: 'JVM',
        |              data: [${jvmData}],
        |              backgroundColor: 'rgba(54, 162, 235, 0.7)',
        |              borderColor: 'rgba(54, 162, 235, 1)',
        |              borderWidth: 1
        |            },
        |            {
        |              label: 'Native',
        |              data: [${nativeData}],
        |              backgroundColor: 'rgba(255, 99, 132, 0.7)',
        |              borderColor: 'rgba(255, 99, 132, 1)',
        |              borderWidth: 1
        |            }
        |          ]
        |        },
        |        options: {
        |          responsive: true,
        |          maintainAspectRatio: false,
        |          scales: {
        |            y: {
        |              beginAtZero: true,
        |              title: {
        |                display: true,
        |                text: 'Time (ns)'
        |              }
        |            }
        |          }
        |        }
        |      });
        |    </script>
        |  </div>
        |</body>
        |</html>
      """.stripMargin)
    } finally {
      dashboardFile.close()
    }

    println(s"Dashboard generated: $dashboardPath")
    dashboardPath
  }

  /** Main entry point for running the dashboard generator directly.
    *
    * @param args
    *   Command-line arguments (not used)
    */
  def main(args: Array[String]): Unit = {
    generateDashboard() match {
      case Success(dashboardPath) =>
        println(s"Successfully generated dashboard: $dashboardPath")
      case Failure(e) =>
        println(s"Failed to generate dashboard: ${e.getMessage}")
        e.printStackTrace()
    }
  }
}

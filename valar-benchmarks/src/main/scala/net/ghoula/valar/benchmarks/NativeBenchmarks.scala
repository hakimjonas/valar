package net.ghoula.valar.benchmarks

import net.ghoula.valar.*
import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.translator.{translateErrors, Translator}

/** Native benchmarks for Valar.
  *
  * This suite provides custom timing-based benchmarks for Scala Native, measuring:
  *   - Core validation performance
  *   - Macro derivation overhead
  *   - Error accumulation scaling
  *   - Observer pattern overhead
  *   - Translator integration
  *   - Deep nesting performance
  *
  * To run these benchmarks, use the sbt command: `valarBenchmarks / run native`
  */
object dNativeBenchmarks {

  // --- Test Data and Models ---

  // Simple case class for basic validation
  case class User(name: String, age: Int)

  // Complex nested case class for macro derivation overhead benchmark
  case class Address(street: String, city: String, zipCode: String)
  case class Contact(email: String, phone: String)
  case class Department(name: String, code: String)
  case class Employee(id: String, name: String, age: Int, address: Address, contact: Contact, department: Department)

  // Large data structure for error accumulation scaling benchmark
  case class LargeDataStructure(
    fields: Vector[String],
    numbers: Vector[Int],
    nested: Vector[User]
  )

  // Deeply nested structure for stress testing
  case class Level1(value: String, next: Option[Level2])
  case class Level2(value: String, next: Option[Level3])
  case class Level3(value: String, next: Option[Level4])
  case class Level4(value: String, next: Option[Level5])
  case class Level5(value: String, next: Option[Level6])
  case class Level6(value: String, next: Option[Level7])
  case class Level7(value: String, next: Option[Level8])
  case class Level8(value: String, next: Option[Level9])
  case class Level9(value: String, next: Option[Level10])
  case class Level10(value: String)

  // Very deep nested case for extreme testing
  case class VeryDeepNestedCase(level1: Level1)

  // Test data
  private val validUser = User("John Doe", 30)
  private val invalidUser = User("", -1)

  private val validAddress = Address("123 Main St", "Anytown", "12345")
  private val validContact = Contact("john@example.com", "555-1234")
  private val validDepartment = Department("Engineering", "ENG")
  private val validEmployee = Employee("E123", "John Doe", 30, validAddress, validContact, validDepartment)

  private val invalidAddress = Address("", "", "invalid")
  private val invalidContact = Contact("invalid-email", "")
  private val invalidDepartment = Department("", "")
  private val invalidEmployee = Employee("", "", -1, invalidAddress, invalidContact, invalidDepartment)

  // Large data structure with valid data
  private val validLargeData = LargeDataStructure(
    Vector.fill(10)("valid"),
    Vector.fill(10)(42),
    Vector.fill(10)(validUser)
  )

  // Large data structure with all invalid data (worst case for error accumulation)
  private val invalidLargeData = LargeDataStructure(
    Vector.fill(100)(""),
    Vector.fill(100)(-1),
    Vector.fill(100)(invalidUser)
  )

  // Create a deeply nested valid structure
  private val validLevel10 = Level10("valid10")
  private val validLevel9 = Level9("valid9", Some(validLevel10))
  private val validLevel8 = Level8("valid8", Some(validLevel9))
  private val validLevel7 = Level7("valid7", Some(validLevel8))
  private val validLevel6 = Level6("valid6", Some(validLevel7))
  private val validLevel5 = Level5("valid5", Some(validLevel6))
  private val validLevel4 = Level4("valid4", Some(validLevel5))
  private val validLevel3 = Level3("valid3", Some(validLevel4))
  private val validLevel2 = Level2("valid2", Some(validLevel3))
  private val validLevel1 = Level1("valid1", Some(validLevel2))
  private val validDeepNested = VeryDeepNestedCase(validLevel1)

  // Create a deeply nested invalid structure (with errors at each level)
  private val invalidLevel10 = Level10("")
  private val invalidLevel9 = Level9("", Some(invalidLevel10))
  private val invalidLevel8 = Level8("", Some(invalidLevel9))
  private val invalidLevel7 = Level7("", Some(invalidLevel8))
  private val invalidLevel6 = Level6("", Some(invalidLevel7))
  private val invalidLevel5 = Level5("", Some(invalidLevel6))
  private val invalidLevel4 = Level4("", Some(invalidLevel5))
  private val invalidLevel3 = Level3("", Some(invalidLevel4))
  private val invalidLevel2 = Level2("", Some(invalidLevel3))
  private val invalidLevel1 = Level1("", Some(invalidLevel2))
  private val invalidDeepNested = VeryDeepNestedCase(invalidLevel1)

  // --- Validators ---

  // Basic validators
  given stringValidator: Validator[String] with {
    def validate(value: String): ValidationResult[String] =
      if (value.nonEmpty) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationError("String is empty"))
  }

  given intValidator: Validator[Int] with {
    def validate(value: Int): ValidationResult[Int] =
      if (value >= 0) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationError("Int is negative"))
  }

  // Derived validators
  given userValidator: Validator[User] = Validator.derive
  given addressValidator: Validator[Address] = Validator.derive
  given contactValidator: Validator[Contact] = Validator.derive
  given departmentValidator: Validator[Department] = Validator.derive
  given employeeValidator: Validator[Employee] = Validator.derive
  given largeDataValidator: Validator[LargeDataStructure] = Validator.derive

  // Derived validators for all nested levels
  given level10Validator: Validator[Level10] = Validator.derive
  given level9Validator: Validator[Level9] = Validator.derive
  given level8Validator: Validator[Level8] = Validator.derive
  given level7Validator: Validator[Level7] = Validator.derive
  given level6Validator: Validator[Level6] = Validator.derive
  given level5Validator: Validator[Level5] = Validator.derive
  given level4Validator: Validator[Level4] = Validator.derive
  given level3Validator: Validator[Level3] = Validator.derive
  given level2Validator: Validator[Level2] = Validator.derive
  given level1Validator: Validator[Level1] = Validator.derive
  given deepNestedValidator: Validator[VeryDeepNestedCase] = Validator.derive

  // --- Observer for benchmarking ---

  // Immutable counter class to track validation results
  case class ValidationCounter(validCount: Int = 0, invalidCount: Int = 0, errorCount: Int = 0) {
    def countValid: ValidationCounter = copy(validCount = validCount + 1)
    def countInvalid(errorSize: Int): ValidationCounter =
      copy(invalidCount = invalidCount + 1, errorCount = errorCount + errorSize)
  }

  // Thread-safe observer using atomic reference to avoid mutable state
  class BenchmarkObserver extends ValidationObserver {
    private val counterRef = new java.util.concurrent.atomic.AtomicReference(ValidationCounter())

    // Get the current counter state
    def getCounter: ValidationCounter = counterRef.get()

    def onResult[A](result: ValidationResult[A]): Unit = {
      counterRef.updateAndGet(current =>
        result match {
          case ValidationResult.Valid(_) => current.countValid
          case ValidationResult.Invalid(errors) => current.countInvalid(errors.size)
        }
      )
    }

    def reset(): Unit = {
      counterRef.set(ValidationCounter())
    }
  }

  private val benchmarkObserver = new BenchmarkObserver()

  // --- Translator for benchmarking ---

  class BenchmarkTranslator extends Translator {
    def translate(error: ValidationError): String =
      s"Translated: ${error.message} (code: ${error.code.getOrElse("none")})"
  }

  private given translator: Translator = new BenchmarkTranslator()

  // --- Benchmark Utilities ---

  /** Runs a benchmark function multiple times and returns the average execution time in nanoseconds
    */
  def benchmark[T](name: String, iterations: Int = 1000)(f: => T): (String, Double, T) = {
    // Warmup
    for (_ <- 1 to 5) f

    // Run once to get a result value
    val sampleResult = f

    // Time the iterations
    val start = System.nanoTime()

    // Run the benchmark iterations
    for (_ <- 1 to iterations) {
      f // Discard the result during timing
    }

    val end = System.nanoTime()
    val avgNanos = (end - start).toDouble / iterations

    // Return the name, average time, and a sample result
    (name, avgNanos, sampleResult)
  }

  /** Prints benchmark results in a formatted table */
  def printResults(results: Seq[(String, Double, Any)]): Unit = {
    println("=== Native Benchmark Results ===")
    println("Benchmark                                  | Avg Time (ns)    ")
    println("-" * 60)

    results.foreach { case (name, time, _) =>
      println(f"$name%40s | $time%15.2f")
    }
  }

  // --- Benchmark Implementations ---

  def runCoreBenchmarks(): Seq[(String, Double, Any)] = Seq(
    benchmark("syncSimpleValid") {
      userValidator.validate(validUser)
    },
    benchmark("syncSimpleInvalid") {
      userValidator.validate(invalidUser)
    },
    benchmark("syncNestedValid") {
      employeeValidator.validate(validEmployee)
    },
    benchmark("syncNestedInvalid") {
      employeeValidator.validate(invalidEmployee)
    }
  )

  def runExtendedBenchmarks(): Seq[(String, Double, Any)] = Seq(
    benchmark("macroDerivationOverhead") {
      employeeValidator.validate(validEmployee)
    },
    benchmark("macroDerivationOverheadInvalid") {
      employeeValidator.validate(invalidEmployee)
    },
    benchmark("errorAccumulationScaling") {
      largeDataValidator.validate(invalidLargeData)
    },
    benchmark("errorAccumulationScalingValid") {
      largeDataValidator.validate(validLargeData)
    },
    benchmark("observerPattern") {
      benchmarkObserver.reset()
      given ValidationObserver = benchmarkObserver
      userValidator.validate(validUser).observe()
    },
    benchmark("observerPatternInvalid") {
      benchmarkObserver.reset()
      given ValidationObserver = benchmarkObserver
      userValidator.validate(invalidUser).observe()
    },
    benchmark("noObserverBaseline") {
      userValidator.validate(validUser).observe()
    },
    benchmark("translatorIntegration") {
      userValidator.validate(invalidUser).translateErrors()
    },
    benchmark("translatorIntegrationValid") {
      userValidator.validate(validUser).translateErrors()
    }
  )

  def runAdvancedBenchmarks(): Seq[(String, Double, Any)] = Seq(
    benchmark("deepNestingPerformance") {
      deepNestedValidator.validate(validDeepNested)
    },
    benchmark("deepNestingPerformanceWithErrors") {
      deepNestedValidator.validate(invalidDeepNested)
    }
  )

  // --- Main Entry Point ---

  def main(args: Array[String]): Unit = {
    println("Running Valar Native Benchmarks...")

    val allResults = runCoreBenchmarks() ++ runExtendedBenchmarks() ++ runAdvancedBenchmarks()
    printResults(allResults)

    // Generate a simple CSV report for platform comparison
    val reportPath = "native-benchmark-results.csv"
    val report = new java.io.PrintWriter(reportPath)
    try {
      report.println("Benchmark,Time (ns),Platform")
      allResults.foreach { case (name, time, _) =>
        report.println(s"$name,$time,Native")
      }
    } finally {
      report.close()
    }

    println(s"Benchmark results saved to $reportPath")
  }
}

package net.ghoula.valar.benchmarks

import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit

import net.ghoula.valar.*
import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.translator.{translateErrors, Translator}

/** Defines extended JMH benchmarks for Valar.
  *
  * This suite measures the performance of additional validation scenarios, including:
  *   - Macro derivation overhead
  *   - Error accumulation scaling
  *   - Observer pattern overhead
  *   - Translator integration
  *
  * To run these benchmarks, use the sbt command: `valarBenchmarks / Jmh / run`
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class ExtendedBenchmarks {

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

  // --- Benchmarks ---

  // Tier 2: Macro Derivation Overhead

  @Benchmark
  def macroDerivationOverhead(): ValidationResult[Employee] = {
    employeeValidator.validate(validEmployee)
  }

  @Benchmark
  def macroDerivationOverheadInvalid(): ValidationResult[Employee] = {
    employeeValidator.validate(invalidEmployee)
  }

  // Tier 2: Error Accumulation Scaling

  @Benchmark
  def errorAccumulationScaling(): ValidationResult[LargeDataStructure] = {
    largeDataValidator.validate(invalidLargeData)
  }

  @Benchmark
  def errorAccumulationScalingValid(): ValidationResult[LargeDataStructure] = {
    largeDataValidator.validate(validLargeData)
  }

  // Tier 2: Observer Pattern

  @Benchmark
  def observerPattern(): ValidationResult[User] = {
    benchmarkObserver.reset()
    given ValidationObserver = benchmarkObserver
    userValidator.validate(validUser).observe()
  }

  @Benchmark
  def observerPatternInvalid(): ValidationResult[User] = {
    benchmarkObserver.reset()
    given ValidationObserver = benchmarkObserver
    userValidator.validate(invalidUser).observe()
  }

  @Benchmark
  def noObserverBaseline(): ValidationResult[User] = {
    // Uses the default no-op observer
    userValidator.validate(validUser).observe()
  }

  // Tier 2: Translator Integration

  @Benchmark
  def translatorIntegration(): ValidationResult[User] = {
    userValidator.validate(invalidUser).translateErrors()
  }

  @Benchmark
  def translatorIntegrationValid(): ValidationResult[User] = {
    userValidator.validate(validUser).translateErrors()
  }
}

package net.ghoula.valar.benchmarks

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

import net.ghoula.valar.*
import net.ghoula.valar.ValidationErrors.ValidationError

/** Defines advanced JMH benchmarks for Valar.
  *
  * This suite measures advanced performance characteristics, including:
  *   - Memory allocation profiling
  *   - Concurrency benchmarks
  *   - Stress testing with deeply nested structures
  *
  * To run these benchmarks, use the sbt command: `valarBenchmarks / Jmh / run`
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AdvancedBenchmarks {

  // --- Test Data and Models ---

  // Simple case class for basic validation
  case class User(name: String, age: Int)

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

  // Derived validators for all nested levels
  given userValidator: Validator[User] = Validator.derive
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

  // Async validators
  given asyncStringValidator: AsyncValidator[String] with {
    def validateAsync(value: String)(using ec: concurrent.ExecutionContext): Future[ValidationResult[String]] =
      Future.successful(
        if (value.nonEmpty) ValidationResult.Valid(value)
        else ValidationResult.invalid(ValidationError("String is empty"))
      )
  }

  given asyncIntValidator: AsyncValidator[Int] with {
    def validateAsync(value: Int)(using ec: concurrent.ExecutionContext): Future[ValidationResult[Int]] =
      Future.successful(
        if (value >= 0) ValidationResult.Valid(value)
        else ValidationResult.invalid(ValidationError("Int is negative"))
      )
  }

  given asyncUserValidator: AsyncValidator[User] = AsyncValidator.derive

  // --- Benchmarks ---

  // Tier 4: Memory Allocation Profiling

  @Benchmark
  def memoryAllocationProfile(bh: Blackhole): ValidationResult[User] = {
    // Create many short-lived objects during validation
    val result = userValidator.validate(validUser)

    // Use the result to prevent dead code elimination
    bh.consume(result)

    // Create more objects by mapping over the result multiple times
    val transformed = (1 to 10).foldLeft(result) { (acc, _) =>
      acc.map(user => User(user.name + "x", user.age + 1))
    }

    transformed
  }

  @Benchmark
  def memoryAllocationProfileInvalid(bh: Blackhole): ValidationResult[User] = {
    // Create many short-lived objects during validation with errors
    val result = userValidator.validate(invalidUser)

    // Use the result to prevent dead code elimination
    bh.consume(result)

    // Create more objects by recovering and mapping
    val transformed = result
      .recover(User("default", 0))
      .map(user => User(user.name + "x", user.age + 1))

    transformed
  }

  // Tier 4: Concurrency Benchmarks

  @Benchmark
  def concurrentValidation(): ValidationResult[User] = {
    // Run multiple validations concurrently and combine results
    val futures = (1 to 10).map { i =>
      val user = User(s"User$i", i * 10)
      asyncUserValidator.validateAsync(user)
    }

    val futureResults = Future.sequence(futures)
    val results = Await.result(futureResults, 1.second)

    // Combine all results (this will accumulate all errors if any)
    results.foldLeft(ValidationResult.Valid(validUser): ValidationResult[User]) { (acc, result) =>
      acc.zip(result).map { case (_, latest) => latest }
    }
  }

  @Benchmark
  def concurrentValidationWithErrors(): ValidationResult[User] = {
    // Run multiple validations concurrently with some errors
    val futures = (1 to 10).map { i =>
      val user = if (i % 2 == 0) User("", -i) else User(s"User$i", i * 10)
      asyncUserValidator.validateAsync(user)
    }

    val futureResults = Future.sequence(futures)
    val results = Await.result(futureResults, 1.second)

    // Combine all results (this will accumulate all errors)
    results.foldLeft(ValidationResult.Valid(validUser): ValidationResult[User]) { (acc, result) =>
      acc.zip(result).map { case (_, latest) => latest }
    }
  }

  // Tier 4: Stress Testing with Deep Nesting

  @Benchmark
  def deepNestingPerformance(): ValidationResult[VeryDeepNestedCase] = {
    deepNestedValidator.validate(validDeepNested)
  }

  @Benchmark
  def deepNestingPerformanceWithErrors(): ValidationResult[VeryDeepNestedCase] = {
    deepNestedValidator.validate(invalidDeepNested)
  }
}

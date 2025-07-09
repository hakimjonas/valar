package net.ghoula.valar.benchmarks

import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

import net.ghoula.valar.*
import net.ghoula.valar.ValidationErrors.ValidationError

/** Defines the JMH benchmark suite for Valar.
  *
  * This suite measures the performance of critical validation paths, including
  *   - Synchronous validation of simple and nested case classes.
  *   - Asynchronous validation with a mix of sync and async rules.
  *
  * To run these benchmarks, use the sbt command: `valarBenchmarks / Jmh / run`
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class ValarBenchmark {

  // --- Test Data and Models ---

  case class SimpleUser(name: String, age: Int)
  case class NestedCompany(name: String, owner: SimpleUser)

  private val validUser: SimpleUser = SimpleUser("John Doe", 30)
  private val invalidUser: SimpleUser = SimpleUser("", -1)
  private val validCompany: NestedCompany = NestedCompany("Valid Corp", validUser)
  private val invalidCompany: NestedCompany = NestedCompany("", invalidUser)

  // --- Synchronous Validators ---

  given syncStringValidator: Validator[String] with {
    def validate(value: String): ValidationResult[String] =
      if (value.nonEmpty) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationError("String is empty"))
  }

  given syncIntValidator: Validator[Int] with {
    def validate(value: Int): ValidationResult[Int] =
      if (value >= 0) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationError("Int is negative"))
  }

  given syncUserValidator: Validator[SimpleUser] = Validator.derive
  given syncCompanyValidator: Validator[NestedCompany] = Validator.derive

  // --- Asynchronous Validators ---

  given asyncStringValidator: AsyncValidator[String] with {
    def validateAsync(name: String)(using ec: concurrent.ExecutionContext): Future[ValidationResult[String]] =
      Future.successful(syncStringValidator.validate(name))
  }

  given asyncUserValidator: AsyncValidator[SimpleUser] = AsyncValidator.derive
  given asyncCompanyValidator: AsyncValidator[NestedCompany] = AsyncValidator.derive

  // --- Benchmarks ---

  @Benchmark
  def syncSimpleValid(): ValidationResult[SimpleUser] = {
    syncUserValidator.validate(validUser)
  }

  @Benchmark
  def syncSimpleInvalid(): ValidationResult[SimpleUser] = {
    syncUserValidator.validate(invalidUser)
  }

  @Benchmark
  def syncNestedValid(): ValidationResult[NestedCompany] = {
    syncCompanyValidator.validate(validCompany)
  }

  @Benchmark
  def syncNestedInvalid(): ValidationResult[NestedCompany] = {
    syncCompanyValidator.validate(invalidCompany)
  }

  @Benchmark
  def asyncSimpleValid(): ValidationResult[SimpleUser] = {
    Await.result(asyncUserValidator.validateAsync(validUser), 1.second)
  }

  @Benchmark
  def asyncSimpleInvalid(): ValidationResult[SimpleUser] = {
    Await.result(asyncUserValidator.validateAsync(invalidUser), 1.second)
  }

  @Benchmark
  def asyncNestedValid(): ValidationResult[NestedCompany] = {
    Await.result(asyncCompanyValidator.validateAsync(validCompany), 1.second)
  }

  @Benchmark
  def asyncNestedInvalid(): ValidationResult[NestedCompany] = {
    Await.result(asyncCompanyValidator.validateAsync(invalidCompany), 1.second)
  }
}

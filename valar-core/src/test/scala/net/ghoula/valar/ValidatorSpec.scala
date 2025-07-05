package net.ghoula.valar

import munit.FunSuite

import java.time.*
import java.util.UUID
import scala.Symbol
import scala.math.{BigDecimal, BigInt}

/** Tests the built-in validators for standard library and Java types provided in the `Validator`
  * companion object.
  *
  * This spec ensures that Valar provides sensible default instances for common types. It verifies
  * both the simple "pass-through" validators (for types like `Long`, `Boolean`, `UUID`, etc.) and
  * the more opinionated default validators that enforce constraints (e.g., non-negative `Int`,
  * non-empty `String`).
  */
class ValidatorSpec extends FunSuite {

  /** Helper to test simple pass-through validators for a given value. */
  private def checkValidator[T](value: T)(using validator: Validator[T]): Unit = {
    assertEquals(validator.validate(value), ValidationResult.Valid(value))
  }

  /** Tests the opinionated standard validators that enforce constraints. */

  test("Provided validator for Int should validate non-negative numbers") {
    val validator = summon[Validator[Int]]
    assertEquals(validator.validate(10), ValidationResult.Valid(10))
    assertEquals(validator.validate(0), ValidationResult.Valid(0))
    validator.validate(-1) match {
      case _: ValidationResult.Invalid =>
      /** Expected Invalid result */
      case v => fail(s"Expected Invalid, but got $v")
    }
  }

  test("Provided validator for Float should validate finite numbers") {
    val validator = summon[Validator[Float]]
    assertEquals(validator.validate(3.14f), ValidationResult.Valid(3.14f))
    validator.validate(Float.NaN) match {
      case _: ValidationResult.Invalid =>
      /** Expected Invalid result */
      case v => fail(s"Expected Invalid, but got $v")
    }
  }

  test("Provided validator for Double should validate finite numbers") {
    val validator = summon[Validator[Double]]
    assertEquals(validator.validate(3.14d), ValidationResult.Valid(3.14d))
    validator.validate(Double.PositiveInfinity) match {
      case _: ValidationResult.Invalid =>
      /** Expected Invalid result */
      case v => fail(s"Expected Invalid, but got $v")
    }
  }

  test("Provided validator for String should validate non-empty strings") {
    val validator = summon[Validator[String]]
    assertEquals(validator.validate("hello"), ValidationResult.Valid("hello"))
    validator.validate("") match {
      case _: ValidationResult.Invalid =>
      /** Expected Invalid result */
      case v => fail(s"Expected Invalid, but got $v")
    }
  }

  test("Provided validator for Option[Int] should validate the inner value") {
    val validator = summon[Validator[Option[Int]]]
    assertEquals(validator.validate(Some(42)), ValidationResult.Valid(Some(42)))
    assertEquals(validator.validate(None), ValidationResult.Valid(None))

    validator.validate(Some(-5)) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        assert(errors.head.message.contains("Int must be non-negative"))
      case _ => fail("Expected Invalid result but got Valid")
    }
  }

  /** Tests the pass-through validators that accept all values of their type. */

  test("Pass-through validator for Boolean") { checkValidator(true) }
  test("Pass-through validator for Byte") { checkValidator(1.toByte) }
  test("Pass-through validator for Short") { checkValidator(1.toShort) }
  test("Pass-through validator for Long") { checkValidator(1L) }
  test("Pass-through validator for Char") { checkValidator('a') }
  test("Pass-through validator for Unit") { checkValidator(()) }
  test("Pass-through validator for BigInt") { checkValidator(BigInt(123)) }
  test("Pass-through validator for BigDecimal") { checkValidator(BigDecimal(123.45)) }
  test("Pass-through validator for Symbol") { checkValidator(Symbol("abc")) }
  test("Pass-through validator for UUID") {
    checkValidator(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
  }
  test("Pass-through validator for Instant") {
    checkValidator(Instant.ofEpochSecond(1672531200))
  }
  test("Pass-through validator for LocalDate") {
    checkValidator(LocalDate.of(2025, 7, 5))
  }
  test("Pass-through validator for LocalTime") {
    checkValidator(LocalTime.of(10, 30, 0))
  }
  test("Pass-through validator for LocalDateTime") {
    checkValidator(LocalDateTime.of(2025, 7, 5, 10, 30, 0))
  }
  test("Pass-through validator for ZonedDateTime") {
    checkValidator(ZonedDateTime.of(2025, 7, 5, 10, 30, 0, 0, ZoneId.of("UTC")))
  }
  test("Pass-through validator for Duration") {
    checkValidator(Duration.ofHours(5))
  }
}

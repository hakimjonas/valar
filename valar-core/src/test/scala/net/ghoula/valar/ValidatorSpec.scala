package net.ghoula.valar

import munit.FunSuite

/** Tests the built-in validators that enforce constraints (non-negative Int, non-empty String,
  * etc).
  */
class ValidatorSpec extends FunSuite {

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

}

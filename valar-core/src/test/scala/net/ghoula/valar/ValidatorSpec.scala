package net.ghoula.valar

import munit.FunSuite

import net.ghoula.valar.ValidationHelpers.*

/** Tests the built-in pass-through validators and opt-in constraint validators from
  * ValidationHelpers.
  */
class ValidatorSpec extends FunSuite {

  test("Built-in Int validator should be pass-through") {
    val validator = summon[Validator[Int]]
    assertEquals(validator.validate(10), ValidationResult.Valid(10))
    assertEquals(validator.validate(0), ValidationResult.Valid(0))
    assertEquals(validator.validate(-1), ValidationResult.Valid(-1))
    assertEquals(validator.validate(Int.MinValue), ValidationResult.Valid(Int.MinValue))
  }

  test("Built-in Float validator should be pass-through") {
    val validator = summon[Validator[Float]]
    assertEquals(validator.validate(3.14f), ValidationResult.Valid(3.14f))
    assertEquals(validator.validate(Float.PositiveInfinity), ValidationResult.Valid(Float.PositiveInfinity))
    // NaN requires special handling since NaN != NaN
    validator.validate(Float.NaN) match {
      case ValidationResult.Valid(v) => assert(v.isNaN)
      case _ => fail("Expected Valid result for NaN")
    }
  }

  test("Built-in Double validator should be pass-through") {
    val validator = summon[Validator[Double]]
    assertEquals(validator.validate(3.14d), ValidationResult.Valid(3.14d))
    assertEquals(validator.validate(Double.PositiveInfinity), ValidationResult.Valid(Double.PositiveInfinity))
    // NaN requires special handling since NaN != NaN
    validator.validate(Double.NaN) match {
      case ValidationResult.Valid(v) => assert(v.isNaN)
      case _ => fail("Expected Valid result for NaN")
    }
  }

  test("Built-in String validator should be pass-through") {
    val validator = summon[Validator[String]]
    assertEquals(validator.validate("hello"), ValidationResult.Valid("hello"))
    assertEquals(validator.validate(""), ValidationResult.Valid(""))
  }

  test("Built-in Option validator should be pass-through for inner value") {
    val validator = summon[Validator[Option[Int]]]
    assertEquals(validator.validate(Some(42)), ValidationResult.Valid(Some(42)))
    assertEquals(validator.validate(None), ValidationResult.Valid(None))
    assertEquals(validator.validate(Some(-5)), ValidationResult.Valid(Some(-5)))
  }

  test("Opt-in nonNegativeInt constraint should reject negative values") {
    val result = nonNegativeInt(-5)
    result match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        assert(errors.head.message.contains("non-negative"))
      case _ => fail("Expected Invalid result")
    }
  }

  test("Opt-in finiteFloat constraint should reject NaN") {
    val result = finiteFloat(Float.NaN)
    assert(result.isInvalid)
  }

  test("Opt-in finiteDouble constraint should reject Infinity") {
    val result = finiteDouble(Double.PositiveInfinity)
    assert(result.isInvalid)
  }

  test("Opt-in nonEmpty constraint should reject empty strings") {
    val result = nonEmpty("")
    assert(result.isInvalid)
  }
}

package net.ghoula.valar

import munit.FunSuite

/** Tests the validation of tuple types, including both regular tuples and named tuples.
  *
  * This spec verifies that Valar can properly validate tuple structures by validating each
  * component independently and collecting any validation errors with proper field path information
  * for named tuples.
  */
class TupleValidatorSpec extends FunSuite {

  /** Local validators to avoid conflicts with other test files. */
  private given localStringValidator: Validator[String] with {
    def validate(value: String): ValidationResult[String] =
      if (value.nonEmpty) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationErrors.ValidationError("String must not be empty"))
  }

  private given localIntValidator: Validator[Int] with {
    def validate(value: Int): ValidationResult[Int] =
      if (value >= 0) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationErrors.ValidationError("Int must be non-negative"))
  }

  /** Tuple validator for regular tuples. */
  private given tupleValidator[A, B](using va: Validator[A], vb: Validator[B]): Validator[(A, B)] =
    Validator.deriveValidatorMacro

  /** Named tuple validator using automatic derivation. */
  private given namedTupleValidator: Validator[(name: String, age: Int)] =
    Validator.deriveValidatorMacro

  test("Regular tuples should be validated with default validators") {
    val validTuple = ("hello", 42)
    val validator = summon[Validator[(String, Int)]]
    assertEquals(validator.validate(validTuple), ValidationResult.Valid(validTuple))
  }

  test("Named tuples should be validated with automatic derivation") {
    type PersonTuple = (name: String, age: Int)

    val validPerson: PersonTuple = (name = "Alice", age = 30)
    val validator = summon[Validator[PersonTuple]]

    /** Test valid case. */
    assertEquals(validator.validate(validPerson), ValidationResult.Valid(validPerson))

    /** Test invalid case. */
    val invalidPerson: PersonTuple = (name = "", age = -10)
    validator.validate(invalidPerson) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 2)
        assert(errors.exists(_.message.contains("String must not be empty")))
        assert(errors.exists(_.message.contains("Int must be non-negative")))
        assertEquals(errors.flatMap(_.fieldPath).toSet, Set("name", "age"))
      case _ => fail("Expected Invalid result")
    }
  }

  test("Named tuples and regular tuples should be treated as different types but convertible") {
    type PersonTuple = (name: String, age: Int)
    val namedTuple: PersonTuple = (name = "Alice", age = 30)
    val regularTuple: (String, Int) = ("Alice", 30)

    assertEquals(namedTuple.toTuple, regularTuple)

    val namedValidator = summon[Validator[PersonTuple]]
    val regularValidator = summon[Validator[(String, Int)]]

    assertEquals(namedValidator.validate(namedTuple), ValidationResult.Valid(namedTuple))
    assertEquals(regularValidator.validate(regularTuple), ValidationResult.Valid(regularTuple))
  }
}

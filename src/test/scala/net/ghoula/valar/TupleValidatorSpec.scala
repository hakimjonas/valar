package net.ghoula.valar

import org.specs2.matcher.{Matchers, ResultMatchers, TraversableMatchers}
import org.specs2.mutable.Specification

import scala.language.implicitConversions

/** Minimal test to check if named tuples work with the existing validator system. */
object TupleValidatorSpec extends Specification with Matchers with TraversableMatchers with ResultMatchers {

  // Local validators to avoid conflicts with other test files
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

  // Add tuple validator for regular tuples
  private given tupleValidator[A, B](using va: Validator[A], vb: Validator[B]): Validator[(A, B)] =
    Validator.deriveValidatorMacro

  private given namedTupleValidator: Validator[(name: String, age: Int)] =
    Validator.deriveValidatorMacro

  "Named Tuple Support" should {

    "validate regular tuples with default validators" in {
      val validTuple = ("hello", 42)
      val validator = summon[Validator[(String, Int)]]
      validator.validate(validTuple) must beEqualTo(ValidationResult.Valid(validTuple))
    }

    "validate named tuples with automatic derivation" in {
      type PersonTuple = (name: String, age: Int)

      val validPerson: PersonTuple = (name = "Alice", age = 30)
      val validator = summon[Validator[PersonTuple]]

      validator.validate(validPerson) must beEqualTo(ValidationResult.Valid(validPerson))

      val invalidPerson: PersonTuple = (name = "", age = -10)
      validator.validate(invalidPerson) must beLike { case ValidationResult.Invalid(errors) =>
        errors must haveSize(2)
        errors.map(_.message).must(contain(contain("String must not be empty")))
        errors.map(_.message).must(contain(contain("Int must be non-negative")))
        errors.flatMap(_.fieldPath) must containTheSameElementsAs(List("name", "age"))
      }
    }

    "demonstrate that named tuples and regular tuples are different types" in {
      type PersonTuple = (name: String, age: Int)
      val namedTuple: PersonTuple = (name = "Alice", age = 30)
      val regularTuple: (String, Int) = ("Alice", 30)

      namedTuple.toTuple must beEqualTo(regularTuple)

      val namedValidator = summon[Validator[PersonTuple]]
      val regularValidator = summon[Validator[(String, Int)]]

      namedValidator.validate(namedTuple) must beEqualTo(ValidationResult.Valid(namedTuple))
      regularValidator.validate(regularTuple) must beEqualTo(ValidationResult.Valid(regularTuple))
    }
  }
}

package net.ghoula.valar

import munit.FunSuite

import scala.collection.immutable.ArraySeq

import net.ghoula.valar.ErrorAccumulator
import net.ghoula.valar.ValidationErrors.{ValidationError, ValidationException}
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.Validator.derive

/** Comprehensive test suite for Valar's validation system.
  *
  * This spec tests all major components of the validation framework including:
  *   - Collection validators (List, Set, Map, Array, etc.)
  *   - Intersection type validation
  *   - Helper function validation utilities
  *   - Union type validation
  *   - Error accumulation strategies
  *   - ValidationResult extension methods
  *   - Macro-derived validators for case classes
  *   - Fail-fast validation operations
  */
class ValidationSpec extends FunSuite {

  /** Test setup with local validator instances to avoid conflicts. */

  private given stringValidator: Validator[String] with {
    def validate(value: String): ValidationResult[String] =
      if (value.nonEmpty) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationError("TestString: must not be empty", expected = Some("non-empty")))
  }

  private given intValidator: Validator[Int] with {
    def validate(value: Int): ValidationResult[Int] =
      if (value >= 0) ValidationResult.Valid(value)
      else
        ValidationResult.invalid(
          ValidationError("TestInt: must be non-negative", expected = Some(">= 0"), actual = Some(value.toString))
        )
  }

  private given optionTypeValidator[T](using v: Validator[T]): Validator[Option[T]] with {
    def validate(value: Option[T]): ValidationResult[Option[T]] = value match {
      case Some(inner) => v.validate(inner).map(Some(_))
      case None => ValidationResult.Valid(None)
    }
  }

  /** Test case classes for macro derivation testing. */

  private case class User(name: String, age: Option[Int])
  private given Validator[User] = derive

  private case class Address(street: String, city: String, zip: Int)
  private given Validator[Address] = derive

  private case class Company(name: String, address: Address, ceo: Option[User])
  private given Validator[Company] = derive

  /** Tests for collection type validators. */

  test("Collection Validators - listValidator") {
    val validator = summon[Validator[List[Int]]]
    assertEquals(validator.validate(List(1, 0, 3)), ValidationResult.Valid(List(1, 0, 3)))
    validator.validate(List(1, -2, 3, -4)) match {
      case ValidationResult.Invalid(errors) => assertEquals(errors.size, 2)
      case _ => fail("Expected Invalid")
    }
  }

  test("Collection Validators - seqValidator") {
    val validator = summon[Validator[Seq[Int]]]
    validator.validate(Seq(1, -5, 7, -8)) match {
      case ValidationResult.Invalid(errors) => assertEquals(errors.size, 2)
      case _ => fail("Expected Invalid")
    }
  }

  test("Collection Validators - vectorValidator") {
    val validator = summon[Validator[Vector[Int]]]
    validator.validate(Vector(10, -3, 20, -7)) match {
      case ValidationResult.Invalid(errors) => assertEquals(errors.size, 2)
      case _ => fail("Expected Invalid")
    }
  }

  test("Collection Validators - setValidator") {
    val validator = summon[Validator[Set[Int]]]
    validator.validate(Set(5, -10, 15, -20)) match {
      case ValidationResult.Invalid(errors) => assertEquals(errors.size, 2)
      case _ => fail("Expected Invalid")
    }
  }

  test("Collection Validators - mapValidator") {
    val validator = summon[Validator[Map[Int, String]]]
    assertEquals(validator.validate(Map(1 -> "a", 0 -> "b")), ValidationResult.Valid(Map(1 -> "a", 0 -> "b")))
    validator.validate(Map(-1 -> "a", 2 -> "", -3 -> "")) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 4)
        assertEquals(errors.count(_.message.contains("Invalid field: key")), 2)
        assertEquals(errors.count(_.message.contains("Invalid field: value")), 2)
      case _ => fail("Expected Invalid")
    }
  }

  test("Collection Validators - arrayValidator") {
    val validator = summon[Validator[Array[Int]]]
    validator.validate(Array(100, -50, 200, -150)) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 2)
        assertEquals(errors.flatMap(_.actual).toSet, Set("-50", "-150"))
      case _ => fail("Expected Invalid")
    }
  }

  test("Collection Validators - arraySeqValidator") {
    val validator = summon[Validator[ArraySeq[Int]]]
    validator.validate(ArraySeq(10, -20, 30, -40)) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 2)
        assertEquals(errors.flatMap(_.actual).toSet, Set("-20", "-40"))
      case _ => fail("Expected Invalid")
    }
  }

  /** Tests for intersection type validation. */

  test("Intersection Validator - all cases") {

    /** These givens are scoped locally to this test, resolving potential ambiguity. */
    trait LongerThan3 { def value: String }
    trait StartsWithA { def value: String }
    case class TestIntersection(value: String) extends LongerThan3 with StartsWithA

    given localLongerThan3Validator: Validator[LongerThan3] = (a: LongerThan3) =>
      if (a.value.length > 3) ValidationResult.Valid(a)
      else ValidationResult.invalid(ValidationError("Length must be > 3"))
    given localStartsWithAValidator: Validator[StartsWithA] = (a: StartsWithA) =>
      if (a.value.startsWith("A")) ValidationResult.Valid(a)
      else ValidationResult.invalid(ValidationError("Must start with A"))
    given testIntersectionValidator: Validator[LongerThan3 & StartsWithA] =
      (ab: LongerThan3 & StartsWithA) =>
        summon[Validator[LongerThan3]].validate(ab).zip(summon[Validator[StartsWithA]].validate(ab)).map(_ => ab)

    val validator = summon[Validator[LongerThan3 & StartsWithA]]

    /** Test success case. */
    assertEquals(validator.validate(TestIntersection("Amazing")), ValidationResult.Valid(TestIntersection("Amazing")))

    /** Test single failures. */
    assert(validator.validate(TestIntersection("Awe")).isInvalid)
    assert(validator.validate(TestIntersection("Brave")).isInvalid)

    /** Test error accumulation. */
    validator.validate(TestIntersection("Bad")) match {
      case ValidationResult.Invalid(errs) => assertEquals(errs.size, 2)
      case _ => fail("Expected Invalid")
    }
  }

  /** Tests for basic helper function validation utilities. */

  test("Helpers - nonEmpty") {
    assertEquals(nonEmpty("hello"), ValidationResult.Valid("hello"))
    assert(nonEmpty("").isInvalid)
    assert(nonEmpty("  ").isInvalid)
  }

  test("Helpers - nonNegativeInt") {
    assertEquals(nonNegativeInt(5), ValidationResult.Valid(5))
    assertEquals(nonNegativeInt(0), ValidationResult.Valid(0))
    assert(nonNegativeInt(-1).isInvalid)
  }

  test("Helpers - finiteFloat") {
    assertEquals(finiteFloat(3.14f), ValidationResult.Valid(3.14f))
    assert(finiteFloat(Float.PositiveInfinity).isInvalid)
    assert(finiteFloat(Float.NaN).isInvalid)
  }

  test("Helpers - finiteDouble") {
    assertEquals(finiteDouble(3.14d), ValidationResult.Valid(3.14d))
    assert(finiteDouble(Double.NegativeInfinity).isInvalid)
    assert(finiteDouble(Double.NaN).isInvalid)
  }

  test("Helpers - minLengthValidator") {
    assertEquals(minLengthValidator("hi", 1)(), ValidationResult.Valid("hi"))
    assertEquals(minLengthValidator("hi", 2)(), ValidationResult.Valid("hi"))
    assert(minLengthValidator("", 1)().isInvalid)
    assert(minLengthValidator("hi", 3)().isInvalid)
    assert(minLengthValidator(Option.empty[String].orNull, 3)().isInvalid)
  }

  test("Helpers - maxLengthValidator") {
    assertEquals(maxLengthValidator("hi", 3)(), ValidationResult.Valid("hi"))
    assertEquals(maxLengthValidator("hi", 2)(), ValidationResult.Valid("hi"))
    assert(maxLengthValidator("hi", 1)().isInvalid)
    assert(maxLengthValidator(Option.empty[String].orNull, 3)().isInvalid)
  }

  test("Helpers - regexMatch") {
    val pattern = "[a-z]+[0-9]+"
    assertEquals(regexMatch("abc123", pattern)(), ValidationResult.Valid("abc123"))
    assert(regexMatch("abc", pattern)().isInvalid)
    assert(regexMatch("TestData", "([a-z)")().isInvalid)
    assert(regexMatch(Option.empty[String].orNull, pattern)().isInvalid)
  }

  test("Helpers - inRange") {
    assertEquals(inRange(5, 1, 10)(), ValidationResult.Valid(5))
    assert(inRange(0, 1, 10)().isInvalid)
  }

  test("Helpers - oneOf") {
    assertEquals(oneOf("B", Set("A", "B", "C"))(), ValidationResult.Valid("B"))
    assert(oneOf("D", Set("A", "B", "C"))().isInvalid)
  }

  test("Helpers - optional") {
    val validator = summon[Validator[Option[String]]]
    assertEquals(validator.validate(Some("hello")), ValidationResult.Valid(Some("hello")))
    assertEquals(validator.validate(None), ValidationResult.Valid(None))
    assert(validator.validate(Some("")).isInvalid)
  }

  test("Helpers - required") {
    assertEquals(required(Some("hello")), ValidationResult.Valid("hello"))
    assert(required(None: Option[String]).isInvalid)
  }

  test("Helpers - optionValidator") {
    val validateFn = (s: String) => nonEmpty(s)
    assertEquals(optionValidator[String]("hello", validateFn), ValidationResult.Valid(Some("hello")))
    assert(optionValidator[String]("", validateFn).isInvalid)
    assertEquals(optionValidator[String](Option.empty[String].orNull, validateFn), ValidationResult.Valid(None))
    assert(optionValidator[String](Option.empty[String].orNull, validateFn, errorOnEmpty = true).isInvalid)
  }

  /** Tests for union type validation. */

  test("Union Validation - should validate first type") {
    assertEquals(ValidationResult.validateUnion[String, Int]("hello"), ValidationResult.Valid("hello"))
  }

  test("Union Validation - should validate second type") {
    assertEquals(ValidationResult.validateUnion[String, Int](42), ValidationResult.Valid(42))
  }

  test("Union Validation - should fail when value matches neither type") {
    assert(ValidationResult.validateUnion[String, Int](true).isInvalid)
  }

  /** Tests for custom error accumulation strategies. */

  test("Custom Error Accumulation - should use custom accumulator") {
    given customAccumulator: ErrorAccumulator[Vector[ValidationError]] with {
      def combine(e1: Vector[ValidationError], e2: Vector[ValidationError]): Vector[ValidationError] = e2 ++ e1
    }
    val invalid1 = ValidationResult.invalid(ValidationError("error1"))
    val invalid2 = ValidationResult.invalid(ValidationError("error2"))
    assertEquals(
      invalid1.orElse(invalid2),
      ValidationResult.Invalid(Vector(ValidationError("error2"), ValidationError("error1")))
    )
  }

  /** Tests for ValidationResult extension methods. */

  test("ValidationResult Extensions - all") {
    assertEquals(ValidationResult.Valid(10).map(_ * 2), ValidationResult.Valid(20))
    assertEquals(ValidationResult.Valid(10).flatMap(x => ValidationResult.Valid(x * 2)), ValidationResult.Valid(20))
    assertEquals(ValidationResult.Valid("a").zip(ValidationResult.Valid(1)), ValidationResult.Valid(("a", 1)))
    assertEquals(ValidationResult.Valid("a").or(ValidationResult.Valid(1)), ValidationResult.Valid("a"))
    assertEquals(
      ValidationResult.invalid(ValidationError("e")).orElse(ValidationResult.Valid(20)),
      ValidationResult.Valid(20)
    )
    assertEquals(ValidationResult.invalid(ValidationError("e")).recover(0), ValidationResult.Valid(0))
    assertEquals(ValidationResult.Valid(100).fold(_ + 1, _ => 0), 101)
    assertEquals(ValidationResult.invalid(ValidationError("e")).toOption, None)
    assertEquals(ValidationResult.invalid(ValidationError("e")).toList, Nil)
  }

  test("ValidationResult Extensions - toTry") {
    assert(ValidationResult.Valid("ok").toTry.isSuccess)
    val e = intercept[ValidationException] {
      ValidationResult.invalid(ValidationError("fail")).toTry.get
    }
    assertEquals(e.getMessage, "fail")
  }

  /** Tests for macro-derived validators. */

  test("Macro Derivation - success") {
    val company = Company("Acme Corp", Address("123 Main St", "Springfield", 12345), Some(User("Alice", Some(30))))
    assertEquals(summon[Validator[Company]].validate(company), ValidationResult.Valid(company))
  }

  test("Macro Derivation - accumulate nested errors") {
    val company = Company("BadCo", Address("", "Springfield", -1), Some(User("", Some(25))))
    summon[Validator[Company]].validate(company) match {
      case ValidationResult.Invalid(errors) => assertEquals(errors.size, 3)
      case _ => fail("Expected Invalid")
    }
  }

  /** Tests for fail-fast validation operations. */

  test("Fail-Fast - zipFailFast") {
    val v1 = ValidationResult.Valid(1)
    val v2 = ValidationResult.Valid("A")
    val e1 = ValidationResult.invalid[Int](ValidationError("E1"))
    val e2 = ValidationResult.invalid[String](ValidationError("E2"))

    assertEquals(v1.zipFailFast(v2), ValidationResult.Valid((1, "A")))

    e1.zipFailFast(v2) match {
      case ValidationResult.Invalid(errors) =>
        e1 match {
          case ValidationResult.Invalid(e1Errors) => assertEquals(errors, e1Errors)
          case _ => fail("e1 should be Invalid")
        }
      case _ => fail("Expected Invalid")
    }

    v1.zipFailFast(e2) match {
      case ValidationResult.Invalid(errors) =>
        e2 match {
          case ValidationResult.Invalid(e2Errors) => assertEquals(errors, e2Errors)
          case _ => fail("e2 should be Invalid")
        }
      case _ => fail("Expected Invalid")
    }

    e1.zipFailFast(e2) match {
      case ValidationResult.Invalid(errors) =>
        e1 match {
          case ValidationResult.Invalid(e1Errors) => assertEquals(errors, e1Errors)
          case _ => fail("e1 should be Invalid")
        }
      case _ => fail("Expected Invalid")
    }
  }
}

/** Extension methods for ValidationResult to support test assertions. */
private implicit class ValidationResultTestOps[A](vr: ValidationResult[A]) {
  def isInvalid: Boolean = vr match {
    case _: ValidationResult.Invalid => true
    case _ => false
  }
}

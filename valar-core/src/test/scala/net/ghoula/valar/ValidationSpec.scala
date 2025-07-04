package net.ghoula.valar

import munit.FunSuite

import net.ghoula.valar.ErrorAccumulator
import net.ghoula.valar.ValidationErrors.{ValidationError, ValidationException}
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.Validator.deriveValidatorMacro

class ValidationSpec extends FunSuite {

  // --- Givens and Test Setup ---
  private given Validator[String] with {
    def validate(value: String): ValidationResult[String] =
      if (value.nonEmpty) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationError("TestString: must not be empty", expected = Some("non-empty")))
  }

  private given Validator[Int] with {
    def validate(value: Int): ValidationResult[Int] =
      if (value >= 0) ValidationResult.Valid(value)
      else
        ValidationResult.invalid(
          ValidationError("TestInt: must be non-negative", expected = Some(">= 0"), actual = Some(value.toString))
        )
  }

  private given [T](using v: Validator[T]): Validator[Option[T]] with {
    def validate(value: Option[T]): ValidationResult[Option[T]] = value match {
      case Some(inner) => v.validate(inner).map(Some(_))
      case None => ValidationResult.Valid(None)
    }
  }

  private case class User(name: String, age: Option[Int])
  private given Validator[User] = deriveValidatorMacro

  private case class Address(street: String, city: String, zip: Int)
  private given Validator[Address] = deriveValidatorMacro

  private case class Company(name: String, address: Address, ceo: Option[User])
  private given Validator[Company] = deriveValidatorMacro

  private case class NullFieldTest(name: String, age: Int)
  private given Validator[NullFieldTest] = deriveValidatorMacro

  // --- Collection Validator Tests ---

  test("Collection Validators - listValidator should validate a list of valid integers") {
    val validator = summon[Validator[List[Int]]]
    assertEquals(validator.validate(List(1, 0, 3)), ValidationResult.Valid(List(1, 0, 3)))
  }

  test("Collection Validators - listValidator should accumulate errors correctly") {
    val validator = summon[Validator[List[Int]]]
    validator.validate(List(1, -2, 3, -4)) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 2)
        assertEquals(errors.flatMap(_.actual).toSet, Set("-2", "-4"))
      case _ => fail("Expected Invalid")
    }
  }

  test("Collection Validators - mapValidator should accumulate errors for keys and values") {
    val validator = summon[Validator[Map[Int, String]]]
    validator.validate(Map(-1 -> "a", 2 -> "", -3 -> "")) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 4)
        assertEquals(errors.count(_.message.contains("Invalid field: key")), 2)
        assertEquals(errors.count(_.message.contains("Invalid field: value")), 2)
      case _ => fail("Expected Invalid")
    }
  }

  // --- Intersection Validator Tests ---

  trait LongerThan3 { def value: String }
  trait StartsWithA { def value: String }
  case class TestIntersection(value: String) extends LongerThan3 with StartsWithA

  private given Validator[LongerThan3] = (a: LongerThan3) =>
    if (a.value.length > 3) ValidationResult.Valid(a)
    else ValidationResult.invalid(ValidationError("Length must be > 3"))
  private given Validator[StartsWithA] = (a: StartsWithA) =>
    if (a.value.startsWith("A")) ValidationResult.Valid(a)
    else ValidationResult.invalid(ValidationError("Must start with A"))
  private given Validator[LongerThan3 & StartsWithA] = (ab: LongerThan3 & StartsWithA) =>
    summon[Validator[LongerThan3]].validate(ab).zip(summon[Validator[StartsWithA]].validate(ab)).map(_ => ab)

  test("Intersection Validator - should validate an object that satisfies both conditions") {
    val obj = TestIntersection("Amazing")
    assertEquals(summon[Validator[LongerThan3 & StartsWithA]].validate(obj), ValidationResult.Valid(obj))
  }

  test("Intersection Validator - should fail when one condition fails") {
    summon[Validator[LongerThan3 & StartsWithA]]
      .validate(TestIntersection("Awe")) match {
      case ValidationResult.Invalid(_) => // Expected
      case _ => fail("Expected Invalid")
    }
  }

  // --- Basic Helper Function Tests ---

  test("Helpers - nonEmpty") {
    assertEquals(nonEmpty("hello"), ValidationResult.Valid("hello"))
    nonEmpty("") match {
      case ValidationResult.Invalid(_) => // Expected
      case _ => fail("Expected Invalid")
    }
  }

  test("Helpers - positiveInt") {
    assertEquals(positiveInt(5), ValidationResult.Valid(5))
    positiveInt(-1) match {
      case ValidationResult.Invalid(_) => // Expected
      case _ => fail("Expected Invalid")
    }
  }

  test("Helpers - minLengthValidator") {
    assertEquals(minLengthValidator("hi", 2)(), ValidationResult.Valid("hi"))
    minLengthValidator("hi", 3)() match {
      case ValidationResult.Invalid(_) => // Expected
      case _ => fail("Expected Invalid")
    }
  }

  // --- Union Validation Tests ---

  test("Union Validation - should validate when value matches the first type") {
    assertEquals(ValidationResult.validateUnion[String, Int]("hello"), ValidationResult.Valid("hello"))
  }

  test("Union Validation - should validate when value matches the second type") {
    assertEquals(ValidationResult.validateUnion[String, Int](42), ValidationResult.Valid(42))
  }

  test("Union Validation - should fail with a nested error when value matches neither type") {
    ValidationResult.validateUnion[String, Int](true) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        val topError = errors.head
        assert(topError.message.contains("Value failed validation for all expected types"))
        assertEquals(topError.children.size, 2)
      case _ => fail("Expected Invalid")
    }
  }

  // --- Error Accumulation Tests ---

  test("Error Accumulation - should accumulate multiple errors when zipping two invalid results") {
    val invalid1 = ValidationResult.invalid(ValidationError("error1"))
    val invalid2 = ValidationResult.invalid(ValidationError("error2"))
    assertEquals(
      invalid1.zip(invalid2),
      ValidationResult.Invalid(Vector(ValidationError("error1"), ValidationError("error2")))
    )
  }

  // --- ValidationResult Extension Method Tests ---

  test("ValidationResult Extensions - map") {
    assertEquals(ValidationResult.Valid(10).map(_ * 2), ValidationResult.Valid(20))
    val invalid = ValidationResult.invalid[Int](ValidationError("e"))
    assertEquals(invalid.map(_ + 1), invalid)
  }

  test("ValidationResult Extensions - toTry") {
    assert(ValidationResult.Valid("ok").toTry.isSuccess)
    val exception = intercept[ValidationException] {
      ValidationResult.invalid[String](ValidationError("fail")).toTry.get
    }
    assertEquals(exception.error.message, "fail")
  }

  // --- Macro Derivation Tests ---

  test("Macro Derivation - should validate a complex nested case class successfully") {
    val company = Company("Acme Corp", Address("123 Main St", "Springfield", 12345), Some(User("Alice", Some(30))))
    assertEquals(summon[Validator[Company]].validate(company), ValidationResult.Valid(company))
  }

  test("Macro Derivation - should accumulate errors correctly in a nested case class") {
    val company = Company("BadCo", Address("", "Springfield", -1), Some(User("", Some(25))))
    summon[Validator[Company]].validate(company) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 3)
        assert(errors.exists(_.fieldPath == List("address", "street")))
        assert(errors.exists(_.fieldPath == List("address", "zip")))
        assert(errors.exists(_.fieldPath == List("ceo", "name")))
      case _ => fail("Expected Invalid")
    }
  }

  // --- Fail-Fast Operation Tests ---

  test("Fail-Fast - zipFailFast should return Invalid with left errors only") {
    val e1 = ValidationResult.invalid[Int](ValidationError("E1"))
    val e2 = ValidationResult.invalid[String](Vector(ValidationError("E2a"), ValidationError("E2b")))
    val expected = ValidationResult.invalid[(Int, String)](ValidationError("E1"))
    assertEquals(e1.zipFailFast(e2), expected)
  }

  // --- Null Field Tests ---

  test("Null Field - should handle null field validation") {
    val nullTest = NullFieldTest("valid", 42)
    assertEquals(summon[Validator[NullFieldTest]].validate(nullTest), ValidationResult.Valid(nullTest))
  }

  // --- ValidationHelpers.optionValidator Tests ---

  test("Helpers - optionValidator with errorOnEmpty=false should allow null/None") {
    val validator = (s: String) => nonEmpty(s)
    val result = optionValidator[String](Option.empty[String].orNull, validator, errorOnEmpty = false)
    assertEquals(result, ValidationResult.Valid(None))
  }

  test("Helpers - optionValidator with errorOnEmpty=true should reject null/None") {
    val validator = (s: String) => nonEmpty(s)
    val result = optionValidator[String](Option.empty[String].orNull, validator, errorOnEmpty = true)
    result match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        assertEquals(errors.head.message, "Value must not be empty/null")
      case _ => fail("Expected Invalid")
    }
  }

  test("Helpers - optionValidator with errorOnEmpty=true should use custom error message") {
    val customMsg = "Custom error for empty value"
    val validator = (s: String) => nonEmpty(s)
    val result =
      optionValidator[String](Option.empty[String].orNull, validator, errorOnEmpty = true, emptyErrorMsg = customMsg)
    result match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        assertEquals(errors.head.message, customMsg)
      case _ => fail("Expected Invalid")
    }
  }

  // --- ValidationResult Factory Method Tests ---

  test("ValidationResult.fromEither - should convert Right to Valid") {
    val either: Either[ValidationError, String] = Right("success")
    assertEquals(ValidationResult.fromEither(either), ValidationResult.Valid("success"))
  }

  test("ValidationResult.fromEither - should convert Left to Invalid") {
    val error = ValidationError("test error")
    val either: Either[ValidationError, String] = Left(error)
    assertEquals(ValidationResult.fromEither(either), ValidationResult.invalid(error))
  }

  test("ValidationResult.fromEitherErrors - should convert Right to Valid") {
    val either: Either[Vector[ValidationError], Int] = Right(42)
    assertEquals(ValidationResult.fromEitherErrors(either), ValidationResult.Valid(42))
  }

  test("ValidationResult.fromEitherErrors - should convert Left with errors to Invalid") {
    val errors = Vector(ValidationError("error1"), ValidationError("error2"))
    val either: Either[Vector[ValidationError], Int] = Left(errors)
    assertEquals(ValidationResult.fromEitherErrors(either), ValidationResult.invalid(errors))
  }

  test("ValidationResult.fromEitherErrors - should handle empty error vector") {
    val either: Either[Vector[ValidationError], Int] = Left(Vector.empty)
    ValidationResult.fromEitherErrors(either) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        assert(errors.head.message.contains("Cannot create Invalid ValidationResult from an empty error vector"))
      case _ => fail("Expected Invalid")
    }
  }

  // --- ValidationError API Tests ---

  test("ValidationError API - show should format error as a single line") {
    val error = ValidationError(
      message = "Invalid value",
      expected = Some("positive"),
      actual = Some("-1"),
      code = Some("ERR001"),
      severity = Some("Error")
    )
    val formatted = error.show
    assert(formatted.contains("Invalid value"))
    assert(formatted.contains("[ERR001]"))
    assert(formatted.contains("<Error>"))
    assert(formatted.contains("(expected: positive)"))
    assert(formatted.contains("(got: -1)"))
  }

  test("ValidationError API - prettyPrint should format error with correct indentation") {
    val childError = ValidationError("Child error")
    val parentError = ValidationError(
      message = "Parent error",
      children = Vector(childError)
    )
    val formatted = parentError.prettyPrint()

    val expectedOutput =
      """Parent error
        |Child error""".stripMargin.replaceAll("\r\n", "\n")

    assertEquals(formatted, expectedOutput)
  }

  test("ValidationError API - annotateField should add field context") {
    val error = ValidationError("Invalid value")
    val annotated = error.annotateField("username", "String")
    assertEquals(annotated.fieldPath, List("username"))
    assert(annotated.message.contains("Invalid field: username"))
    assert(annotated.message.contains("field type: String"))
    assert(annotated.message.contains("Invalid value"))
  }
}

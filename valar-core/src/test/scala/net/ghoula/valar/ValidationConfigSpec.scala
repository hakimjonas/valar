package net.ghoula.valar

import munit.FunSuite

/** Tests ValidationConfig collection size limits.
  */
class ValidationConfigSpec extends FunSuite {

  private given Validator[Int] with {
    def validate(value: Int): ValidationResult[Int] = ValidationResult.Valid(value)
  }

  test("default config should allow unlimited collection sizes") {
    given ValidationConfig = ValidationConfig.default
    val largeList = List.fill(50000)(1)
    val validator = summon[Validator[List[Int]]]
    assert(validator.validate(largeList).isValid)
  }

  test("strict config should reject collections exceeding limit") {
    given ValidationConfig = ValidationConfig.strict // 10,000 limit
    val oversizedList = List.fill(10001)(1)
    val validator = summon[Validator[List[Int]]]
    validator.validate(oversizedList) match {
      case ValidationResult.Invalid(errors) =>
        assertEquals(errors.size, 1)
        assert(errors.head.message.contains("exceeds maximum allowed size"))
        assertEquals(errors.head.code, Some("validation.security.collection_too_large"))
      case _ => fail("Expected Invalid for oversized collection")
    }
  }

  test("strict config should allow collections within limit") {
    given ValidationConfig = ValidationConfig.strict
    val validList = List.fill(10000)(1)
    val validator = summon[Validator[List[Int]]]
    assert(validator.validate(validList).isValid)
  }

  test("custom config should enforce custom limit") {
    given ValidationConfig = ValidationConfig(maxCollectionSize = Some(5))
    val oversized = List(1, 2, 3, 4, 5, 6)
    val validator = summon[Validator[List[Int]]]
    assert(validator.validate(oversized).isInvalid)
    assert(validator.validate(List(1, 2, 3, 4, 5)).isValid)
  }

  test("size check should fail fast before validating elements") {
    var elementsValidated = 0
    given Validator[String] with {
      def validate(s: String): ValidationResult[String] = {
        elementsValidated += 1
        ValidationResult.Valid(s)
      }
    }
    given ValidationConfig = ValidationConfig(maxCollectionSize = Some(2))

    val oversized = List("a", "b", "c", "d")
    val validator = summon[Validator[List[String]]]
    validator.validate(oversized)

    assertEquals(elementsValidated, 0, "Should not validate any elements when size limit exceeded")
  }
}

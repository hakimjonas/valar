package net.ghoula.valar.munit

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationResult

/** Tests the `ValarSuite` trait to ensure its assertion helpers are correct and reliable.
  */
class ValarSuiteSpec extends ValarSuite {

  private val validResult = ValidationResult.Valid("success")
  private val singleErrorResult = ValidationResult.invalid(ValidationError("single error"))
  private val multipleErrorsResult = ValidationResult.invalid(
    Vector(
      ValidationError("first error", fieldPath = List("field1")),
      ValidationError("second error", fieldPath = List("field2"))
    )
  )

  test("assertValid should return value when result is Valid") {
    val value = assertValid(validResult)
    assertEquals(value, "success")
  }

  test("assertValid should fail when result is Invalid") {
    intercept[munit.FailException] {
      assertValid(singleErrorResult)
    }
  }

  test("assertHasOneError should succeed when result has exactly one error") {
    assertHasOneError(singleErrorResult) { error =>
      assertEquals(error.message, "single error")
    }
  }

  test("assertHasOneError should fail when result is Valid") {
    intercept[munit.FailException] {
      assertHasOneError(validResult)(_ => ())
    }
  }

  test("assertHasOneError should fail when result has multiple errors") {
    intercept[munit.FailException] {
      assertHasOneError(multipleErrorsResult)(_ => ())
    }
  }

  test("assertHasNErrors should succeed when result has exactly N errors") {
    assertHasNErrors(multipleErrorsResult, 2) { errors =>
      assertEquals(errors.size, 2)
      assertEquals(errors.head.message, "first error")
      assertEquals(errors.last.message, "second error")
    }
  }

  test("assertHasNErrors should fail when result is Valid") {
    intercept[munit.FailException] {
      assertHasNErrors(validResult, 1)(_ => ())
    }
  }

  test("assertHasNErrors should fail when error count doesn't match") {
    intercept[munit.FailException] {
      assertHasNErrors(singleErrorResult, 2)(_ => ())
    }
  }

  test("assertInvalid should succeed when result is Invalid and partial function matches") {
    val errors = assertInvalid(multipleErrorsResult) {
      case vector if vector.size == 2 =>
        assert(vector.exists(_.fieldPath == List("field1")))
        assert(vector.exists(_.fieldPath == List("field2")))
    }
    assertEquals(errors.size, 2)
  }

  test("assertInvalid should fail when result is Valid") {
    intercept[munit.FailException] {
      assertInvalid(validResult) { case _ => () }
    }
  }

  test("assertInvalid should fail when partial function doesn't match") {
    intercept[munit.FailException] {
      assertInvalid(singleErrorResult) {
        case errors if errors.size == 2 =>
          ()
      }
    }
  }

  test("assertInvalidWith should succeed when result is Invalid") {
    val errors = assertInvalidWith(singleErrorResult) { errors =>
      assertEquals(errors.size, 1)
      assertEquals(errors.head.message, "single error")
    }
    assertEquals(errors.size, 1)
  }

  test("assertInvalidWith should fail when result is Valid") {
    intercept[munit.FailException] {
      assertInvalidWith(validResult)(_ => ())
    }
  }

  test("assertion failures should provide meaningful error messages") {
    val exception = intercept[munit.FailException] {
      assertValid(singleErrorResult, "Should be valid")
    }
    assert(exception.getMessage.contains("Should be valid"))
    assert(exception.getMessage.contains("single error"))
  }

  test("assertions should work with all ValidationError features") {
    val complexError = ValidationError(
      message = "Complex validation error",
      fieldPath = List("user", "profile", "email"),
      code = Some("EMAIL_INVALID"),
      severity = Some("ERROR"),
      expected = Some("valid email format"),
      actual = Some("invalid@")
    )
    val complexResult = ValidationResult.invalid(complexError)

    assertHasOneError(complexResult) { error =>
      assertEquals(error.message, "Complex validation error")
      assertEquals(error.fieldPath, List("user", "profile", "email"))
      assertEquals(error.code, Some("EMAIL_INVALID"))
      assertEquals(error.severity, Some("ERROR"))
      assertEquals(error.expected, Some("valid email format"))
      assertEquals(error.actual, Some("invalid@"))
    }
  }
}

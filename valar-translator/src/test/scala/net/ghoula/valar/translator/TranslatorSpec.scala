package net.ghoula.valar.translator

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationResult
import net.ghoula.valar.munit.ValarSuite

/** Provides a comprehensive test suite for the [[Translator]] typeclass and its associated
  * `translateErrors` extension method.
  *
  * This specification validates the core functionalities of the translation mechanism. It ensures
  * that `Valid` instances are returned without modification and that `Invalid` instances have their
  * error messages properly translated by the in-scope `Translator`.
  *
  * The suite also confirms the integrity of `ValidationError` objects post-translation, verifying
  * that all properties, such as `fieldPath`, `code`, and `severity`, are preserved. Finally, it
  * guarantees that the translation is not applied recursively to nested child errors, maintaining
  * the original state of the error hierarchy.
  */
class TranslatorSpec extends ValarSuite {

  test("translateErrors on a Valid result should return the instance unchanged") {
    given Translator = error => fail(s"Translator should not be invoked, but was called for: ${error.message}")

    val validResult = ValidationResult.Valid("all good")
    val result = validResult.translateErrors()

    assertEquals(result, validResult)
  }

  test("translateErrors on an Invalid result should translate messages and preserve all other properties") {
    given Translator = error => s"translated: ${error.message}"

    val originalError = ValidationError(
      message = "A test error",
      fieldPath = List("user", "email"),
      children = Vector(ValidationError("A nested error")),
      code = Some("E-101"),
      severity = Some("Warning"),
      expected = Some("a valid email"),
      actual = Some("not-an-email")
    )
    val invalidResult = ValidationResult.invalid(originalError)

    val translatedResult = invalidResult.translateErrors()

    assertHasOneError(translatedResult) { translatedError =>
      assertEquals(translatedError.message, "translated: A test error")
      assertEquals(translatedError.fieldPath, originalError.fieldPath)
      assertEquals(translatedError.children, originalError.children)
      assertEquals(translatedError.code, originalError.code)
      assertEquals(translatedError.severity, originalError.severity)
      assertEquals(translatedError.expected, originalError.expected)
      assertEquals(translatedError.actual, originalError.actual)
    }
  }

  test("translateErrors should correctly translate multiple errors in an Invalid result") {
    given Translator = error => s"translated: ${error.message}"

    val error1 = ValidationError("First error")
    val error2 = ValidationError("Second error")
    val invalidResult = ValidationResult.Invalid(Vector(error1, error2))

    val translatedResult = invalidResult.translateErrors()

    assertHasNErrors(translatedResult, 2)(translatedErrors =>
      assertEquals(translatedErrors.map(_.message), Vector("translated: First error", "translated: Second error"))
    )
  }

  test("translateErrors should not apply translation recursively to nested child errors") {
    given Translator = error => s"translated: ${error.message}"

    val childError = ValidationError("This is a child error")
    val parentError = ValidationError("This is a parent error", children = Vector(childError))
    val invalidResult = ValidationResult.invalid(parentError)
    val translatedResult = invalidResult.translateErrors()

    assertHasOneError(translatedResult) { translatedParent =>
      assertEquals(translatedParent.message, "translated: This is a parent error")
      assertEquals(translatedParent.children.headOption, Some(childError))
      assertEquals(translatedParent.children.head.message, "This is a child error")
    }
  }
}

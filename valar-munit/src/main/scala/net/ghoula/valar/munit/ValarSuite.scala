package net.ghoula.valar.munit

import munit.{FunSuite, Location}

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationResult

/** A base suite for MUnit tests that provides validation-specific assertion helpers.
  *
  * This suite provides a complete toolbox for testing Valar's validation logic:
  *   - `assertValid` for success cases.
  *   - `assertHasOneError` for testing single validation rules.
  *   - `assertInvalid` for testing complex error accumulation.
  */
trait ValarSuite extends FunSuite {

  /** Asserts that a `ValidationResult` is `Valid`.
    * @return
    *   The validated value `A` on success, allowing for chained assertions.
    */
  def assertValid[A](result: ValidationResult[A], clue: => Any = "Expected Valid, but got Invalid")(using
    loc: Location
  ): A = {
    result match {
      case ValidationResult.Valid(value) => value
      case ValidationResult.Invalid(errors) =>
        val errorReport = errors.map(e => s"  - ${e.prettyPrint(2)}").mkString("\n")
        fail(s"$clue. Errors:\n$errorReport")
    }
  }

  /** Asserts that a `ValidationResult` is `Invalid` and contains exactly one error. This is the
    * ideal helper for testing individual validation rules.
    *
    * @param result
    *   The `ValidationResult` to check.
    * @param pf
    *   A partial function to run assertions on the single `ValidationError`.
    * @return
    *   The single `ValidationError` on success.
    */
  def assertHasOneError(
    result: ValidationResult[?]
  )(pf: PartialFunction[ValidationError, Unit])(using loc: Location): ValidationError = {
    val errors = assertInvalid(result) {
      case allErrors if allErrors.size == 1 =>
      case allErrors => fail(s"Expected a single validation error, but found ${allErrors.size}.")
    }
    val singleError = errors.head
    if (!pf.isDefinedAt(singleError)) {
      fail(s"Partial function was not defined for the validation error:\n  - ${singleError.prettyPrint(2)}")
    }
    pf(singleError)
    singleError
  }

  /** Asserts that a `ValidationResult` is `Invalid`. Use this for complex cases where multiple,
    * accumulated errors are expected.
    *
    * @param result
    *   The `ValidationResult` to check.
    * @param pf
    *   A partial function to run assertions on the `Vector[ValidationError]`.
    * @return
    *   The `Vector[ValidationError]` on success.
    */
  def assertInvalid(
    result: ValidationResult[?]
  )(pf: PartialFunction[Vector[ValidationError], Unit])(using loc: Location): Vector[ValidationError] = {
    result match {
      case ValidationResult.Valid(value) =>
        fail(s"Expected Invalid, but got Valid($value)")
      case ValidationResult.Invalid(errors) =>
        if (!pf.isDefinedAt(errors)) {
          val errorReport = errors.map(e => s"  - ${e.prettyPrint(2)}").mkString("\n")
          fail(s"Partial function was not defined for the validation errors:\n$errorReport")
        }
        pf(errors)
        errors
    }
  }
}

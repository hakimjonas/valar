package net.ghoula.valar.munit

import munit.{FunSuite, Location}

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationResult

/** A base trait for test suites that use Valar, providing convenient assertion helpers for working
  * with ValidationResult.
  */
trait ValarSuite extends FunSuite {

  /** Asserts that a ValidationResult is Valid and returns the validated value for further
    * assertions.
    *
    * @param result
    *   The ValidationResult to inspect.
    * @param clue
    *   A clue to provide if the assertion fails.
    * @return
    *   The validated value if the result is Valid.
    */
  def assertValid[A](result: ValidationResult[A], clue: Any = "Expected Valid result")(using loc: Location): A = {
    result match {
      case ValidationResult.Valid(value) => value
      case ValidationResult.Invalid(errors) =>
        val errorReport = errors.map(e => s"  - ${e.prettyPrint(2)}").mkString("\n")
        fail(s"$clue, but got Invalid with errors:\n$errorReport")
    }
  }

  /** Asserts that a ValidationResult is Invalid and contains exactly one error, then allows further
    * assertions on that single error.
    *
    * @param result
    *   The ValidationResult to inspect.
    * @param clue
    *   A clue to provide if the assertion fails.
    * @param body
    *   A function that takes the single ValidationError and performs further checks.
    */
  def assertHasOneError[A](result: ValidationResult[A], clue: Any = "Expected exactly one validation error")(
    body: ValidationError => Unit
  )(using loc: Location): Unit = {
    assertHasNErrors(result, 1, clue) { errors => body(errors.head) }
  }

  /** Asserts that a ValidationResult is Invalid and contains a specific number of errors, then
    * allows further assertions on the collection of errors.
    *
    * @param result
    *   The ValidationResult to inspect.
    * @param expectedSize
    *   The expected number of errors.
    * @param clue
    *   A clue to provide if the assertion fails.
    * @param body
    *   A function that takes the Vector of ValidationErrors and performs further checks.
    */
  def assertHasNErrors[A](result: ValidationResult[A], expectedSize: Int, clue: Any = "Mismatched number of errors")(
    body: Vector[ValidationError] => Unit
  )(using loc: Location): Unit = {
    result match {
      case ValidationResult.Valid(value) =>
        fail(s"Expected $expectedSize validation errors, but the result was Valid($value).")
      case ValidationResult.Invalid(errors) =>
        if (errors.size == expectedSize) {
          body(errors)
        } else {
          fail(s"$clue. Expected $expectedSize errors, but found ${errors.size}.")
        }
    }
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
  def assertInvalid[A](
    result: ValidationResult[A]
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

  /** Asserts that a ValidationResult is Invalid and allows flexible assertions on the error
    * collection. This is a simpler alternative to `assertInvalid` that works with regular
    * functions.
    *
    * @param result
    *   The ValidationResult to inspect.
    * @param clue
    *   A clue to provide if the assertion fails.
    * @param body
    *   A function that takes the Vector of ValidationErrors and performs further checks.
    * @return
    *   The Vector of ValidationErrors on success.
    */
  def assertInvalidWith[A](
    result: ValidationResult[A],
    clue: Any = "Expected Invalid result"
  )(body: Vector[ValidationError] => Unit)(using loc: Location): Vector[ValidationError] = {
    result match {
      case ValidationResult.Valid(value) =>
        fail(s"$clue, but got Valid($value)")
      case ValidationResult.Invalid(errors) =>
        body(errors)
        errors
    }
  }

}

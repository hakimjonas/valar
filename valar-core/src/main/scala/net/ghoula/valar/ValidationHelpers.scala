package net.ghoula.valar

import scala.util.control.NonFatal
import scala.util.matching.Regex

import net.ghoula.valar.ValidationErrors.ValidationError

/** Provides reusable helper functions for common validation scenarios.
  *
  * Includes utilities for:
  *   - String constraints (non-empty, length)
  *   - Numeric constraints (non-negative, finite)
  *   - Regex pattern matching
  *   - Optional and required field handling
  *
  * @since 0.4.0
  */
object ValidationHelpers {

  /** Validates an input that might be null by treating it as an Option. This is useful for
    * validating fields from sources like JSON or databases that may be null.
    *
    * @example
    *   {{{
    * // A validation function for strings
    * val checkNonEmpty = (s: String) => nonEmpty(s)
    *
    * // Handles a non-null, valid value
    * optionValidator("hello", checkNonEmpty) // Returns Valid(Some("hello"))
    *
    * // Handles a null value gracefully
    * optionValidator(null, checkNonEmpty) // Returns Valid(None)
    *
    * // Fails on a null value when errorOnEmpty is true
    * optionValidator(null, checkNonEmpty, errorOnEmpty = true) // Returns Invalid(...)
    *   }}}
    *
    * @param value
    *   The input value, which may be null.
    * @param validationFn
    *   The validation function to apply if the value is not null.
    * @param errorOnEmpty
    *   If true, a null input is considered an `Invalid` result.
    * @param emptyErrorMsg
    *   The custom error message to use when `errorOnEmpty` is true and the input is null.
    * @return
    *   A `ValidationResult` containing an `Option` of the validated value, or an error.
    */
  def optionValidator[T](
    value: T,
    validationFn: T => ValidationResult[T],
    errorOnEmpty: Boolean = false,
    emptyErrorMsg: String = "Value must not be empty/null"
  ): ValidationResult[Option[T]] = {
    Option(value) match {
      case Some(v) => validationFn(v).map(Some(_))
      case None =>
        if (errorOnEmpty) ValidationResult.invalid(ValidationError(emptyErrorMsg))
        else ValidationResult.Valid(None)
    }
  }

  /** Validates that a string is non-empty (contains non-whitespace characters). Considers `null`
    * invalid.
    *
    * @param s
    *   The string to validate.
    * @param errorMessage
    *   A function that produces an error message. It receives the original (invalid) string as
    *   input, allowing for dynamic error messages.
    * @return
    *   A `ValidationResult` with the original string or an error.
    */
  def nonEmpty(
    s: String,
    errorMessage: String => String = _ => "String must not be empty"
  ): ValidationResult[String] = {
    Option(s) match {
      case Some(str) if str.trim.nonEmpty => ValidationResult.Valid(str)
      case Some(str) =>
        ValidationResult.invalid(
          ValidationError(message = errorMessage(str), expected = Some("non-empty string"), actual = Some(str))
        )
      case None =>
        ValidationResult.invalid(
          ValidationError(message = errorMessage("null"), expected = Some("non-empty string"), actual = Some("null"))
        )
    }
  }

  /** Validates that an integer is non-negative (>= 0).
    *
    * @param i
    *   The integer to validate.
    * @param errorMessage
    *   A function that produces an error message, receiving the invalid integer as input.
    * @return
    *   A `ValidationResult` with the original integer or an error.
    */
  def nonNegativeInt(i: Int, errorMessage: Int => String = _ => "Int must be non-negative"): ValidationResult[Int] =
    if (i >= 0) ValidationResult.Valid(i)
    else
      ValidationResult.invalid(
        ValidationError(message = errorMessage(i), expected = Some(">= 0"), actual = Some(i.toString))
      )

  /** Validates that a numeric value is finite (not NaN or infinite).
    *
    * @param value
    *   The numeric value to validate.
    * @param errorMessage
    *   A function producing an error message.
    * @return
    *   A `ValidationResult` with the original value or an error.
    */
  private def finiteNumeric[T](
    value: T,
    isFinite: T => Boolean,
    errorMessage: T => String
  ): ValidationResult[T] = {
    if (isFinite(value)) ValidationResult.Valid(value)
    else
      ValidationResult.invalid(
        ValidationError(message = errorMessage(value), expected = Some("finite value"), actual = Some(value.toString))
      )
  }

  /** Validates that a float is finite (not NaN or infinite).
    *
    * @param f
    *   The float to validate.
    * @param errorMessage
    *   A function producing an error message.
    * @return
    *   A `ValidationResult` with the original float or an error.
    */
  def finiteFloat(
    f: Float,
    errorMessage: Float => String = (_: Float) => "Float must be finite"
  ): ValidationResult[Float] =
    finiteNumeric(f, _.isFinite, errorMessage)

  /** Validates that a double is finite (not NaN or infinite).
    *
    * @param d
    *   The double to validate.
    * @param errorMessage
    *   A function producing an error message.
    * @return
    *   A `ValidationResult` with the original double or an error.
    */
  def finiteDouble(
    d: Double,
    errorMessage: Double => String = (_: Double) => "Double must be finite"
  ): ValidationResult[Double] =
    finiteNumeric(d, _.isFinite, errorMessage)

  /** Validates that a string has a minimum length. Null is invalid.
    * @return
    *   A `ValidationResult` with the original string or an error.
    */
  def minLengthValidator(s: String, min: Int)(errorMessage: String => String = value => {
    val actualValue = if (value == "null") "null" else value.length.toString
    s"Actual length ($actualValue) is less than minimum required length of $min"
  }): ValidationResult[String] = {
    Option(s) match {
      case Some(str) if str.length >= min => ValidationResult.Valid(str)
      case Some(str) =>
        ValidationResult.invalid(
          ValidationError(
            message = errorMessage(str),
            expected = Some(s"length >= $min"),
            actual = Some(str.length.toString)
          )
        )
      case None =>
        ValidationResult.invalid(
          ValidationError(message = errorMessage("null"), expected = Some(s"length >= $min"), actual = Some("null"))
        )
    }
  }

  /** Validates that a string does not exceed a maximum length. Null is invalid.
    * @return
    *   A `ValidationResult` with the original string or an error.
    */
  def maxLengthValidator(s: String, max: Int)(
    errorMessage: String => String = value => s"Length (${value.length}) exceeds maximum allowed length of $max"
  ): ValidationResult[String] = {
    Option(s) match {
      case Some(str) if str.length <= max => ValidationResult.Valid(str)
      case Some(str) =>
        ValidationResult.invalid(
          ValidationError(
            message = errorMessage(str),
            expected = Some(s"length <= $max"),
            actual = Some(str.length.toString)
          )
        )
      case None =>
        ValidationResult.invalid(
          ValidationError(
            message = "Input must be a non-null string (actual: null)",
            expected = Some(s"non-null string with length <= $max"),
            actual = Some("null")
          )
        )
    }
  }

  /** Validates a string against a pre-compiled regex pattern. Null is invalid.
    *
    * @param s
    *   The string to validate.
    * @param regex
    *   The compiled `Regex` object.
    * @param errorMessage
    *   A function that produces an error message, receiving the invalid string as input.
    * @return
    *   A `ValidationResult` with the original string or an error.
    */
  def regexMatch(s: String, regex: Regex)(
    errorMessage: String => String = value => s"Value '$value' does not match pattern '${regex.pattern.toString}'"
  ): ValidationResult[String] = {
    Option(s) match {
      case Some(str) if regex.matches(str) => ValidationResult.Valid(str)
      case Some(str) =>
        ValidationResult.invalid(
          ValidationError(message = errorMessage(str), expected = Some(regex.pattern.toString), actual = Some(str))
        )
      case None =>
        ValidationResult.invalid(
          ValidationError(
            message = errorMessage("null"),
            expected = Some(regex.pattern.toString),
            actual = Some("null")
          )
        )
    }
  }

  /** Validates a string against a string pattern. This overload handles potential
    * `java.util.regex.PatternSyntaxException` by returning an `Invalid` result.
    *
    * '''⚠️ SECURITY WARNING - ReDoS Vulnerability:''' This method accepts user-provided regex
    * patterns and is '''UNSAFE''' for untrusted input. Maliciously crafted regex patterns can cause
    * catastrophic backtracking (Regular Expression Denial of Service - ReDoS), leading to CPU
    * exhaustion and application hang.
    *
    * '''Recommendations:'''
    *   - For '''production use with untrusted input''': Use [[regexMatch(s: String, regex: Regex)]]
    *     with pre-compiled, developer-controlled regex patterns
    *   - For '''developer-defined validators only''': This method is safe when the pattern is
    *     hardcoded in your application code
    *   - '''Never''' pass user-submitted regex patterns to this method
    *
    * '''Example of UNSAFE usage:'''
    * {{{
    * // DO NOT DO THIS - userInput could be malicious!
    * val userPattern = request.getParameter("pattern")
    * regexMatch(value, userPattern)(_ => "Invalid")
    * }}}
    *
    * '''Example of SAFE usage:'''
    * {{{
    * // SAFE - pattern is developer-controlled
    * regexMatch(email, "^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$")(_ => "Invalid email")
    * }}}
    *
    * @param s
    *   The string to validate.
    * @param patternString
    *   The string representation of the regex pattern. '''Must be developer-controlled, not
    *   user-provided.'''
    * @param errorMessage
    *   A function that produces an error message.
    * @return
    *   A `ValidationResult` with the original string or an error.
    */
  def regexMatch(s: String, patternString: String)(errorMessage: String => String): ValidationResult[String] = {
    try {
      val regex = patternString.r
      regexMatch(s, regex)(errorMessage)
    } catch {
      case e: java.util.regex.PatternSyntaxException =>
        ValidationResult.invalid(
          ValidationError(
            message = s"Invalid regex pattern: ${e.getMessage}",
            code = Some("validation.regex.invalid_pattern")
          )
        )
      case NonFatal(e) =>
        ValidationResult.invalid(
          ValidationError(message = s"Error validating regex: ${e.getMessage}", code = Some("validation.regex.error"))
        )
    }
  }

  /** Validates a string against a string pattern using a default error message. The empty parameter
    * list `()` is a Scala 3 convention allowing for calls like `regexMatch("a", "[b]")`.
    *
    * '''⚠️ SECURITY WARNING - ReDoS Vulnerability:''' This method is '''UNSAFE''' for untrusted
    * regex patterns. See
    * [[regexMatch(s: String, patternString: String)(errorMessage: String => String)]] for full
    * security documentation. Only use this with developer-controlled, hardcoded patterns.
    *
    * @param s
    *   The string to validate.
    * @param patternString
    *   The string representation of the regex pattern. '''Must be developer-controlled, not
    *   user-provided.'''
    * @return
    *   A `ValidationResult` with the original string or an error.
    */
  def regexMatch(s: String, patternString: String)(): ValidationResult[String] = {
    val defaultErrorMessage: String => String = value => s"Value '$value' does not match pattern '$patternString'"
    regexMatch(s, patternString)(defaultErrorMessage)
  }

  /** Validates that an integer is within a specified inclusive range `[min, max]`.
    * @return
    *   A `ValidationResult` with the original integer or an error.
    */
  def inRange(i: Int, min: Int, max: Int)(
    errorMessage: Int => String = _ => s"Must be in range [$min, $max]"
  ): ValidationResult[Int] = {
    if (i >= min && i <= max) ValidationResult.Valid(i)
    else
      ValidationResult.invalid(
        ValidationError(message = errorMessage(i), expected = Some(s"[$min, $max]"), actual = Some(i.toString))
      )
  }

  /** Validates that a value is present within a `Set` of allowed values.
    * @return
    *   A `ValidationResult` with the original value or an error.
    */
  def oneOf[A](a: A, validValues: Set[A])(
    errorMessage: A => String = (_: A) => s"Must be one of ${validValues.mkString(", ")}"
  ): ValidationResult[A] = {
    if (validValues.contains(a)) ValidationResult.Valid(a)
    else
      ValidationResult.invalid(
        ValidationError(
          message = errorMessage(a),
          expected = Some(validValues.mkString(", ")),
          actual = Some(a.toString)
        )
      )
  }

  /** Ensures an `Option[A]` is a `Some`, returning the inner value `A`. Fails if the Option is
    * `None`.
    *
    * @param a
    *   The `Option` to check.
    * @param errorMessage
    *   The error message to use if the `Option` is `None`.
    * @return
    *   A `ValidationResult` with the inner value `A` or an error.
    */
  def required[A](a: Option[A], errorMessage: String = "Required value must not be empty/null"): ValidationResult[A] = {
    a match {
      case Some(value) => ValidationResult.Valid(value)
      case None =>
        ValidationResult.invalid(
          ValidationError(message = errorMessage, expected = Some("defined Option (Some)"), actual = Some("None"))
        )
    }
  }

  /** A helper that validates the value *inside* an `Option[A]` if it is a `Some`, using the
    * implicitly available `Validator[A]`. This is the underlying logic for the
    * `given Validator[Option[A]]`.
    *
    * @param opt
    *   The `Option` to validate.
    * @param v
    *   The implicit validator for the inner type `A`.
    * @return
    *   A `ValidationResult` containing the `Option`.
    */
  def optional[A](opt: Option[A])(using v: Validator[A]): ValidationResult[Option[A]] = {
    opt match {
      case Some(value) => v.validate(value).map(Some(_))
      case None => ValidationResult.Valid(None)
    }
  }
}

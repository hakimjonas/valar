package net.ghoula.valar

import net.ghoula.valar.ValidationErrors.ValidationError

import scala.util.control.NonFatal
import scala.util.matching.Regex

/** Provides reusable helper functions for common validation scenarios.
  *
  * Includes utilities for:
  *   - Non-empty string validation
  *   - Numeric validation (positive integers, finite floats/doubles)
  *   - Regex pattern matching with robust error handling
  *   - Optional and required field handling
  */
object ValidationHelpers {

  /** Validates an input that might be null by treating it as an Option. */
  def optionValidator[T](
    value: T,
    validationFn: T => ValidationResult[T],
    errorOnEmpty: Boolean = false,
    emptyErrorMsg: String = "Value must not be empty/null"
  ): ValidationResult[Option[T]] = {
    Option(value) match {
      case Some(v) => validationFn(v).map(Some(_))
      case None =>
        if (errorOnEmpty)
          ValidationResult.invalid(ValidationError(emptyErrorMsg))
        else
          ValidationResult.Valid(None)
    }
  }

  /** Validates that a string is non-empty (contains non-whitespace characters). Null is invalid. */
  def nonEmpty(
    s: String,
    errorMessage: String => String = _ => "String must not be empty"
  ): ValidationResult[String] = {
    Option(s) match {
      case Some(str) if str.trim.nonEmpty =>
        ValidationResult.Valid(str)

      case Some(str) =>
        ValidationResult.invalid(
          ValidationError(
            message = errorMessage(str),
            expected = Some("non-empty string"),
            actual = Some(str)
          )
        )

      case None =>
        ValidationResult.invalid(
          ValidationError(
            message = errorMessage("null"),
            expected = Some("non-empty string"),
            actual = Some("null")
          )
        )
    }
  }

  /** Validates that an integer is non-negative (>= 0). */
  def positiveInt(i: Int, errorMessage: Int => String = _ => "Int must be non-negative"): ValidationResult[Int] =
    if i >= 0 then ValidationResult.Valid(i)
    else
      ValidationResult.invalid(
        ValidationError(
          message = errorMessage(i),
          expected = Some(">= 0"),
          actual = Some(i.toString)
        )
      )

  /** Validates that a float is finite (not NaN or infinite). */
  def finiteFloat(f: Float, errorMessage: Float => String = _ => "Float must be finite"): ValidationResult[Float] =
    if f.isFinite then ValidationResult.Valid(f)
    else
      ValidationResult.invalid(
        ValidationError(
          message = errorMessage(f),
          expected = Some("finite value"),
          actual = Some(f.toString)
        )
      )

  /** Validates that a double is finite (not NaN or infinite). */
  def finiteDouble(d: Double, errorMessage: Double => String = _ => "Double must be finite"): ValidationResult[Double] =
    if d.isFinite then ValidationResult.Valid(d)
    else
      ValidationResult.invalid(
        ValidationError(
          message = errorMessage(d),
          expected = Some("finite value"),
          actual = Some(d.toString)
        )
      )

  /** Validates that a string is non-null and does not exceed a maximum length. Null is invalid. */
  def maxLengthValidator(
    s: String,
    max: Int
  )(
    errorMessage: String => String = value => s"Length (${value.length}) exceeds maximum allowed length of $max"
  ): ValidationResult[String] = {
    Option(s) match {
      case Some(str) if str.length <= max =>
        ValidationResult.Valid(str)

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

  /** Validates that a string is non-null and meets a minimum length requirement. Null is invalid.
    */
  def minLengthValidator(
    s: String,
    min: Int
  )(
    errorMessage: String => String = value => {
      val actualValue = if (value == "null") "null" else value.length.toString
      s"Actual length ($actualValue) is less than minimum required length of $min"
    }
  ): ValidationResult[String] = {
    Option(s) match {
      case Some(str) if str.length >= min =>
        ValidationResult.Valid(str)

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
          ValidationError(
            message = errorMessage("null"),
            expected = Some(s"length >= $min"),
            actual = Some("null")
          )
        )
    }
  }

  /** Validates that a string is non-null and matches a pre-compiled regex. Null is invalid. */
  def regexMatch(
    s: String,
    regex: Regex
  )(
    errorMessage: String => String = (value: String) =>
      s"Value '$value' does not match pattern '${regex.pattern.toString}'"
  ): ValidationResult[String] = {
    Option(s) match {
      case Some(str) if regex.matches(str) =>
        ValidationResult.Valid(str)

      case Some(str) =>
        ValidationResult.invalid(
          ValidationError(
            message = errorMessage(str),
            expected = Some(regex.pattern.toString),
            actual = Some(str)
          )
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

  /** Validates using a String pattern and a custom error message. Null is invalid. Handles invalid
    * patterns.
    */
  def regexMatch(
    s: String,
    patternString: String
  )(
    errorMessage: String => String
  ): ValidationResult[String] = {
    try {
      val regex = patternString.r
      regexMatch(s, regex)(errorMessage)
    } catch {
      case e: java.util.regex.PatternSyntaxException =>
        ValidationResult.invalid(
          ValidationError(
            message = s"Invalid regex pattern: ${e.getMessage}",
            code = Some("validation.regex.invalid_pattern"),
            severity = Some("Error"),
            expected = Some("Valid Regex Pattern"),
            actual = Some(patternString)
          )
        )
      case NonFatal(e) =>
        ValidationResult.invalid(
          ValidationError(
            message = s"Error validating regex: ${e.getMessage}",
            code = Some("validation.regex.error"),
            severity = Some("Error"),
            expected = Some("Valid Regex Pattern"),
            actual = Some(patternString)
          )
        )
    }
  }

  /** Validates using a String pattern and a default error message. Null is invalid. Handles invalid
    * patterns.
    */
  def regexMatch(
    s: String,
    patternString: String
  )(): ValidationResult[String] = {
    val defaultErrorMessageForStringPattern: String => String = value =>
      s"Value '$value' does not match pattern '$patternString'"
    regexMatch(s, patternString)(defaultErrorMessageForStringPattern)
  }

  /** Validates that an integer is within a specified range [min, max]. */
  def inRange(i: Int, min: Int, max: Int)(
    errorMessage: Int => String = i => s"Must be in range [$min, $max]"
  ): ValidationResult[Int] =
    if i >= min && i <= max then ValidationResult.Valid(i)
    else
      ValidationResult.invalid(
        ValidationError(
          message = errorMessage(i),
          expected = Some(s"[$min, $max]"),
          actual = Some(i.toString)
        )
      )

  /** Validates that a value is present within a set of allowed values. */
  def oneOf[A](a: A, validValues: Set[A])(
    errorMessage: A => String = (a: A) => s"Must be one of ${validValues.mkString(", ")}"
  ): ValidationResult[A] =
    if validValues.contains(a) then ValidationResult.Valid(a)
    else
      ValidationResult.invalid(
        ValidationError(
          message = errorMessage(a),
          expected = Some(validValues.mkString(", ")),
          actual = Some(a.toString)
        )
      )

  /** Validates that an `Option[A]` is `Some`, returning the inner value if so. */
  def required[A](a: Option[A], errorMessage: String = "Required value must not be empty/null"): ValidationResult[A] =
    a match {
      case Some(value) => ValidationResult.Valid(value)
      case None =>
        ValidationResult.invalid(
          ValidationError(
            message = errorMessage,
            expected = Some("defined Option (Some)"),
            actual = Some("None")
          )
        )
    }

  /** Validates the value *inside* an `Option[A]` if present using the implicit `Validator[A]`. */
  def optional[A](opt: Option[A])(using v: Validator[A]): ValidationResult[Option[A]] =
    opt match {
      case Some(value) => v.validate(value).map(Some(_))
      case None => ValidationResult.Valid(None)
    }
}

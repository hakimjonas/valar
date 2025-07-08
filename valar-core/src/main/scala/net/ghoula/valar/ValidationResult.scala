package net.ghoula.valar

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import net.ghoula.valar
import net.ghoula.valar.ValidationErrors.{ValidationError, ValidationException}

/** Represents the outcome of a validation operation, containing either a successfully validated
  * value or validation errors.
  *
  * `ValidationResult` is designed to support two primary error handling strategies:
  *   - '''Error Accumulation''' (default): Collects all validation failures, ideal for providing
  *     comprehensive feedback
  *   - '''Fail-Fast''': Stops on the first validation failure, suitable for performance-critical
  *     scenarios
  *
  * The type provides monadic operations (`map`, `flatMap`) for chaining validations and applicative
  * operations (`zip`, `mapN`) for combining independent validations while preserving all errors.
  *
  * @tparam A
  *   The type of the value when validation succeeds
  *
  * @example
  *   {{{ // Success case val valid: ValidationResult[Int] = ValidationResult.Valid(42)
  *
  * // Failure case val invalid: ValidationResult[String] = ValidationResult.invalid(
  * ValidationError("Value must not be empty") )
  *
  * // Chaining validations (fail-fast) val result = valid.flatMap(i => ValidationResult.Valid(i *
  * 2))
  *
  * // Combining validations (error accumulation) val combined =
  * valid.zip(ValidationResult.Valid("hello")) }}}
  */
enum ValidationResult[+A] {

  /** Successful validation result containing a validated value.
    *
    * @param value
    *   The successfully validated value
    */
  case Valid(value: A) extends ValidationResult[A]

  /** Failed validation result containing one or more validation errors.
    *
    * @param errors
    *   A non-empty collection of validation errors that occurred during validation
    */
  case Invalid(errors: Vector[ValidationError]) extends ValidationResult[Nothing]
}

/** Companion object providing factory methods, type class instances, and extension methods for
  * `ValidationResult`.
  *
  * This object contains utilities for creating validation results, converting from other types like
  * `Either` and `Try`, and specialized validation functions for union types and type checking.
  */
object ValidationResult {

  /** Type Class Instances
    *
    * Default error accumulator and other typeclass instances for ValidationResult.
    */

  /** Default error accumulator for combining validation errors.
    *
    * This instance enables error accumulation when using operations like `zip` and `mapN`. It
    * combines error vectors by concatenating them, preserving the order of errors.
    */
  given validationErrorAccumulator: ErrorAccumulator[Vector[ValidationError]] =
    valar.ErrorAccumulator.vectorAccumulator[ValidationError]

  /** Factory Methods
    *
    * Methods for creating ValidationResult instances from values or errors.
    */

  /** Creates a successful validation result.
    *
    * @param value
    *   The validated value
    * @tparam A
    *   The type of the value
    * @return
    *   A `Valid` result containing the value
    */
  private def valid[A](value: A): ValidationResult[A] = Valid(value)

  /** Creates a failed validation result with a single error.
    *
    * @param error
    *   The validation error
    * @tparam A
    *   The expected type (for type inference)
    * @return
    *   An `Invalid` result containing the error
    */
  def invalid[A](error: ValidationError): ValidationResult[A] = Invalid(Vector(error))

  /** Creates a failed validation result with multiple errors.
    *
    * @param errors
    *   A non-empty vector of validation errors
    * @tparam A
    *   The expected type (for type inference)
    * @return
    *   An `Invalid` result containing all errors
    * @throws IllegalArgumentException
    *   if the vector is empty
    */
  def invalid[A](errors: Vector[ValidationError]): ValidationResult[A] = {
    require(errors.nonEmpty, "Cannot create Invalid with empty errors vector")
    Invalid(errors)
  }

  /** Conversion from Other Types
    *
    * Methods for converting from Either, Try, and other common types.
    */

  /** Converts an `Either[ValidationError, A]` to a `ValidationResult[A]`.
    *
    * @param either
    *   The either value to convert
    * @tparam A
    *   The success type
    * @return
    *   A validation result equivalent to the either value
    */
  def fromEither[A](either: Either[ValidationError, A]): ValidationResult[A] = either match {
    case Right(a) => valid(a)
    case Left(e) => invalid(e)
  }

  /** Converts an `Either[Vector[ValidationError], A]` to a `ValidationResult[A]`.
    *
    * Handles the edge case where the error vector might be empty by creating a synthetic error
    * indicating a programming mistake.
    *
    * @param either
    *   The either value with multiple errors to convert
    * @tparam A
    *   The success type
    * @return
    *   A validation result equivalent to the either value
    */
  def fromEitherErrors[A](either: Either[Vector[ValidationError], A]): ValidationResult[A] =
    either match {
      case Right(a) =>
        valid(a)
      case Left(es) if es.nonEmpty =>
        invalid(es)
      case Left(_) =>
        invalid(
          ValidationErrors.ValidationError(
            message = "Programmer error: Cannot create Invalid ValidationResult from an empty error vector",
            code = Some("validation.error.fromEither.empty"),
            severity = Some("Error")
          )
        )
    }

  /** Specialized Validation Methods
    *
    * Methods for validating union types and performing type-safe conversions.
    */

  /** Validates a value against a specific type using its associated validator.
    *
    * This method first checks if the value is of the expected type using pattern matching, then
    * applies the type's validator if the cast succeeds.
    *
    * @param value
    *   The value to validate
    * @param validator
    *   The validator for type `A`
    * @param ct
    *   Class tag for runtime type checking
    * @tparam A
    *   The target type
    * @return
    *   Validation result for the typed and validated value
    */
  private def validateType[A](
    value: Any
  )(using validator: Validator[A], ct: ClassTag[A]): ValidationResult[A] = value match {
    case a: A => validator.validate(a)
    case _ =>
      invalid(
        ValidationErrors.ValidationError(s"Value is not of type ${ct.runtimeClass.getSimpleName}")
      )
  }

  /** Validates a value against a union type `A | B`.
    *
    * Attempts to validate the value as both type `A` and type `B`. Returns the first successful
    * validation or combines all errors if both fail. This enables validation of sum types where a
    * value must match at least one of several possible types.
    *
    * @param value
    *   The value to validate
    * @param va
    *   Validator for type `A`
    * @param vb
    *   Validator for type `B`
    * @param ctA
    *   Class tag for type `A`
    * @param ctB
    *   Class tag for type `B`
    * @tparam A
    *   First possible type
    * @tparam B
    *   Second possible type
    * @return
    *   Validation result for the union type, containing the successfully validated value or
    *   combined errors
    */
  def validateUnion[A, B](
    value: Any
  )(using
    va: Validator[A],
    vb: Validator[B],
    ctA: ClassTag[A],
    ctB: ClassTag[B]
  ): ValidationResult[A | B] = {
    val resultA = validateType[A](value)(using va, ctA)
    val resultB = validateType[B](value)(using vb, ctB)

    (resultA, resultB) match {
      case (Valid(_), _) => resultA
      case (_, Valid(_)) => resultB
      case (Invalid(errsA), Invalid(errsB)) =>
        val typeAName = ctA.runtimeClass.getSimpleName
        val typeBName = ctB.runtimeClass.getSimpleName
        val expectedTypes = s"$typeAName | $typeBName"
        val summaryMessage = s"Value failed validation for all expected types: $expectedTypes"

        val combinedError: ValidationError = ValidationError(
          message = summaryMessage,
          children = errsA ++ errsB,
          expected = Some(expectedTypes),
          actual = Some(value.toString)
        )
        invalid(combinedError)
    }
  }

  /** Extension Methods
    *
    * Functional operations for ValidationResult instances.
    */
  extension [A](vr: ValidationResult[A]) {

    /** Basic Transformations
      *
      * Core operations like map and fold for transforming ValidationResult.
      */

    /** Transforms the value inside a successful validation result.
      *
      * If the result is `Valid`, applies the function to the contained value. If the result is
      * `Invalid`, returns the errors unchanged.
      *
      * @param f
      *   Function to transform the valid value
      * @tparam B
      *   The result type of the transformation
      * @return
      *   A new validation result with the transformed value or original errors
      */
    def map[B](f: A => B): ValidationResult[B] = vr match {
      case Valid(a) => Valid(f(a))
      case Invalid(errs) => Invalid(errs)
    }

    /** Eliminates the validation result by applying one of two functions.
      *
      * Provides a way to extract a value of a common type from either success or failure cases.
      *
      * @param ifValid
      *   Function to apply to a successful value
      * @param ifInvalid
      *   Function to apply to validation errors
      * @tparam B
      *   The common result type
      * @return
      *   The result of applying the appropriate function
      */
    def fold[B](ifValid: A => B, ifInvalid: Vector[ValidationError] => B): B = vr match {
      case Valid(a) => ifValid(a)
      case Invalid(errs) => ifInvalid(errs)
    }

    /** Sequential Composition (Fail-Fast)
      *
      * Monadic operations that stop on the first error.
      */

    /** Chains validation operations with fail-fast semantics.
      *
      * If this result is `Valid`, applies the validation function to the value. If this result is
      * `Invalid`, returns the errors without applying the function. This enables sequential
      * validation where later steps depend on earlier successes.
      *
      * @param f
      *   Function that performs additional validation
      * @tparam B
      *   The type produced by the validation function
      * @return
      *   The result of applying the validation function, or the original errors
      */
    def flatMap[B](f: A => ValidationResult[B]): ValidationResult[B] = vr match {
      case Valid(a) => f(a)
      case Invalid(errs) => Invalid(errs)
    }

    /** Combines two validation results with fail-fast semantics.
      *
      * Unlike `zip`, this stops on the first error rather than accumulating all errors. Useful when
      * you want early termination for performance or when later validations depend on earlier ones
      * succeeding.
      *
      * @param that
      *   The validation result to combine with this one
      * @tparam B
      *   The type of the second validation result
      * @return
      *   A tuple of both values if both are valid, or the first error encountered
      */
    def zipFailFast[B](that: => ValidationResult[B]): ValidationResult[(A, B)] = vr match {
      case Valid(a) =>
        that match {
          case Valid(b) => Valid((a, b))
          case Invalid(errs) => Invalid(errs)
        }
      case Invalid(errs) => Invalid(errs)
    }

    /** Combines validation results with fail-fast semantics and a custom combiner function.
      *
      * Fail-fast version of `mapN` that stops on the first validation error.
      *
      * @param that
      *   A validation result containing a tuple to combine with this value
      * @param f
      *   Function to combine this value with the tuple from `that`
      * @tparam T
      *   The tuple type from the second validation result
      * @tparam R
      *   The result type of the combination function
      * @return
      *   The combined value if both validations succeed, or the first error encountered
      */
    def mapNFailFast[T <: Tuple, R](that: ValidationResult[T])(
      f: (A *: T) => R
    ): ValidationResult[R] = vr match {
      case Valid(a) =>
        that match {
          case Valid(t) => Valid(f(a *: t))
          case Invalid(errs) => Invalid(errs)
        }
      case Invalid(errs) => Invalid(errs)
    }

    /** Parallel Composition (Error Accumulation)
      *
      * Operations that combine independent validations and accumulate all errors.
      */

    /** Combines two validation results, accumulating all errors.
      *
      * If both results are valid, returns a tuple of both values. If either or both are invalid,
      * combines all errors using the error accumulator. This enables parallel validation where you
      * want to collect all possible errors.
      *
      * @param that
      *   The validation result to combine with this one
      * @param acc
      *   Error accumulator for combining error collections
      * @tparam B
      *   The type of the second validation result
      * @return
      *   A validation result containing a tuple of both values or all combined errors
      */
    def zip[B](
      that: => ValidationResult[B]
    )(using acc: ErrorAccumulator[Vector[ValidationError]]): ValidationResult[(A, B)] = (vr, that) match {
      case (Valid(a), Valid(b)) => Valid((a, b))
      case (Invalid(errs1), Invalid(errs2)) => Invalid(acc.combine(errs1, errs2))
      case (Invalid(errs), _) => Invalid(errs)
      case (_, Invalid(errs)) => Invalid(errs)
    }

    /** Combines validation results with applicative semantics and a custom combiner function.
      *
      * Similar to `zip` but allows specifying how to combine the successful values. Accumulates all
      * errors if any validation fails.
      *
      * @param that
      *   A validation result containing a tuple to combine with this value
      * @param f
      *   Function to combine this value with the tuple from `that`
      * @param acc
      *   Error accumulator for combining error collections
      * @tparam T
      *   The tuple type from the second validation result
      * @tparam R
      *   The result type of the combination function
      * @return
      *   A validation result with the combined value or all accumulated errors
      */
    def mapN[T <: Tuple, R](that: ValidationResult[T])(
      f: (A *: T) => R
    )(using acc: ErrorAccumulator[Vector[ValidationError]]): ValidationResult[R] = (vr, that) match {
      case (Valid(a), Valid(t)) => Valid(f(a *: t))
      case (Invalid(errs1), Invalid(errs2)) => Invalid(acc.combine(errs1, errs2))
      case (Invalid(errs), _) => Invalid(errs)
      case (_, Invalid(errs)) => Invalid(errs)
    }

    /** Alternative Operations
      *
      * Operations for trying alternative validations and providing fallbacks.
      */

    /** Returns the first successful result from two validation attempts.
      *
      * If this result is valid, returns it unchanged. If this result is invalid but `that` is
      * valid, returns `that`. If both are invalid, combines their errors.
      *
      * @param that
      *   Alternative validation result to try
      * @param acc
      *   Error accumulator for combining errors if both fail
      * @tparam B
      *   The type of the alternative result (must be compatible with A)
      * @return
      *   The first successful result or combined errors
      */
    def or[B >: A](
      that: ValidationResult[B]
    )(using acc: ErrorAccumulator[Vector[ValidationError]]): ValidationResult[B] = (vr, that) match {
      case (Valid(_), _) => vr
      case (_, Valid(_)) => that
      case (Invalid(errsA), Invalid(errsB)) => Invalid(acc.combine(errsA, errsB))
    }

    /** Returns this result if valid, otherwise evaluates and returns the alternative.
      *
      * Lazy version of `or` that only evaluates the alternative if this result is invalid. Combines
      * errors if both results are invalid.
      *
      * @param that
      *   Alternative validation result (evaluated lazily)
      * @param acc
      *   Error accumulator for combining errors
      * @return
      *   This result if valid, or the alternative result, or combined errors
      */
    def orElse(
      that: => ValidationResult[A]
    )(using acc: ErrorAccumulator[Vector[ValidationError]]): ValidationResult[A] = vr match {
      case Valid(a) => Valid(a)
      case Invalid(errs) =>
        that match {
          case Valid(b) => Valid(b)
          case Invalid(errs2) => Invalid(acc.combine(errs, errs2))
        }
    }

    /** Provides a default value for failed validations.
      *
      * If this result is valid, returns it unchanged. If this result is invalid, returns a valid
      * result with the provided default value.
      *
      * @param default
      *   Default value to use if validation failed (evaluated lazily)
      * @return
      *   This result if valid, or a valid result with the default value
      */
    def recover(default: => A): ValidationResult[A] = vr match {
      case Valid(a) => Valid(a)
      case Invalid(_) => Valid(default)
    }

    /** Conversion to Other Types
      *
      * Methods for converting ValidationResult to standard Scala types.
      */

    /** Converts to a `Try`, representing validation errors as exceptions.
      *
      * Success becomes `Success`, and the first validation error becomes a `Failure` containing a
      * `ValidationException`. Other errors are lost in this conversion.
      *
      * @return
      *   A `Try` representing the validation result
      */
    def toTry: Try[A] = vr match {
      case Valid(a) => Success(a)
      case Invalid(errs) => Failure(new ValidationException(errs.head))
    }

    /** Converts to `Either`, preserving all error information.
      *
      * Success becomes `Right`, and all validation errors are preserved in `Left`.
      *
      * @return
      *   An `Either` with all errors on the left or the value on the right
      */
    def toEither: Either[Vector[ValidationError], A] = vr match {
      case Valid(a) => Right(a)
      case Invalid(errs) => Left(errs)
    }

    /** Converts to an `Option`, discarding error information.
      *
      * Success becomes `Some`, and any validation errors become `None`. All error details are lost
      * in this conversion.
      *
      * @return
      *   An `Option` representing success or failure
      */
    def toOption: Option[A] = fold(Some(_), _ => None)

    /** Converts to a `List`, treating success as a single-element list.
      *
      * Success becomes a single-element list, and validation errors become an empty list.
      *
      * @return
      *   A list containing the value if valid, or empty if invalid
      */
    def toList: List[A] = fold(List(_), _ => Nil)

    /** Converts to a `Vector`, treating success as a single-element vector.
      *
      * Success becomes a single-element vector, and validation errors become an empty vector.
      *
      * @return
      *   A vector containing the value if valid, or empty if invalid
      */
    def toVector: Vector[A] = fold(Vector(_), _ => Vector.empty)
  }
}

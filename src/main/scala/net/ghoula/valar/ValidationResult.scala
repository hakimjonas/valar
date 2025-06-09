package net.ghoula.valar

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import net.ghoula.valar.ValidationErrors.{ValidationError, ValidationException}
import net.ghoula.valar.internal.{ErrorAccumulator, MacroHelpers}

/** Represents the result of a validation process, either a valid value or validation errors.
  *
  * Supports two primary error handling strategies:
  *   - Error accumulation (default) for gathering multiple validation errors.
  *   - Fail-fast for immediate error handling.
  *
  * @tparam A
  *   The type of the value in case of successful validation.
  */
enum ValidationResult[+A] {

  /** Successful validation result holding a validated value.
    *
    * @param value
    *   the successfully validated value
    */
  case Valid(value: A) extends ValidationResult[A]

  /** Failed validation result holding accumulated validation errors.
    *
    * @param errors
    *   non-empty collection of validation errors
    */
  case Invalid(errors: Vector[ValidationError]) extends ValidationResult[Nothing]
}

/** Companion object for the [[ValidationResult]] enum. Provides factory methods, implicit
  * instances, and extension methods.
  */
object ValidationResult {

  /** Provides the default ErrorAccumulator for Vector[ValidationError]. */
  given validationErrorAccumulator: ErrorAccumulator[Vector[ValidationError]] =
    internal.ErrorAccumulator.vectorAccumulator[ValidationError]

  /** Creates a [[Valid]] instance. */
  def valid[A](value: A): ValidationResult[A] = Valid(value)

  /** Creates an [[Invalid]] instance with a single error. */
  def invalid[A](error: ValidationError): ValidationResult[A] = Invalid(Vector(error))

  /** Creates an [[Invalid]] instance with multiple errors. */
  def invalid[A](errors: Vector[ValidationError]): ValidationResult[A] = {
    require(errors.nonEmpty, "Cannot create Invalid with empty errors vector")
    Invalid(errors)
  }

  /** Converts an [[Either[ValidationError, A]]] to a [[ValidationResult]]. */
  def fromEither[A](either: Either[ValidationError, A]): ValidationResult[A] = either match {
    case Right(a) => valid(a)
    case Left(e) => invalid(e)
  }

  /** Converts an [[Either[Vector[ValidationError], A]]] into a [[ValidationResult]]. */
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

  /** Validates a value against a target type `A` using its implicitly available [[Validator]]. */
  def validateType[A](
    value: Any
  )(using validator: Validator[A], ct: ClassTag[A]): ValidationResult[A] = value match {
    case a: A => validator.validate(a)
    case _ =>
      invalid(
        ValidationErrors.ValidationError(s"Value is not of type ${ct.runtimeClass.getSimpleName}")
      )
  }

  /** Validates a value against a union type `A | B`. */
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
      case (Valid(_), _) => MacroHelpers.upcastTo(resultA)
      case (_, Valid(_)) => MacroHelpers.upcastTo(resultB)
      case (Invalid(errsA), Invalid(errsB)) =>
        val typeAName = ctA.runtimeClass.getSimpleName
        val typeBName = ctB.runtimeClass.getSimpleName
        val expectedTypes = s"$typeAName | $typeBName"
        val summaryMessage = s"Value failed validation for all expected types: $expectedTypes"
        val allNestedErrors: Vector[ValidationError] = errsA ++ errsB

        val combinedError: ValidationError = ValidationErrors.ValidationError(
          message = summaryMessage,
          fieldPath = Nil,
          children = allNestedErrors,
          code = None,
          severity = None,
          expected = Some(expectedTypes),
          actual = Some(value.toString)
        )
        ValidationResult.invalid(combinedError)
    }
  }

  /** Provides extension methods for [[ValidationResult]]. */
  extension [A](vr: ValidationResult[A]) {

    /** Transforms the value inside a [[Valid]] result using function `f`. */
    def map[B](f: A => B): ValidationResult[B] = vr match {
      case Valid(a) => Valid(f(a))
      case Invalid(errs) => Invalid(errs)
    }

    /** Chains validation functions (fail-fast). */
    def flatMap[B](f: A => ValidationResult[B]): ValidationResult[B] = vr match {
      case Valid(a) => f(a)
      case Invalid(errs) => Invalid(errs)
    }

    /** Combines two results, accumulating errors via `ErrorAccumulator`. */
    def zip[B](
      that: => ValidationResult[B]
    )(using acc: ErrorAccumulator[Vector[ValidationError]]): ValidationResult[(A, B)] = (vr, that) match {
      case (Valid(a), Valid(b)) => Valid((a, b))
      case (Invalid(errs1), Invalid(errs2)) => Invalid(acc.combine(errs1, errs2))
      case (Invalid(errs), _) => Invalid(errs)
      case (_, Invalid(errs)) => Invalid(errs)
    }

    /** Returns the first [[Valid]] result, or combines errors if both are [[Invalid]]. */
    def or[B](
      that: ValidationResult[B]
    )(using acc: ErrorAccumulator[Vector[ValidationError]]): ValidationResult[A | B] = (vr, that) match {
      case (Valid(_), _) => MacroHelpers.upcastTo(vr)
      case (_, Valid(_)) => MacroHelpers.upcastTo(that)
      case (Invalid(errsA), Invalid(errsB)) => Invalid(acc.combine(errsA, errsB))
    }

    /** Returns this result if [[Valid]], otherwise evaluates and returns `that`. Combines errors if
      * both are [[Invalid]].
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

    /** Recovers from an [[Invalid]] state with a default value. */
    def recover(default: => A): ValidationResult[A] = vr match {
      case Valid(a) => Valid(a)
      case Invalid(_) => Valid(default)
    }

    /** Folds over the result, applying one function for [[Valid]] and another for [[Invalid]]. */
    def fold[B](ifValid: A => B, ifInvalid: Vector[ValidationError] => B): B = vr match {
      case Valid(a) => ifValid(a)
      case Invalid(errs) => ifInvalid(errs)
    }

    /** Converts to `Try`, wrapping the first error in a `ValidationException`. */
    def toTry: Try[A] = vr match {
      case Valid(a) => Success(a)
      case Invalid(errs) => Failure(new ValidationException(errs.head))
    }

    /** Converts to `Either`, preserving all errors in `Left`. */
    def toEither: Either[Vector[ValidationError], A] = vr match {
      case Valid(a) => Right(a)
      case Invalid(errs) => Left(errs)
    }

    /** Converts to `Option`, discarding errors. */
    def toOption: Option[A] = fold(Some(_), _ => None)

    /** Converts to `List`, discarding errors. */
    def toList: List[A] = fold(List(_), _ => Nil)

    /** Converts to `Vector`, discarding errors. */
    def toVector: Vector[A] = fold(Vector(_), _ => Vector.empty)

    /** Combines results applicatively, accumulating errors. */
    def mapN[T <: Tuple, R](that: ValidationResult[T])(
      f: (A *: T) => R
    )(using acc: ErrorAccumulator[Vector[ValidationError]]): ValidationResult[R] = (vr, that) match {
      case (Valid(a), Valid(t)) => Valid(f(a *: t))
      case (Invalid(errs1), Invalid(errs2)) => Invalid(acc.combine(errs1, errs2))
      case (Invalid(errs), _) => Invalid(errs)
      case (_, Invalid(errs)) => Invalid(errs)
    }

    /** Zips results, failing fast on the first error. */
    def zipFailFast[B](that: => ValidationResult[B]): ValidationResult[(A, B)] = vr match {
      case Valid(a) =>
        that match {
          case Valid(b) => Valid((a, b))
          case Invalid(errs) => Invalid(errs)
        }
      case Invalid(errs) => Invalid(errs)
    }

    /** Combines results applicatively, failing fast on the first error. */
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
  }
}

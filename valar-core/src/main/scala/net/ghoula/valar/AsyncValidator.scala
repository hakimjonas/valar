package net.ghoula.valar

import java.time.*
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.deriving.Mirror
import scala.quoted.{Expr, Quotes, Type}

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.internal.Derivation

/** A typeclass for defining custom asynchronous validation logic for type `A`.
  *
  * This is used for validations that involve non-blocking I/O, such as checking for uniqueness in a
  * database or calling an external service.
  *
  * @tparam A
  *   the type to be validated
  */
trait AsyncValidator[A] {

  /** Asynchronously validate an instance of type `A`.
    *
    * @param a
    *   the instance to validate
    * @param ec
    *   the execution context for the Future
    * @return
    *   a `Future` containing the `ValidationResult[A]`
    */
  def validateAsync(a: A)(using ec: ExecutionContext): Future[ValidationResult[A]]
}

/** Companion object for the [[AsyncValidator]] typeclass. */
object AsyncValidator {

  /** Summons an implicit [[AsyncValidator]] instance for type `A`. */
  def apply[A](using v: AsyncValidator[A]): AsyncValidator[A] = v

  /** Lifts a synchronous `Validator` into an `AsyncValidator`.
    *
    * This allows synchronous validators to be used seamlessly in an asynchronous validation chain.
    *
    * @param v
    *   the synchronous validator to lift
    * @return
    *   an `AsyncValidator` that wraps the result in a `Future.successful`.
    */
  def fromSync[A](v: Validator[A]): AsyncValidator[A] = new AsyncValidator[A] {
    def validateAsync(a: A)(using ec: ExecutionContext): Future[ValidationResult[A]] =
      Future.successful(v.validate(a))
  }

  /** Generic helper method for folding validation results into errors and valid values.
    *
    * @param results
    *   the sequence of validation results to fold
    * @param emptyAcc
    *   the empty accumulator for valid values
    * @param addToAcc
    *   function to add a valid value to the accumulator
    * @return
    *   a tuple containing accumulated errors and valid values
    */
  private def foldValidationResults[A, B](
    results: Iterable[ValidationResult[A]],
    emptyAcc: B,
    addToAcc: (B, A) => B
  ): (Vector[ValidationError], B) = {
    results.foldLeft((Vector.empty[ValidationError], emptyAcc)) {
      case ((errs, acc), ValidationResult.Valid(value)) => (errs, addToAcc(acc, value))
      case ((errs, acc), ValidationResult.Invalid(e)) => (errs ++ e, acc)
    }
  }

  /** Generic helper method for validating collections asynchronously.
    *
    * This method eliminates code duplication by providing a common validation pattern for different
    * collection types. It validates each element in the collection asynchronously and accumulates
    * both errors and valid results.
    *
    * @param items
    *   the collection of items to validate
    * @param validator
    *   the validator for individual items
    * @param buildResult
    *   function to construct the final collection from valid items
    * @param ec
    *   execution context for async operations
    * @return
    *   a Future containing the validation result
    */
  private def validateCollection[A, C[_]](
    items: Iterable[A],
    validator: AsyncValidator[A],
    buildResult: Iterable[A] => C[A]
  )(using ec: ExecutionContext): Future[ValidationResult[C[A]]] = {
    val futureResults = items.map { item =>
      validator.validateAsync(item).map {
        case ValidationResult.Valid(a) => ValidationResult.Valid(a)
        case ValidationResult.Invalid(errors) => ValidationResult.Invalid(errors)
      }
    }

    Future.sequence(futureResults).map { results =>
      val (errors, validValues) = foldValidationResults(results, Vector.empty[A], _ :+ _)
      if (errors.isEmpty) ValidationResult.Valid(buildResult(validValues))
      else ValidationResult.Invalid(errors)
    }
  }

  /** Asynchronous validator for optional values.
    *
    * Validates an `Option[A]` by delegating to the underlying validator only when the value is
    * present. Empty options are considered valid by default.
    *
    * @param v
    *   the validator for the wrapped type A
    * @return
    *   an AsyncValidator that handles optional values
    */
  given optionAsyncValidator[A](using v: AsyncValidator[A]): AsyncValidator[Option[A]] with {
    def validateAsync(opt: Option[A])(using ec: ExecutionContext): Future[ValidationResult[Option[A]]] =
      opt match {
        case None => Future.successful(ValidationResult.Valid(None))
        case Some(value) =>
          v.validateAsync(value).map {
            case ValidationResult.Valid(a) => ValidationResult.Valid(Some(a))
            case ValidationResult.Invalid(errors) => ValidationResult.Invalid(errors)
          }
      }
  }

  /** Asynchronous validator for lists.
    *
    * Validates a `List[A]` by applying the element validator to each item in the list
    * asynchronously. All validation futures are executed concurrently, and their results are
    * collected. Errors from individual elements are accumulated while preserving the order of valid
    * elements.
    *
    * @param v
    *   the validator for list elements
    * @return
    *   an AsyncValidator that handles lists
    */
  given listAsyncValidator[A](using v: AsyncValidator[A]): AsyncValidator[List[A]] with {
    def validateAsync(xs: List[A])(using ec: ExecutionContext): Future[ValidationResult[List[A]]] =
      validateCollection(xs, v, _.toList)
  }

  /** Asynchronous validator for sequences.
    *
    * Validates a `Seq[A]` by applying the element validator to each item in the sequence
    * asynchronously. All validation futures are executed concurrently, and their results are
    * collected. Errors from individual elements are accumulated while preserving the order of valid
    * elements.
    *
    * @param v
    *   the validator for sequence elements
    * @return
    *   an AsyncValidator that handles sequences
    */
  given seqAsyncValidator[A](using v: AsyncValidator[A]): AsyncValidator[Seq[A]] with {
    def validateAsync(xs: Seq[A])(using ec: ExecutionContext): Future[ValidationResult[Seq[A]]] =
      validateCollection(xs, v, _.toSeq)
  }

  /** Asynchronous validator for vectors.
    *
    * Validates a `Vector[A]` by applying the element validator to each item in the vector
    * asynchronously. All validation futures are executed concurrently, and their results are
    * collected. Errors from individual elements are accumulated while preserving the order of valid
    * elements.
    *
    * @param v
    *   the validator for vector elements
    * @return
    *   an AsyncValidator that handles vectors
    */
  given vectorAsyncValidator[A](using v: AsyncValidator[A]): AsyncValidator[Vector[A]] with {
    def validateAsync(xs: Vector[A])(using ec: ExecutionContext): Future[ValidationResult[Vector[A]]] =
      validateCollection(xs, v, _.toVector)
  }

  /** Asynchronous validator for sets.
    *
    * Validates a `Set[A]` by applying the element validator to each item in the set asynchronously.
    * All validation futures are executed concurrently, and their results are collected. Errors from
    * individual elements are accumulated while preserving the valid elements in the resulting set.
    *
    * @param v
    *   the validator for set elements
    * @return
    *   an AsyncValidator that handles sets
    */
  given setAsyncValidator[A](using v: AsyncValidator[A]): AsyncValidator[Set[A]] with {
    def validateAsync(xs: Set[A])(using ec: ExecutionContext): Future[ValidationResult[Set[A]]] =
      validateCollection(xs, v, _.toSet)
  }

  /** Asynchronous validator for maps.
    *
    * Validates a `Map[K, V]` by applying the key validator to each key and the value validator to
    * each value asynchronously. All validation futures are executed concurrently, and their results
    * are collected. Errors from individual keys and values are accumulated with proper field path
    * annotation, while valid key-value pairs are preserved in the resulting map.
    *
    * @param vk
    *   the validator for map keys
    * @param vv
    *   the validator for map values
    * @return
    *   an AsyncValidator that handles maps
    */
  given mapAsyncValidator[K, V](using vk: AsyncValidator[K], vv: AsyncValidator[V]): AsyncValidator[Map[K, V]] with {
    def validateAsync(m: Map[K, V])(using ec: ExecutionContext): Future[ValidationResult[Map[K, V]]] = {
      val futureResults = m.map { case (k, v) =>
        val futureKey = vk.validateAsync(k).map {
          case ValidationResult.Valid(kk) => ValidationResult.Valid(kk)
          case ValidationResult.Invalid(es) =>
            ValidationResult.Invalid(es.map(_.annotateField("key", k.getClass.getSimpleName)))
        }
        val futureValue = vv.validateAsync(v).map {
          case ValidationResult.Valid(vv) => ValidationResult.Valid(vv)
          case ValidationResult.Invalid(es) =>
            ValidationResult.Invalid(es.map(_.annotateField("value", v.getClass.getSimpleName)))
        }

        for {
          keyResult <- futureKey
          valueResult <- futureValue
        } yield keyResult.zip(valueResult)
      }

      Future.sequence(futureResults).map { results =>
        val (errors, validPairs) = foldValidationResults(results, Map.empty[K, V], _ + _)
        if (errors.isEmpty) ValidationResult.Valid(validPairs) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Asynchronous validator for non-negative integers.
    *
    * Validates that an integer value is non-negative (>= 0). This validator is lifted from the
    * corresponding synchronous validator and is used as a fallback when no custom integer validator
    * is provided.
    */
  given nonNegativeIntAsyncValidator: AsyncValidator[Int] = fromSync(Validator.nonNegativeIntValidator)

  /** Asynchronous validator for finite floating-point numbers.
    *
    * Validates that a float value is finite (not NaN or infinite). This validator is lifted from
    * the corresponding synchronous validator and is used as a fallback when no custom float
    * validator is provided.
    */
  given finiteFloatAsyncValidator: AsyncValidator[Float] = fromSync(Validator.finiteFloatValidator)

  /** Asynchronous validator for finite double-precision numbers.
    *
    * Validates that a double value is finite (not NaN or infinite). This validator is lifted from
    * the corresponding synchronous validator and is used as a fallback when no custom double
    * validator is provided.
    */
  given finiteDoubleAsyncValidator: AsyncValidator[Double] = fromSync(Validator.finiteDoubleValidator)

  /** Asynchronous validator for non-empty strings.
    *
    * Validates that a string value is not empty. This validator is lifted from the corresponding
    * synchronous validator and is used as a fallback when no custom string validator is provided.
    */
  given nonEmptyStringAsyncValidator: AsyncValidator[String] = fromSync(Validator.nonEmptyStringValidator)

  /** Asynchronous validator for boolean values.
    *
    * Pass-through validator for boolean values that always succeeds. This validator is lifted from
    * the corresponding synchronous validator and provides consistent async behavior.
    */
  given booleanAsyncValidator: AsyncValidator[Boolean] = fromSync(Validator.booleanValidator)

  /** Asynchronous validator for byte values.
    *
    * Pass-through validator for byte values that always succeeds. This validator is lifted from the
    * corresponding synchronous validator and provides consistent async behavior.
    */
  given byteAsyncValidator: AsyncValidator[Byte] = fromSync(Validator.byteValidator)

  /** Asynchronous validator for short values.
    *
    * Pass-through validator for short values that always succeeds. This validator is lifted from
    * the corresponding synchronous validator and provides consistent async behavior.
    */
  given shortAsyncValidator: AsyncValidator[Short] = fromSync(Validator.shortValidator)

  /** Asynchronous validator for long values.
    *
    * Pass-through validator for long values that always succeeds. This validator is lifted from the
    * corresponding synchronous validator and provides consistent async behavior.
    */
  given longAsyncValidator: AsyncValidator[Long] = fromSync(Validator.longValidator)

  /** Asynchronous validator for character values.
    *
    * Pass-through validator for character values that always succeeds. This validator is lifted
    * from the corresponding synchronous validator and provides consistent async behavior.
    */
  given charAsyncValidator: AsyncValidator[Char] = fromSync(Validator.charValidator)

  /** Asynchronous validator for unit values.
    *
    * Pass-through validator for unit values that always succeeds. This validator is lifted from the
    * corresponding synchronous validator and provides consistent async behavior.
    */
  given unitAsyncValidator: AsyncValidator[Unit] = fromSync(Validator.unitValidator)

  /** Asynchronous validator for arbitrary precision integers.
    *
    * Pass-through validator for BigInt values that always succeeds. This validator is lifted from
    * the corresponding synchronous validator and provides consistent async behavior.
    */
  given bigIntAsyncValidator: AsyncValidator[BigInt] = fromSync(Validator.bigIntValidator)

  /** Asynchronous validator for arbitrary precision decimal numbers.
    *
    * Pass-through validator for BigDecimal values that always succeeds. This validator is lifted
    * from the corresponding synchronous validator and provides consistent async behavior.
    */
  given bigDecimalAsyncValidator: AsyncValidator[BigDecimal] = fromSync(Validator.bigDecimalValidator)

  /** Asynchronous validator for symbol values.
    *
    * Pass-through validator for symbol values that always succeeds. This validator is lifted from
    * the corresponding synchronous validator and provides consistent async behavior.
    */
  given symbolAsyncValidator: AsyncValidator[Symbol] = fromSync(Validator.symbolValidator)

  /** Asynchronous validator for UUID values.
    *
    * Pass-through validator for UUID values that always succeeds. This validator is lifted from the
    * corresponding synchronous validator and provides consistent async behavior.
    */
  given uuidAsyncValidator: AsyncValidator[UUID] = fromSync(Validator.uuidValidator)

  /** Asynchronous validator for instant values.
    *
    * Pass-through validator for Instant values that always succeeds. This validator is lifted from
    * the corresponding synchronous validator and provides consistent async behavior.
    */
  given instantAsyncValidator: AsyncValidator[Instant] = fromSync(Validator.instantValidator)

  /** Asynchronous validator for local date values.
    *
    * Pass-through validator for LocalDate values that always succeeds. This validator is lifted
    * from the corresponding synchronous validator and provides consistent async behavior.
    */
  given localDateAsyncValidator: AsyncValidator[LocalDate] = fromSync(Validator.localDateValidator)

  /** Asynchronous validator for local time values.
    *
    * Pass-through validator for LocalTime values that always succeeds. This validator is lifted
    * from the corresponding synchronous validator and provides consistent async behavior.
    */
  given localTimeAsyncValidator: AsyncValidator[LocalTime] = fromSync(Validator.localTimeValidator)

  /** Asynchronous validator for local date-time values.
    *
    * Pass-through validator for LocalDateTime values that always succeeds. This validator is lifted
    * from the corresponding synchronous validator and provides consistent async behavior.
    */
  given localDateTimeAsyncValidator: AsyncValidator[LocalDateTime] = fromSync(Validator.localDateTimeValidator)

  /** Asynchronous validator for zoned date-time values.
    *
    * Pass-through validator for ZonedDateTime values that always succeeds. This validator is lifted
    * from the corresponding synchronous validator and provides consistent async behavior.
    */
  given zonedDateTimeAsyncValidator: AsyncValidator[ZonedDateTime] = fromSync(Validator.zonedDateTimeValidator)

  /** Asynchronous validator for duration values.
    *
    * Pass-through validator for Duration values that always succeeds. This validator is lifted from
    * the corresponding synchronous validator and provides consistent async behavior.
    */
  given javaDurationAsyncValidator: AsyncValidator[Duration] = fromSync(Validator.durationValidator)

  /** Automatically derives an `AsyncValidator` for case classes using Scala 3 macros.
    *
    * This method provides compile-time derivation of async validators for product types by
    * analyzing the case class structure and generating appropriate validation logic that validates
    * each field using the corresponding validator in scope.
    *
    * @param m
    *   the Mirror.ProductOf evidence for the type T
    * @return
    *   a derived AsyncValidator instance for type T
    */
  inline def derive[T](using m: Mirror.ProductOf[T]): AsyncValidator[T] =
    ${ deriveImpl[T, m.MirroredElemTypes, m.MirroredElemLabels]('m) }

  /** Macro implementation for deriving an `AsyncValidator`.
    *
    * This method implements the actual macro logic for generating async validator instances at
    * compile time. It delegates to the internal Derivation utility with the async flag set to true
    * to generate appropriate asynchronous validation code.
    *
    * @param m
    *   the Mirror.ProductOf expression
    * @return
    *   an expression representing the derived AsyncValidator
    */
  private def deriveImpl[T: Type, Elems <: Tuple: Type, Labels <: Tuple: Type](
    m: Expr[Mirror.ProductOf[T]]
  )(using q: Quotes): Expr[AsyncValidator[T]] = {
    Derivation.deriveValidatorImpl[T, Elems, Labels](m, isAsync = true).asExprOf[AsyncValidator[T]]
  }
}

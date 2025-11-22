package net.ghoula.valar

import java.time.*
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.deriving.Mirror
import scala.quoted.{Expr, Quotes, Type}

import net.ghoula.valar.internal.{Derivation, FutureEffect, ValidationLogic}

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

  /** Asynchronous validator for lists. */
  given listAsyncValidator[A](using v: AsyncValidator[A], config: ValidationConfig): AsyncValidator[List[A]] with {
    def validateAsync(xs: List[A])(using ec: ExecutionContext): Future[ValidationResult[List[A]]] =
      ValidationLogic.validateCollection(xs, identity, "List")(v.validateAsync)(using FutureEffect(), config)
  }

  /** Asynchronous validator for sequences. */
  given seqAsyncValidator[A](using v: AsyncValidator[A], config: ValidationConfig): AsyncValidator[Seq[A]] with {
    def validateAsync(xs: Seq[A])(using ec: ExecutionContext): Future[ValidationResult[Seq[A]]] =
      ValidationLogic.validateCollection(xs, _.toSeq, "Seq")(v.validateAsync)(using FutureEffect(), config)
  }

  /** Asynchronous validator for vectors. */
  given vectorAsyncValidator[A](using v: AsyncValidator[A], config: ValidationConfig): AsyncValidator[Vector[A]] with {
    def validateAsync(xs: Vector[A])(using ec: ExecutionContext): Future[ValidationResult[Vector[A]]] =
      ValidationLogic.validateCollection(xs, _.toVector, "Vector")(v.validateAsync)(using FutureEffect(), config)
  }

  /** Asynchronous validator for sets. */
  given setAsyncValidator[A](using v: AsyncValidator[A], config: ValidationConfig): AsyncValidator[Set[A]] with {
    def validateAsync(xs: Set[A])(using ec: ExecutionContext): Future[ValidationResult[Set[A]]] =
      ValidationLogic.validateCollection(xs, _.toSet, "Set")(v.validateAsync)(using FutureEffect(), config)
  }

  /** Asynchronous validator for maps. */
  given mapAsyncValidator[K, V](using
    vk: AsyncValidator[K],
    vv: AsyncValidator[V],
    config: ValidationConfig
  ): AsyncValidator[Map[K, V]] with {
    def validateAsync(m: Map[K, V])(using ec: ExecutionContext): Future[ValidationResult[Map[K, V]]] =
      ValidationLogic.validateMap(m)(vk.validateAsync, vv.validateAsync)(using FutureEffect(), config)
  }

  /** Pass-through async validator for Int. For constraints, define a custom AsyncValidator. */
  given intAsyncValidator: AsyncValidator[Int] = fromSync(Validator.intValidator)

  /** Pass-through async validator for Float. For constraints, define a custom AsyncValidator. */
  given floatAsyncValidator: AsyncValidator[Float] = fromSync(Validator.floatValidator)

  /** Pass-through async validator for Double. For constraints, define a custom AsyncValidator. */
  given doubleAsyncValidator: AsyncValidator[Double] = fromSync(Validator.doubleValidator)

  /** Pass-through async validator for String. For constraints, define a custom AsyncValidator. */
  given stringAsyncValidator: AsyncValidator[String] = fromSync(Validator.stringValidator)

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
  )(using q: Quotes): Expr[AsyncValidator[T]] =
    Derivation.deriveAsyncValidatorImpl[T, Elems, Labels](m)
}

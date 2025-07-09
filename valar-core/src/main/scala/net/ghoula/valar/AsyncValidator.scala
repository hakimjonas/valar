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

  // === Given instances for common types ===

  /** Default async validator for `Option[A]`. */
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

  /** Validates a `List[A]` by validating each element asynchronously. */
  given listAsyncValidator[A](using v: AsyncValidator[A]): AsyncValidator[List[A]] with {
    def validateAsync(xs: List[A])(using ec: ExecutionContext): Future[ValidationResult[List[A]]] = {
      val futureResults = xs.zipWithIndex.map { case (item, _) =>
        v.validateAsync(item).map {
          case ValidationResult.Valid(a) => ValidationResult.Valid(a)
          case ValidationResult.Invalid(errors) =>
            // Don't annotate with index, let the derivation handle field paths
            ValidationResult.Invalid(errors)
        }
      }

      Future.sequence(futureResults).map { results =>
        val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], List.empty[A])) {
          case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
          case ((errs, vals), ValidationResult.Invalid(e)) => (errs ++ e, vals)
        }
        if (errors.isEmpty) ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Validates a `Seq[A]` by validating each element asynchronously. */
  given seqAsyncValidator[A](using v: AsyncValidator[A]): AsyncValidator[Seq[A]] with {
    def validateAsync(xs: Seq[A])(using ec: ExecutionContext): Future[ValidationResult[Seq[A]]] = {
      val futureResults = xs.zipWithIndex.map { case (item, _) =>
        v.validateAsync(item).map {
          case ValidationResult.Valid(a) => ValidationResult.Valid(a)
          case ValidationResult.Invalid(errors) =>
            ValidationResult.Invalid(errors)
        }
      }

      Future.sequence(futureResults).map { results =>
        val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Seq.empty[A])) {
          case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
          case ((errs, vals), ValidationResult.Invalid(e)) => (errs ++ e, vals)
        }
        if (errors.isEmpty) ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Validates a `Vector[A]` by validating each element asynchronously. */
  given vectorAsyncValidator[A](using v: AsyncValidator[A]): AsyncValidator[Vector[A]] with {
    def validateAsync(xs: Vector[A])(using ec: ExecutionContext): Future[ValidationResult[Vector[A]]] = {
      val futureResults = xs.zipWithIndex.map { case (item, _) =>
        v.validateAsync(item).map {
          case ValidationResult.Valid(a) => ValidationResult.Valid(a)
          case ValidationResult.Invalid(errors) =>
            ValidationResult.Invalid(errors)
        }
      }

      Future.sequence(futureResults).map { results =>
        val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Vector.empty[A])) {
          case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
          case ((errs, vals), ValidationResult.Invalid(e)) => (errs ++ e, vals)
        }
        if (errors.isEmpty) ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Validates a `Set[A]` by validating each element asynchronously. */
  given setAsyncValidator[A](using v: AsyncValidator[A]): AsyncValidator[Set[A]] with {
    def validateAsync(xs: Set[A])(using ec: ExecutionContext): Future[ValidationResult[Set[A]]] = {
      val futureResults = xs.map(v.validateAsync(_))

      Future.sequence(futureResults).map { results =>
        val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Set.empty[A])) {
          case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals + a)
          case ((errs, vals), ValidationResult.Invalid(e)) => (errs ++ e, vals)
        }
        if (errors.isEmpty) ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Validates a `Map[K, V]` by validating each key and value asynchronously. */
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
        val (errors, validPairs) = results.foldLeft((Vector.empty[ValidationError], Map.empty[K, V])) {
          case ((errs, acc), ValidationResult.Valid(pair)) => (errs, acc + pair)
          case ((errs, acc), ValidationResult.Invalid(e)) => (errs ++ e, acc)
        }
        if (errors.isEmpty) ValidationResult.Valid(validPairs) else ValidationResult.Invalid(errors)
      }
    }
  }

  // === Lift all the built-in Validator instances to AsyncValidator ===
  // These are only used as fallbacks when no custom validators are provided

  /** Async validator for non-negative integers. */
  given nonNegativeIntAsyncValidator: AsyncValidator[Int] = fromSync(Validator.nonNegativeIntValidator)

  /** Async validator for finite floats. */
  given finiteFloatAsyncValidator: AsyncValidator[Float] = fromSync(Validator.finiteFloatValidator)

  /** Async validator for finite doubles. */
  given finiteDoubleAsyncValidator: AsyncValidator[Double] = fromSync(Validator.finiteDoubleValidator)

  /** Async validator for non-empty strings. */
  given nonEmptyStringAsyncValidator: AsyncValidator[String] = fromSync(Validator.nonEmptyStringValidator)

  // Pass-through validators for basic types
  given booleanAsyncValidator: AsyncValidator[Boolean] = fromSync(Validator.booleanValidator)
  given byteAsyncValidator: AsyncValidator[Byte] = fromSync(Validator.byteValidator)
  given shortAsyncValidator: AsyncValidator[Short] = fromSync(Validator.shortValidator)
  given longAsyncValidator: AsyncValidator[Long] = fromSync(Validator.longValidator)
  given charAsyncValidator: AsyncValidator[Char] = fromSync(Validator.charValidator)
  given unitAsyncValidator: AsyncValidator[Unit] = fromSync(Validator.unitValidator)
  given bigIntAsyncValidator: AsyncValidator[BigInt] = fromSync(Validator.bigIntValidator)
  given bigDecimalAsyncValidator: AsyncValidator[BigDecimal] = fromSync(Validator.bigDecimalValidator)
  given symbolAsyncValidator: AsyncValidator[Symbol] = fromSync(Validator.symbolValidator)
  given uuidAsyncValidator: AsyncValidator[UUID] = fromSync(Validator.uuidValidator)
  given instantAsyncValidator: AsyncValidator[Instant] = fromSync(Validator.instantValidator)
  given localDateAsyncValidator: AsyncValidator[LocalDate] = fromSync(Validator.localDateValidator)
  given localTimeAsyncValidator: AsyncValidator[LocalTime] = fromSync(Validator.localTimeValidator)
  given localDateTimeAsyncValidator: AsyncValidator[LocalDateTime] = fromSync(Validator.localDateTimeValidator)
  given zonedDateTimeAsyncValidator: AsyncValidator[ZonedDateTime] = fromSync(Validator.zonedDateTimeValidator)
  given javaDurationAsyncValidator: AsyncValidator[Duration] = fromSync(Validator.durationValidator)

  /** Automatically derives an `AsyncValidator` for case classes using Scala 3 macros. */
  inline def derive[T](using m: Mirror.ProductOf[T]): AsyncValidator[T] =
    ${ deriveImpl[T, m.MirroredElemTypes, m.MirroredElemLabels]('m) }

  /** Macro implementation for deriving an `AsyncValidator`. */
  private def deriveImpl[T: Type, Elems <: Tuple: Type, Labels <: Tuple: Type](
    m: Expr[Mirror.ProductOf[T]]
  )(using q: Quotes): Expr[AsyncValidator[T]] = {
    Derivation.deriveValidatorImpl[T, Elems, Labels](m, isAsync = true).asExprOf[AsyncValidator[T]]
  }
}

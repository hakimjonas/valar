package net.ghoula.valar

import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.ArraySeq
import scala.deriving.Mirror
import scala.language.reflectiveCalls
import scala.quoted.{Expr, Quotes, Type}
import scala.reflect.ClassTag

import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.ValidationResult.{validateUnion, given}
import net.ghoula.valar.internal.Derivation

/** A typeclass for defining custom validation logic for type `A`.
  *
  * Validators encapsulate the validation rules for a specific type and produce structured
  * `ValidationResult`s.
  *
  * @tparam A
  *   the type to be validated
  */
trait Validator[A] {

  /** Validate an instance of type `A`.
    *
    * @param a
    *   the instance to validate
    * @return
    *   ValidationResult[A] representing validation success or accumulated errors
    */
  def validate(a: A): ValidationResult[A]
}

/** Companion object for the [[Validator]] typeclass. */
object Validator {

  /** Summons an implicit [[Validator]] instance for type `A`. */
  def apply[A](using v: Validator[A]): Validator[A] = v

  // ... keep all the existing given instances exactly as they are ...

  /** Validates that an Int is non-negative (>= 0). Uses [[ValidationHelpers.nonNegativeInt]]. */
  given nonNegativeIntValidator: Validator[Int] with {
    def validate(i: Int): ValidationResult[Int] = nonNegativeInt(i)
  }

  /** Validates that a Float is finite (not NaN or infinite). Uses
    * [[ValidationHelpers.finiteFloat]].
    */
  given finiteFloatValidator: Validator[Float] with {
    def validate(f: Float): ValidationResult[Float] = finiteFloat(f)
  }

  /** Validates that a Double is finite (not NaN or infinite). Uses
    * [[ValidationHelpers.finiteDouble]].
    */
  given finiteDoubleValidator: Validator[Double] with {
    def validate(d: Double): ValidationResult[Double] = finiteDouble(d)
  }

  /** Validates that a String is non-empty. Uses [[ValidationHelpers.nonEmpty]]. */
  given nonEmptyStringValidator: Validator[String] with {
    def validate(s: String): ValidationResult[String] = nonEmpty(s)
  }

  /** Default validator for `Option[A]`. */
  given optionValidator[A](using v: Validator[A]): Validator[Option[A]] with {
    def validate(opt: Option[A]): ValidationResult[Option[A]] =
      optional(opt)(using v)
  }

  /** Validates a `List[A]` by validating each element.
    *
    * If a [[ValidationConfig]] is in scope with `maxCollectionSize` set, this validator will check
    * the collection size before processing elements, failing fast if the limit is exceeded.
    */
  given listValidator[A](using v: Validator[A], config: ValidationConfig): Validator[List[A]] with {
    def validate(xs: List[A]): ValidationResult[List[A]] = {
      config.checkCollectionSize(xs.size, "List").flatMap { _ =>
        val results = xs.map(v.validate)
        val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], List.empty[A])) {
          case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
          case ((errs, vals), ValidationResult.Invalid(e2)) => (errs ++ e2, vals)
        }
        if errors.isEmpty then ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Validates a `Seq[A]` by validating each element.
    *
    * If a [[ValidationConfig]] is in scope with `maxCollectionSize` set, this validator will check
    * the collection size before processing elements, failing fast if the limit is exceeded.
    */
  given seqValidator[A](using v: Validator[A], config: ValidationConfig): Validator[Seq[A]] with {
    def validate(xs: Seq[A]): ValidationResult[Seq[A]] = {
      config.checkCollectionSize(xs.size, "Seq").flatMap { _ =>
        val results = xs.map(v.validate)
        val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Seq.empty[A])) {
          case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
          case ((errs, vals), ValidationResult.Invalid(e2)) => (errs ++ e2, vals)
        }
        if errors.isEmpty then ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Validates a `Vector[A]` by validating each element.
    *
    * If a [[ValidationConfig]] is in scope with `maxCollectionSize` set, this validator will check
    * the collection size before processing elements, failing fast if the limit is exceeded.
    */
  given vectorValidator[A](using v: Validator[A], config: ValidationConfig): Validator[Vector[A]] with {
    def validate(xs: Vector[A]): ValidationResult[Vector[A]] = {
      config.checkCollectionSize(xs.size, "Vector").flatMap { _ =>
        val results = xs.map(v.validate)
        val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Vector.empty[A])) {
          case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals :+ a)
          case ((errs, vals), ValidationResult.Invalid(e2)) => (errs ++ e2, vals)
        }
        if errors.isEmpty then ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Validates a `Set[A]` by validating each element.
    *
    * If a [[ValidationConfig]] is in scope with `maxCollectionSize` set, this validator will check
    * the collection size before processing elements, failing fast if the limit is exceeded.
    */
  given setValidator[A](using v: Validator[A], config: ValidationConfig): Validator[Set[A]] with {
    def validate(xs: Set[A]): ValidationResult[Set[A]] = {
      config.checkCollectionSize(xs.size, "Set").flatMap { _ =>
        val results = xs.map(v.validate)
        val (errors, validValues) = results.foldLeft((Vector.empty[ValidationError], Set.empty[A])) {
          case ((errs, vals), ValidationResult.Valid(a)) => (errs, vals + a)
          case ((errs, vals), ValidationResult.Invalid(e2)) => (errs ++ e2, vals)
        }
        if errors.isEmpty then ValidationResult.Valid(validValues) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Validates a `Map[K, V]` by validating each key and value.
    *
    * If a [[ValidationConfig]] is in scope with `maxCollectionSize` set, this validator will check
    * the map size before processing entries, failing fast if the limit is exceeded.
    */
  given mapValidator[K, V](using vk: Validator[K], vv: Validator[V], config: ValidationConfig): Validator[Map[K, V]]
  with {
    def validate(m: Map[K, V]): ValidationResult[Map[K, V]] = {
      config.checkCollectionSize(m.size, "Map").flatMap { _ =>
        val results = m.map { case (k, v) =>
          val validatedKey: ValidationResult[K] = vk.validate(k) match {
            case ValidationResult.Valid(kk) => ValidationResult.Valid(kk)
            case ValidationResult.Invalid(es) =>
              ValidationResult.Invalid(
                es.map(e => e.annotateField("key", k.getClass.getSimpleName))
              )
          }
          val validatedValue: ValidationResult[V] = vv.validate(v) match {
            case ValidationResult.Valid(vv) => ValidationResult.Valid(vv)
            case ValidationResult.Invalid(es) =>
              ValidationResult.Invalid(
                es.map(e => e.annotateField("value", v.getClass.getSimpleName))
              )
          }
          validatedKey.zip(validatedValue)
        }
        val (errors, validPairs) = results.foldLeft((Vector.empty[ValidationError], Map.empty[K, V])) {
          case ((errs, acc), ValidationResult.Valid(pair)) => (errs, acc + pair)
          case ((errs, acc), ValidationResult.Invalid(e2)) => (errs ++ e2, acc)
        }
        if errors.isEmpty then ValidationResult.Valid(validPairs) else ValidationResult.Invalid(errors)
      }
    }
  }

  /** Helper for validating iterable collections. */
  private def validateIterable[A, C[_]](
    xs: Iterable[A],
    builder: Vector[A] => C[A]
  )(using v: Validator[A]): ValidationResult[C[A]] = {
    val resultsIterator = xs.iterator.map(v.validate)
    val initial = (Vector.empty[ValidationError], Vector.empty[A])
    val (errors, validValues) = resultsIterator.foldLeft(initial) {
      case ((currentErrors, currentValidValues), result) =>
        result match {
          case ValidationResult.Valid(a) => (currentErrors, currentValidValues :+ a)
          case ValidationResult.Invalid(e2) => (currentErrors ++ e2, currentValidValues)
        }
    }
    if (errors.isEmpty) ValidationResult.Valid(builder(validValues))
    else ValidationResult.Invalid(errors)
  }

  /** Validates an `Array[A]`.
    *
    * If a [[ValidationConfig]] is in scope with `maxCollectionSize` set, this validator will check
    * the array size before processing elements, failing fast if the limit is exceeded.
    */
  given arrayValidator[A](using v: Validator[A], ct: ClassTag[A], config: ValidationConfig): Validator[Array[A]] with {
    def validate(xs: Array[A]): ValidationResult[Array[A]] =
      config.checkCollectionSize(xs.length, "Array").flatMap { _ =>
        validateIterable(xs, (validValues: Vector[A]) => validValues.toArray)
      }
  }

  /** Validates an `ArraySeq[A]`.
    *
    * If a [[ValidationConfig]] is in scope with `maxCollectionSize` set, this validator will check
    * the collection size before processing elements, failing fast if the limit is exceeded.
    */
  given arraySeqValidator[A](using v: Validator[A], ct: ClassTag[A], config: ValidationConfig): Validator[ArraySeq[A]]
  with {
    def validate(xs: ArraySeq[A]): ValidationResult[ArraySeq[A]] =
      config.checkCollectionSize(xs.size, "ArraySeq").flatMap { _ =>
        validateIterable(xs, (validValues: Vector[A]) => ArraySeq.unsafeWrapArray(validValues.toArray))
      }
  }

  /** Validates an intersection type `A & B`. */
  given intersectionValidator[A, B](using va: Validator[A], vb: Validator[B]): Validator[A & B] with {
    def validate(ab: A & B): ValidationResult[A & B] =
      va.validate(ab).zip(vb.validate(ab)).map(_ => ab)
  }

  /** Validates a union type `A | B`. */
  given unionValidator[A, B](using
    va: Validator[A],
    vb: Validator[B],
    ctA: ClassTag[A],
    ctB: ClassTag[B]
  ): Validator[A | B] with {
    def validate(value: A | B): ValidationResult[A | B] = validateUnion[A, B](value)(using va, vb, ctA, ctB)
  }

  /** This section provides "pass-through" `given` instances that always return `Valid`. */
  inline given booleanValidator: Validator[Boolean] with {
    def validate(b: Boolean): ValidationResult[Boolean] = ValidationResult.Valid(b)
  }
  inline given byteValidator: Validator[Byte] with {
    def validate(b: Byte): ValidationResult[Byte] = ValidationResult.Valid(b)
  }
  inline given shortValidator: Validator[Short] with {
    def validate(s: Short): ValidationResult[Short] = ValidationResult.Valid(s)
  }
  inline given longValidator: Validator[Long] with {
    def validate(l: Long): ValidationResult[Long] = ValidationResult.Valid(l)
  }
  inline given charValidator: Validator[Char] with {
    def validate(c: Char): ValidationResult[Char] = ValidationResult.Valid(c)
  }
  inline given unitValidator: Validator[Unit] with {
    def validate(u: Unit): ValidationResult[Unit] = ValidationResult.Valid(u)
  }
  inline given bigIntValidator: Validator[BigInt] with {
    def validate(bi: BigInt): ValidationResult[BigInt] = ValidationResult.Valid(bi)
  }
  inline given bigDecimalValidator: Validator[BigDecimal] with {
    def validate(bd: BigDecimal): ValidationResult[BigDecimal] = ValidationResult.Valid(bd)
  }
  inline given symbolValidator: Validator[Symbol] with {
    def validate(s: Symbol): ValidationResult[Symbol] = ValidationResult.Valid(s)
  }
  inline given uuidValidator: Validator[UUID] with {
    def validate(v: UUID): ValidationResult[UUID] = ValidationResult.Valid(v)
  }
  inline given instantValidator: Validator[Instant] with {
    def validate(v: Instant): ValidationResult[Instant] = ValidationResult.Valid(v)
  }
  inline given localDateValidator: Validator[LocalDate] with {
    def validate(v: LocalDate): ValidationResult[LocalDate] = ValidationResult.Valid(v)
  }
  inline given localTimeValidator: Validator[LocalTime] with {
    def validate(v: LocalTime): ValidationResult[LocalTime] = ValidationResult.Valid(v)
  }
  inline given localDateTimeValidator: Validator[LocalDateTime] with {
    def validate(v: LocalDateTime): ValidationResult[LocalDateTime] = ValidationResult.Valid(v)
  }
  inline given zonedDateTimeValidator: Validator[ZonedDateTime] with {
    def validate(v: ZonedDateTime): ValidationResult[ZonedDateTime] = ValidationResult.Valid(v)
  }
  inline given durationValidator: Validator[Duration] with {
    def validate(v: Duration): ValidationResult[Duration] = ValidationResult.Valid(v)
  }

  /** Automatically derives a `Validator` for case classes using Scala 3 macros. */
  inline def derive[T](using m: Mirror.ProductOf[T]): Validator[T] =
    ${ deriveImpl[T, m.MirroredElemTypes, m.MirroredElemLabels]('m) }

  /** Macro implementation for deriving a `Validator`. */
  private def deriveImpl[T: Type, Elems <: Tuple: Type, Labels <: Tuple: Type](
    m: Expr[Mirror.ProductOf[T]]
  )(using q: Quotes): Expr[Validator[T]] =
    Derivation.deriveSyncValidatorImpl[T, Elems, Labels](m)
}

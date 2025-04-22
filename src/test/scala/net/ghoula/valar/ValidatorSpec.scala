package net.ghoula.valar

import org.specs2.matcher.{Matchers, ResultMatchers, TraversableMatchers}
import org.specs2.mutable.Specification

import java.time.*
import java.util.UUID
import scala.Symbol
import scala.math.{BigDecimal, BigInt}

object ValidatorSpec extends Specification with Matchers with TraversableMatchers with ResultMatchers {

  def checkValidator[T](value: T)(using validator: Validator[T]): org.specs2.execute.Result = {
    validator must beAnInstanceOf[Validator[T]]
    validator.validate(value) must beEqualTo(ValidationResult.Valid(value))
  }

  "Provided Standard Library Validators" should {

    "validate Int (non-negative)" in {
      val validator = summon[Validator[Int]]
      validator must beAnInstanceOf[Validator[Int]]
      validator.validate(10) must beEqualTo(ValidationResult.Valid(10))
      validator.validate(0) must beEqualTo(ValidationResult.Valid(0))
      validator.validate(-1) must beAnInstanceOf[ValidationResult.Invalid]
    }
    "validate Float (finite)" in {
      val validator = summon[Validator[Float]]
      validator must beAnInstanceOf[Validator[Float]]
      validator.validate(3.14f) must beEqualTo(ValidationResult.Valid(3.14f))
      validator.validate(Float.NaN) must beAnInstanceOf[ValidationResult.Invalid]
    }
    "validate Double (finite)" in {
      val validator = summon[Validator[Double]]
      validator must beAnInstanceOf[Validator[Double]]
      validator.validate(3.14d) must beEqualTo(ValidationResult.Valid(3.14d))
      validator.validate(Double.PositiveInfinity) must beAnInstanceOf[ValidationResult.Invalid]
    }
    "validate String (non-empty)" in {
      val validator = summon[Validator[String]]
      validator must beAnInstanceOf[Validator[String]]
      validator.validate("hello") must beEqualTo(ValidationResult.Valid("hello"))
      validator.validate("") must beAnInstanceOf[ValidationResult.Invalid]
    }

    "validate Option[Int]" in {
      val validator = summon[Validator[Option[Int]]]
      validator must beAnInstanceOf[Validator[Option[Int]]]

      validator.validate(Some(42)) must beEqualTo(ValidationResult.Valid(Some(42)))
      validator.validate(Some(0)) must beEqualTo(ValidationResult.Valid(Some(0)))

      validator.validate(None) must beEqualTo(ValidationResult.Valid(None))

      val invalidInput = Some(-5)
      val result = validator.validate(invalidInput)
      result must beAnInstanceOf[ValidationResult.Invalid]

      result.fold(
        _ => failure("Expected Invalid, got Valid"),
        errors => {
          errors must haveSize(1)
          errors.head.message must contain("Int must be non-negative")
        }
      )
    }

    "validate Boolean (pass-through)" in { checkValidator(true) and checkValidator(false) }

    "validate Byte (pass-through)" in { checkValidator(1.toByte) }

    "validate Short (pass-through)" in { checkValidator(1.toShort) }

    "validate Long (pass-through)" in { checkValidator(1L) }

    "validate Char (pass-through)" in { checkValidator('a') }

    "validate Unit (pass-through)" in { checkValidator(()) }

    "validate BigInt (pass-through)" in { checkValidator(BigInt(123)) }

    "validate BigDecimal (pass-through)" in { checkValidator(BigDecimal(123.45)) }

    "validate Symbol (pass-through)" in { checkValidator(Symbol("abc")) }

    "validate UUID (pass-through)" in {
      checkValidator(UUID.randomUUID())
    }
    "validate Instant (pass-through)" in {
      checkValidator(Instant.now())
    }
    "validate LocalDate (pass-through)" in {
      checkValidator(LocalDate.now())
    }
    "validate LocalTime (pass-through)" in {
      checkValidator(LocalTime.now())
    }
    "validate LocalDateTime (pass-through)" in {
      checkValidator(LocalDateTime.now())
    }
    "validate ZonedDateTime (pass-through)" in {
      checkValidator(ZonedDateTime.now(ZoneId.systemDefault()))
    }
    "validate Duration (pass-through)" in {
      checkValidator(Duration.ofSeconds(10))
    }

  }

}

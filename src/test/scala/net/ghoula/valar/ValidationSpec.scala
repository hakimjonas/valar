package net.ghoula.valar

import net.ghoula.valar.ValidationErrors.{ValidationError, ValidationException}
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.ValidationResult.*
import net.ghoula.valar.Validator.deriveValidatorMacro
import net.ghoula.valar.internal.ErrorAccumulator
import org.specs2.matcher.{Matchers, ResultMatchers, TraversableMatchers}
import org.specs2.mutable.Specification

import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions
import scala.reflect.ClassTag

object ValidationSpec extends Specification with Matchers with TraversableMatchers with ResultMatchers {

  given Validator[String] with {
    def validate(value: String): ValidationResult[String] =
      if (value.nonEmpty) Valid(value)
      else invalid(ValidationError("TestString: must not be empty", expected = Some("non-empty")))
  }

  given Validator[Int] with {
    def validate(value: Int): ValidationResult[Int] =
      if (value >= 0) Valid(value)
      else
        invalid(
          ValidationError("TestInt: must be non-negative", expected = Some(">= 0"), actual = Some(value.toString))
        )
  }

  given [T](using v: Validator[T]): Validator[Option[T]] with {
    def validate(value: Option[T]): ValidationResult[Option[T]] = value match {
      case Some(inner) => v.validate(inner).map(Some(_))
      case None => Valid(None)
    }
  }

  case class User(name: String, age: Option[Int])
  given Validator[User] = deriveValidatorMacro

  case class Address(street: String, city: String, zip: Int)
  given Validator[Address] = deriveValidatorMacro

  case class Company(name: String, address: Address, ceo: Option[User])
  given Validator[Company] = deriveValidatorMacro

  case class NullFieldTest(name: String, age: Int)
  given Validator[NullFieldTest] = deriveValidatorMacro

  "Collection Validators" should {
    val listValidator = summon[Validator[List[Int]]]
    val seqValidator = summon[Validator[Seq[Int]]]
    val vectorValidator = summon[Validator[Vector[Int]]]
    val setValidator = summon[Validator[Set[Int]]]
    val mapValidator = summon[Validator[Map[Int, String]]]
    val arrayValidator = summon[Validator[Array[Int]]]
    val arraySeqValidator = summon[Validator[ArraySeq[Int]]]

    "validate a list of valid integers" in {
      listValidator.validate(List(1, 0, 3)).must(beEqualTo(Valid(List(1, 0, 3))))
    }

    "accumulate errors correctly using listValidator" in {
      listValidator
        .validate(List(1, -2, 3, -4))
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(2)
          errors.map(_.message).must(contain(equalTo("TestInt: must be non-negative")).forall)
          errors.flatMap(_.actual).must(containTheSameElementsAs(List("-2", "-4")))
        })
    }

    "accumulate errors correctly using seqValidator" in {
      seqValidator
        .validate(Seq(1, -5, 7, -8))
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(2)
          errors.flatMap(_.actual).must(containTheSameElementsAs(List("-5", "-8")))
        })
    }

    "accumulate errors correctly using vectorValidator" in {
      vectorValidator
        .validate(Vector(10, -3, 20, -7))
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(2)
          errors.flatMap(_.actual).must(containTheSameElementsAs(List("-3", "-7")))
        })
    }

    "accumulate errors correctly using setValidator" in {
      setValidator
        .validate(Set(5, -10, 15, -20))
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(2)
          errors.flatMap(_.actual).must(containTheSameElementsAs(List("-10", "-20")))
        })
    }

    "validate a valid Map using mapValidator" in {
      mapValidator.validate(Map(1 -> "a", 0 -> "b")).must(beEqualTo(Valid(Map(1 -> "a", 0 -> "b"))))
    }

    "accumulate errors correctly using mapValidator" in {
      mapValidator
        .validate(Map(-1 -> "a", 2 -> "", -3 -> ""))
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(4)
          val keyErrorMessages = errors.filter(_.message.contains("Invalid field: key")).map(_.message)
          keyErrorMessages must haveSize(2)
          keyErrorMessages.forall(_.contains("TestInt: must be non-negative")).must(beTrue)
          val valueErrorMessages = errors.filter(_.message.contains("Invalid field: value")).map(_.message)
          valueErrorMessages must haveSize(2)
          valueErrorMessages.forall(_.contains("TestString: must not be empty")).must(beTrue)
          errors
            .exists(e => e.message.contains("field type: Int") && e.message.contains("Invalid field: key"))
            .must(beTrue)
          errors
            .exists(e => e.message.contains("field type: String") && e.message.contains("Invalid field: value"))
            .must(beTrue)
        })
    }

    "accumulate errors correctly using arrayValidator" in {
      arrayValidator
        .validate(Array(100, -50, 200, -150))
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(2)
          errors.flatMap(_.actual).must(containTheSameElementsAs(List("-50", "-150")))
        })
    }

    "accumulate errors correctly using arraySeqValidator" in {
      arraySeqValidator
        .validate(ArraySeq(10, -20, 30, -40))
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(2)
          errors.flatMap(_.actual).must(containTheSameElementsAs(List("-20", "-40")))
        })
    }
  }

  "Intersection Validator" should {
    trait LongerThan3 { def value: String }
    trait StartsWithA { def value: String }
    case class TestIntersection(value: String) extends LongerThan3 with StartsWithA

    given localLongerThan3Validator: Validator[LongerThan3] with {
      def validate(a: LongerThan3): ValidationResult[LongerThan3] =
        if (a.value.length > 3) Valid(a)
        else
          invalid(ValidationError("Length must be > 3", expected = Some("> 3"), actual = Some(a.value.length.toString)))
    }
    given localStartsWithAValidator: Validator[StartsWithA] with {
      def validate(a: StartsWithA): ValidationResult[StartsWithA] =
        if (a.value.startsWith("A")) Valid(a)
        else invalid(ValidationError("Must start with A", expected = Some("A*"), actual = Some(a.value)))
    }

    given testIntersectionValidator(using
      v1: Validator[LongerThan3],
      v2: Validator[StartsWithA]
    ): Validator[LongerThan3 & StartsWithA] = (ab: LongerThan3 & StartsWithA) => {
      v1.validate(ab).zip(v2.validate(ab)).map(_ => ab)
    }

    "validate an object that satisfies both conditions" in {
      val obj = TestIntersection("Amazing")
      summon[Validator[LongerThan3 & StartsWithA]].validate(obj).must(beEqualTo(Valid(obj)))
    }

    "fail when the object does not satisfy the length requirement" in {
      val obj = TestIntersection("Awe")
      summon[Validator[LongerThan3 & StartsWithA]]
        .validate(obj)
        .must(beLike { case Invalid(errs) =>
          errs must haveSize(1)
          errs.head.message.must(contain("Length must be > 3"))
        })
    }

    "fail when the object does not start with A" in {
      val obj = TestIntersection("Brave")
      summon[Validator[LongerThan3 & StartsWithA]]
        .validate(obj)
        .must(beLike { case Invalid(errs) =>
          errs must haveSize(1)
          errs.head.message.must(contain("Must start with A"))
        })
    }

    "fail and accumulate errors when both conditions fail" in {
      val obj = TestIntersection("Bad")
      summon[Validator[LongerThan3 & StartsWithA]]
        .validate(obj)
        .must(beLike { case Invalid(errs) =>
          errs must haveSize(2)
          errs.map(_.message).must(contain(equalTo("Length must be > 3")))
          errs.map(_.message).must(contain(equalTo("Must start with A")))
        })
    }
  }

  "Basic Helper Functions" should {
    "nonEmpty" >> {
      nonEmpty("hello").must(beEqualTo(Valid("hello")))
      nonEmpty("").must(beLike { case Invalid(errs) => errs.head.message must contain("String must not be empty") })
      nonEmpty("  ").must(beLike { case Invalid(errs) => errs.head.message must contain("String must not be empty") })
    }
    "positiveInt" >> {
      positiveInt(5).must(beEqualTo(Valid(5)))
      positiveInt(0).must(beEqualTo(Valid(0)))
      positiveInt(-1).must(beLike { case Invalid(errs) =>
        errs.head.message must contain("Int must be non-negative")
        errs.head.actual must beSome("-1")
      })
    }
    "finiteFloat" >> {
      finiteFloat(3.14f).must(beEqualTo(Valid(3.14f)))
      finiteFloat(0.0f).must(beEqualTo(Valid(0.0f)))
      finiteFloat(Float.PositiveInfinity).must(beLike { case Invalid(errs) =>
        errs.head.message must contain("Float must be finite")
      })
      finiteFloat(Float.NaN).must(beLike { case Invalid(errs) =>
        errs.head.message must contain("Float must be finite")
      })
    }
    "finiteDouble" >> {
      finiteDouble(3.14d).must(beEqualTo(Valid(3.14d)))
      finiteDouble(0.0d).must(beEqualTo(Valid(0.0d)))
      finiteDouble(Double.NegativeInfinity).must(beLike { case Invalid(errs) =>
        errs.head.message must contain("Double must be finite")
      })
      finiteDouble(Double.NaN).must(beLike { case Invalid(errs) =>
        errs.head.message must contain("Double must be finite")
      })
    }

    "minLengthValidator" >> {
      minLengthValidator("hi", 1)().must(beEqualTo(Valid("hi")))
      minLengthValidator("hi", 2)().must(beEqualTo(Valid("hi")))
      minLengthValidator("hi", 2)().must(beEqualTo(Valid("hi")))
      minLengthValidator("", 1)().must(beLike { case Invalid(errs) =>
        errs.head.message must beEqualTo("Actual length (0) is less than minimum required length of 1")
      })

      minLengthValidator("hi", 3)().must(beLike { case Invalid(errs) =>
        errs must haveSize(1)
        val err = errs.head
        err.message must beEqualTo("Actual length (2) is less than minimum required length of 3")
        err.expected must beSome("length >= 3")
        err.actual must beSome("2")
      })

      @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
      val nullResult = minLengthValidator(null, 3)()
      nullResult must beLike { case Invalid(errs) =>
        errs must haveSize(1)
        val err = errs.head
        err.message must beEqualTo("Actual length (null) is less than minimum required length of 3")
        err.expected must beSome("length >= 3")
        err.actual must beSome("null")
      }
    }

    "maxLengthValidator" >> {
      maxLengthValidator("hi", 3)().must(beEqualTo(Valid("hi")))
      maxLengthValidator("hi", 2)().must(beEqualTo(Valid("hi")))
      maxLengthValidator("", 1)().must(beEqualTo(Valid("")))
      maxLengthValidator("hi", 1)().must(beLike { case Invalid(errs) =>
        errs.head.message must beEqualTo("Length (2) exceeds maximum allowed length of 1")
      })

      maxLengthValidator("hi", 1)().must(beLike { case Invalid(errs) =>
        errs must haveSize(1)
        val err = errs.head
        err.message must beEqualTo("Length (2) exceeds maximum allowed length of 1")
        err.expected must beSome("length <= 1")
        err.actual must beSome("2")
      })

      @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
      val nullResult = maxLengthValidator(null, 3)()
      nullResult must beLike { case Invalid(errs) =>
        errs must haveSize(1)
        val err = errs.head
        err.message must beEqualTo("Input must be a non-null string (actual: null)")
        err.expected must beSome("non-null string with length <= 3")
        err.actual must beSome("null")
      }
    }

    "regexMatch" >> {
      val patternString: String = "[a-z]+[0-9]+"

      regexMatch("abc123", patternString)().must(beEqualTo(Valid("abc123")))

      regexMatch("abc", patternString)().must(beLike { case Invalid(errs) =>
        errs must haveSize(1)
        val err = errs.head
        err.message must beEqualTo(s"Value 'abc' does not match pattern '$patternString'")
        err.expected must beSome(patternString)
        err.actual must beSome("abc")
      })

      val invalidPatternString: String = "([a-z)"
      regexMatch("TestData", invalidPatternString)().must(beLike { case Invalid(errs) =>
        errs must haveSize(1)
        val err = errs.head
        err.message must startWith("Invalid regex pattern:")
        err.message must contain("Unclosed character class near index 5")
        err.expected must beSome("Valid Regex Pattern")
        err.actual must beSome(invalidPatternString)
        err.code must beSome("validation.regex.invalid_pattern")
        err.severity must beSome("Error")
      })

      @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
      val nullResult = regexMatch(null, patternString)()
      nullResult must beLike { case Invalid(errs) =>
        errs must haveSize(1)
        val err = errs.head
        err.message must beEqualTo(s"Value 'null' does not match pattern '$patternString'")
        err.expected must beSome(patternString)
        err.actual must beSome("null")
      }

    }
    "inRange" >> {
      inRange(5, 1, 10)().must(beEqualTo(Valid(5)))
      inRange(1, 1, 10)().must(beEqualTo(Valid(1)))
      inRange(10, 1, 10)().must(beEqualTo(Valid(10)))
      inRange(0, 1, 10)().must(beLike { case Invalid(errs) =>
        errs.head.message must contain("Must be in range [1, 10]")
        errs.head.actual must beSome("0")
      })
    }
    "oneOf" >> {
      oneOf("B", Set("A", "B", "C"))().must(beEqualTo(Valid("B")))
      oneOf("D", Set("A", "B", "C"))().must(beLike { case Invalid(errs) =>
        errs must haveSize(1)
        val msg = errs.head.message
        msg.must(contain("Must be one of"))
        msg.must(contain("A"))
        msg.must(contain("B"))
        msg.must(contain("C"))
      })
    }
    "optional" >> {
      val optStrValidator = summon[Validator[Option[String]]]
      optStrValidator.validate(Some("hello")).must(beEqualTo(Valid(Some("hello"))))
      optStrValidator.validate(None).must(beEqualTo(Valid(None)))
      optStrValidator
        .validate(Some(""))
        .must(beLike { case Invalid(errs) =>
          errs.head.message must contain("TestString: must not be empty")
        })
    }
  }

  "Union Validation" should {
    "validate when value matches the first type (String)" in {
      validateUnion[String, Int]("hello").must(beEqualTo(Valid("hello")))
    }
    "validate when value matches the second type (Int)" in {
      validateUnion[String, Int](42).must(beEqualTo(Valid(42)))
    }
    "fail with a nested error when value matches neither type" in {
      val inputValue: Any = true
      validateUnion[String, Int](inputValue)
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(1)
          val topError = errors.head
          topError.message
            .must(contain("Value failed validation for all expected types: String | int"))
          topError.expected.must(beSome("String | int"))
          topError.actual.must(beSome("true"))
          val children = topError.children
          children must haveSize(2)
          children
            .map(_.message)
            .must(
              contain(
                allOf(
                  equalTo("Value is not of type String"),
                  equalTo("Value is not of type int")
                )
              )
            )
        })
    }
  }

  "Error Accumulation" should {
    "accumulate multiple errors when zipping two invalid results (default order)" in {
      val invalid1 = invalid(ValidationError("error1"))
      val invalid2 = invalid(ValidationError("error2"))
      invalid1
        .zip(invalid2)
        .must(
          beEqualTo(
            Invalid(Vector(ValidationError("error1"), ValidationError("error2")))
          )
        )
    }
  }

  "Custom Error Accumulation" should {
    given customAccumulator: ErrorAccumulator[Vector[ValidationError]] with {
      def combine(e1: Vector[ValidationError], e2: Vector[ValidationError]): Vector[ValidationError] =
        e2 ++ e1
    }

    "combine errors using the custom accumulator explicitly with orElse" in {
      val invalid1 = invalid(ValidationError("error1"))
      val invalid2 = invalid(ValidationError("error2"))
      invalid1
        .orElse(invalid2)
        .must(
          beEqualTo(
            Invalid(
              Vector(ValidationError("error2"), ValidationError("error1"))
            ) // Reversed order due to customAccumulator
          )
        )
    }

    "accumulate errors on zip (both) with reversed order" in {
      invalid[String](ValidationError("e1"))
        .zip(invalid[Int](ValidationError("e2")))
        .must(beEqualTo(Invalid(Vector(ValidationError("e2"), ValidationError("e1")))))
    }

    "accumulate errors on or (both invalid) with reversed order" in {
      invalid[String](ValidationError("e1"))
        .or(invalid[Int](ValidationError("e2")))
        .must(beEqualTo(Invalid(Vector(ValidationError("e2"), ValidationError("e1")))))
    }

    "accumulate errors on orElse (both invalid) with reversed order" in {
      invalid[Int](ValidationError("e1"))
        .orElse(invalid[Int](ValidationError("e2")))
        .must(beEqualTo(Invalid(Vector(ValidationError("e2"), ValidationError("e1")))))
    }

    "support mapN with reversed order for errors" in {
      val vA = Valid(10)
      val vT = Valid(("a", true))

      def combine(h: Int, s: String, b: Boolean): String = s"$h-$s-$b"

      vA.mapN(vT)(combine).must(beEqualTo(Valid("10-a-true")))
      val invA = invalid[Int](ValidationError("errA"))
      val invT = invalid[(String, Boolean)](ValidationError("errT"))
      invA.mapN(vT)(combine).must(beEqualTo(Invalid(Vector(ValidationError("errA")))))
      vA.mapN(invT)(combine).must(beEqualTo(Invalid(Vector(ValidationError("errT")))))
      invA
        .mapN(invT)(combine)
        .must(beEqualTo(Invalid(Vector(ValidationError("errT"), ValidationError("errA")))))
    }

  }

  "ValidationResult Extension Methods" should {
    "support map" in { Valid(10).map(_ * 2).must(beEqualTo(Valid(20))) }
    "propagate errors on map" in {
      invalid[Int](ValidationError("e")).map(_ + 1).must(beEqualTo(invalid[Int](ValidationError("e"))))
    }
    "support flatMap" in { Valid(10).flatMap(x => Valid(x * 2)).must(beEqualTo(Valid(20))) }
    "propagate errors on flatMap" in {
      invalid[Int](ValidationError("e"))
        .flatMap(x => Valid(x + 1))
        .must(beEqualTo(invalid[Int](ValidationError("e"))))
    }
    "short-circuit flatMap on first error" in {
      Valid(10)
        .flatMap(_ => invalid(ValidationError("e")))
        .must(beEqualTo(invalid[Int](ValidationError("e"))))
    }
    "support zip" in { Valid("a").zip(Valid(1)).must(beEqualTo(Valid(("a", 1)))) }
    "propagate errors on zip (left)" in {
      invalid[String](ValidationError("e")).zip(Valid(1)).must(beEqualTo(invalid(ValidationError("e"))))
    }
    "propagate errors on zip (right)" in {
      Valid("a").zip(invalid[Int](ValidationError("e"))).must(beEqualTo(invalid(ValidationError("e"))))
    }
    "support or" in { Valid("a").or(Valid(1)).must(beEqualTo(Valid("a"))) }
    "support or (right valid)" in {
      invalid[String](ValidationError("e")).or(Valid(1)).must(beEqualTo(Valid(1)))
    }

    "support orElse" in { Valid(10).orElse(Valid(20)).must(beEqualTo(Valid(10))) }
    "support orElse (left invalid)" in {
      invalid[Int](ValidationError("e")).orElse(Valid(20)).must(beEqualTo(Valid(20)))
    }

    "support recover" in { Valid(10).recover(0).must(beEqualTo(Valid(10))) }
    "recover from error" in { invalid[Int](ValidationError("e")).recover(0).must(beEqualTo(Valid(0))) }
    "support fold" in {
      Valid(100).fold((a: Int) => a + 1, _ => 0).must(beEqualTo(101))
      invalid[Int](ValidationError("fail"))
        .fold((a: Int) => a + 1, errs => errs.size * 10)
        .must(beEqualTo(10))
    }
    "support toTry" in {
      Valid("ok").toTry.must(beSuccessfulTry("ok"))
      invalid[String](ValidationError("fail")).toTry.must(beFailedTry.like { case e: ValidationException =>
        e.getMessage must contain("fail")
      })
    }
    "support toEither" in {
      Valid(42).toEither.must(beRight(42))
      invalid[Int](ValidationError("err")).toEither.must(beLeft.like { case errs =>
        errs.head.message must contain("err")
      })
    }
    "support toOption" in {
      Valid(42).toOption.must(beSome(42))
      invalid[Int](ValidationError("err")).toOption.must(beNone)
    }
    "support toList" in {
      Valid(42).toList.must(equalTo(List(42)))
      invalid[Int](ValidationError("err")).toList.must(beEmpty)
    }
    "support toVector" in {
      Valid(42).toVector.must(equalTo(Vector(42)))
      invalid[Int](ValidationError("err")).toVector.must(beEmpty)
    }
  }

  "Macro Derivation (deriveValidatorMacro)" should {
    "validate a simple case class (User) successfully" in {
      deriveValidatorMacro[User].validate(User("John", Some(25))).must(beEqualTo(Valid(User("John", Some(25)))))
    }

    "accumulate errors in a simple case class (User)" in {
      deriveValidatorMacro[User]
        .validate(User("", Some(-10)))
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(2)

          val errorMessages = errors.map(_.message)

          errorMessages
            .exists(msg => msg.contains("Invalid field: name") && msg.contains("TestString: must not be empty"))
            .must(beTrue)

          errorMessages
            .exists(msg => msg.contains("Invalid field: age") && msg.contains("TestInt: must be non-negative"))
            .must(beTrue)

          true
        })
    }

    "validate a nested case class (Company) successfully" in {
      val company = Company("Acme Corp", Address("123 Main St", "Springfield", 12345), Some(User("Alice", Some(30))))
      summon[Validator[Company]].validate(company).must(beEqualTo(Valid(company)))
    }

    "accumulate errors correctly in a nested case class (Company)" in {

      val company = Company(
        name = "BadCo",
        address = Address(street = "", city = "Springfield", zip = -1),
        ceo = Some(User(name = "", age = Some(25)))
      )

      val result = summon[Validator[Company]].validate(company)

      val expectedStreetErrorPath = List("address", "street")
      val expectedStreetErrorMsg =
        "Invalid field: address, field type: Address: Invalid field: street, field type: String: TestString: must not be empty"
      val expectedStreetErrorExpected = Some("non-empty")
      val expectedStreetErrorActual = None

      val expectedZipErrorPath = List("address", "zip")
      val expectedZipErrorMsg =
        "Invalid field: address, field type: Address: Invalid field: zip, field type: Int: TestInt: must be non-negative"
      val expectedZipErrorExpected = Some(">= 0")
      val expectedZipErrorActual = Some("-1")

      val expectedCeoNameErrorPath = List("ceo", "name")
      val expectedCeoNameErrorMsg =
        "Invalid field: ceo, field type: Option: Invalid field: name, field type: String: TestString: must not be empty"
      val expectedCeoNameErrorExpected = Some("non-empty")
      val expectedCeoNameErrorActual = None

      result must beLike { case ValidationResult.Invalid(errors) =>
        errors must haveSize(3)
        val streetErrorOpt = errors.find(_.fieldPath == expectedStreetErrorPath)
        streetErrorOpt must beSome.like { case err =>
          err.message must beEqualTo(expectedStreetErrorMsg)
          err.expected must beEqualTo(expectedStreetErrorExpected)
          err.actual must beEqualTo(expectedStreetErrorActual)
          err.code must beNone
          err.severity must beNone
          err.children must beEmpty
        }

        val zipErrorOpt = errors.find(_.fieldPath == expectedZipErrorPath)
        zipErrorOpt must beSome.like { case err =>
          err.message must beEqualTo(expectedZipErrorMsg)
          err.expected must beEqualTo(expectedZipErrorExpected)
          err.actual must beEqualTo(expectedZipErrorActual)
          err.code must beNone
          err.severity must beNone
          err.children must beEmpty
        }

        val ceoNameErrorOpt = errors.find(_.fieldPath == expectedCeoNameErrorPath)
        ceoNameErrorOpt must beSome.like { case err =>
          err.message must beEqualTo(expectedCeoNameErrorMsg)
          err.expected must beEqualTo(expectedCeoNameErrorExpected)
          err.actual must beEqualTo(expectedCeoNameErrorActual)
          err.code must beNone
          err.severity must beNone
          err.children must beEmpty
        }

        ok
      }
    }

    "fail validation if a non-optional case class field is null" in {
      @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
      val instanceWithNull = NullFieldTest(name = null, age = 30)

      summon[Validator[NullFieldTest]]
        .validate(instanceWithNull)
        .must(beLike { case Invalid(errors) =>
          errors must haveSize(1)
          val err = errors.head
          err.message.must(beEqualTo("Field 'name' must not be null."))
          err.fieldPath.must(beEqualTo(List("name")))
          err.expected.must(beSome("non-null value"))
          err.actual.must(beSome("null"))

        })
    }
  }

  "Fail-Fast Operations" should {
    val v1 = Valid(1)
    val v2 = Valid("A")
    val e1: ValidationResult[Int] = invalid(ValidationError("E1"))
    val e2: ValidationResult[String] = invalid(
      Vector(
        ValidationError("E2a"),
        ValidationError("E2b")
      )
    )
    invalid(ValidationError("E3"))
    "zipFailFast with (Valid, Valid) should return Valid" in {
      v1.zipFailFast(v2).must(beEqualTo(Valid((1, "A"))))
    }

    "zipFailFast with (Invalid, Valid) should return Invalid(left errors)" in {
      e1.zipFailFast(v2).must(beEqualTo(e1))
    }

    "zipFailFast with (Valid, Invalid) should return Invalid(right errors)" in {
      v1.zipFailFast(e2).must(beEqualTo(e2))
    }

    "zipFailFast with (Invalid, Invalid) should return Invalid(left errors only)" in {
      e1.zipFailFast(e2).must(beEqualTo(e1))
    }

    val vt = Valid(("X", true))
    val invT = invalid[(String, Boolean)](ValidationError("errT"))

    def combine(i: Int, s: String, b: Boolean): String = s"$i-$s-$b"

    "mapNFailFast with (Valid, Valid) should return Valid" in {
      v1.mapNFailFast(vt)(combine).must(beEqualTo(Valid("1-X-true")))
    }

    "mapNFailFast with (Invalid, Valid) should return Invalid(left errors)" in {
      e1.mapNFailFast(vt)(combine).must(beEqualTo(e1))
    }

    "mapNFailFast with (Valid, Invalid) should return Invalid(right errors)" in {
      v1.mapNFailFast(invT)(combine).must(beEqualTo(invT))
    }

    "mapNFailFast with (Invalid, Invalid) should return Invalid(left errors only)" in {
      e1.mapNFailFast(invT)(combine).must(beEqualTo(e1))
    }

  }

  "required" >> {
    required(Some("hello")).must(beEqualTo(Valid("hello")))

    required(None: Option[String]).must(beLike { case Invalid(errs) =>
      errs must haveSize(1)
      errs.head.message must beEqualTo("Required value must not be empty/null")
      errs.head.expected must beSome("defined Option (Some)")
      errs.head.actual must beSome("None")
    })

    val customMsg = "This optional value is mandatory!"
    required(None: Option[Int], errorMessage = customMsg).must(beLike { case Invalid(errs) =>
      errs must haveSize(1)
      errs.head.message must beEqualTo(customMsg)
      errs.head.expected must beSome("defined Option (Some)")
      errs.head.actual must beSome("None")
    })
  }

  "optionValidator" >> {
    val validateString: String => ValidationResult[String] = nonEmpty(_, _ => "Inner validation failed")

    @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
    def nullTests(): Unit = {
      optionValidator(null: String, validateString).must(beEqualTo(Valid(None)))
      optionValidator(null: String, validateString).must(beEqualTo(Valid(None)))

      val nullErrorMsg = "Explicit null is not allowed here"
      optionValidator(null: String, validateString, errorOnEmpty = true)
        .must(beLike { case Invalid(errs) =>
          errs must haveSize(1)
          errs.head.message must beEqualTo("Value must not be empty/null")
        })
      optionValidator(null: String, validateString, errorOnEmpty = true, emptyErrorMsg = nullErrorMsg)
        .must(beLike { case Invalid(errs) =>
          errs must haveSize(1)
          errs.head.message must beEqualTo(nullErrorMsg)
        })
    }

    nullTests()

    optionValidator("hello", validateString).must(beEqualTo(Valid(Some("hello"))))

    optionValidator("", validateString).must(beLike { case Invalid(errs) =>
      errs must haveSize(1)
      errs.head.message must beEqualTo("Inner validation failed")
    })
  }

}

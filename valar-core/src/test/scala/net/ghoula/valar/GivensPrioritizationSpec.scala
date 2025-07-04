//package net.ghoula.valar
//
//import org.specs2.matcher.{Matchers, ResultMatchers, TraversableMatchers}
//import org.specs2.mutable.Specification
//
//import scala.language.implicitConversions
//
///** Test to verify compatibility with Scala 3.7's new givens prioritization rules. */
//object GivensPrioritizationSpec extends Specification with Matchers with TraversableMatchers with ResultMatchers {
//
//  "Given prioritization in validators" should {
//
//    "demonstrate advanced prioritization with using clauses" in {
//      type Email = String
//
//      given stringValidator: Validator[String] with {
//        def validate(s: String): ValidationResult[String] =
//          ValidationResult.Valid("string: " + s)
//      }
//
//      given emailValidator: Validator[Email] with {
//        def validate(e: Email): ValidationResult[Email] =
//          ValidationResult.Valid("email: " + e)
//      }
//
//      /** Helper functions that use explicit using clauses to control validator selection. */
//      def validateWithStringValidator(s: String)(using v: Validator[String]): ValidationResult[String] =
//        v.validate(s)
//
//      def validateWithEmailValidator(e: Email)(using v: Validator[Email]): ValidationResult[Email] =
//        v.validate(e)
//
//      val email: Email = "user@example.com"
//
//      val stringResult = validateWithStringValidator(email)(using stringValidator)
//      val emailResult = validateWithEmailValidator(email)(using emailValidator)
//
//      stringResult.must(beLike { case ValidationResult.Valid(value) =>
//        value must beEqualTo("string: user@example.com")
//      })
//
//      emailResult.must(beLike { case ValidationResult.Valid(value) =>
//        value must beEqualTo("email: user@example.com")
//      })
//    }
//
//    "demonstrate solution for ambiguous givens issue" in {
//      type UserName = String
//
//      /** Namespace containing validators to avoid ambiguity issues with Scala 3.7's givens
//        * prioritization.
//        */
//      object validators {
//        given generalValidator: Validator[String] with {
//          def validate(value: String): ValidationResult[String] =
//            ValidationResult.Valid("from general validator: " + value)
//        }
//
//        given userNameValidator: Validator[UserName] with {
//          def validate(value: UserName): ValidationResult[UserName] =
//            ValidationResult.Valid("from specific validator: " + value)
//        }
//      }
//
//      {
//        import validators.userNameValidator
//
//        val userName: UserName = "test"
//        val result = summon[Validator[UserName]].validate(userName)
//
//        result.must(beLike { case ValidationResult.Valid(value) =>
//          value must beEqualTo("from specific validator: test")
//        })
//      }
//
//      {
//        val userName: UserName = "test"
//        val result = validators.userNameValidator.validate(userName)
//
//        result.must(beLike { case ValidationResult.Valid(value) =>
//          value must beEqualTo("from specific validator: test")
//        })
//      }
//
//      {
//        import validators.generalValidator
//
//        val userName: UserName = "test"
//        val result = summon[Validator[String]].validate(userName)
//
//        result.must(beLike { case ValidationResult.Valid(value) =>
//          value must beEqualTo("from general validator: test")
//        })
//      }
//    }
//
//    "handle inheritance hierarchies correctly" in {
//
//      /** Base trait for animal hierarchy. */
//      trait Animal
//
//      /** Dog implementation with name validation. */
//      case class Dog(name: String) extends Animal
//
//      /** Cat implementation with lives validation. */
//      case class Cat(lives: Int) extends Animal
//
//      given animalValidator: Validator[Animal] with {
//        def validate(value: Animal): ValidationResult[Animal] =
//          ValidationResult.Valid(value)
//      }
//
//      given dogValidator: Validator[Dog] with {
//        def validate(value: Dog): ValidationResult[Dog] =
//          if (value.name.nonEmpty) ValidationResult.Valid(value)
//          else ValidationResult.invalid(ValidationErrors.ValidationError("Dog name cannot be empty"))
//      }
//
//      given catValidator: Validator[Cat] with {
//        def validate(value: Cat): ValidationResult[Cat] =
//          if (value.lives > 0) ValidationResult.Valid(value)
//          else ValidationResult.invalid(ValidationErrors.ValidationError("Cat must have at least one life"))
//      }
//
//      val dog = Dog("Rex")
//      val cat = Cat(9)
//
//      summon[Validator[Dog]].validate(dog) must beEqualTo(ValidationResult.Valid(dog))
//      summon[Validator[Cat]].validate(cat) must beEqualTo(ValidationResult.Valid(cat))
//
//      val dogAsAnimal: Animal = dog
//      val catAsAnimal: Animal = cat
//      summon[Validator[Animal]].validate(dogAsAnimal) must beEqualTo(ValidationResult.Valid(dogAsAnimal))
//      summon[Validator[Animal]].validate(catAsAnimal) must beEqualTo(ValidationResult.Valid(catAsAnimal))
//
//      val emptyNameDog = Dog("")
//      val noLivesCat = Cat(0)
//
//      summon[Validator[Dog]].validate(emptyNameDog) must beLike { case ValidationResult.Invalid(errors) =>
//        errors.head.message must contain("Dog name cannot be empty")
//      }
//
//      summon[Validator[Cat]].validate(noLivesCat) must beLike { case ValidationResult.Invalid(errors) =>
//        errors.head.message must contain("Cat must have at least one life")
//      }
//    }
//  }
//}

package net.ghoula.valar

import munit.FunSuite

class GivensPrioritizationSpec extends FunSuite {

  test("Given prioritization should allow advanced control with 'using' clauses") {
    type Email = String

    given stringValidator: Validator[String] with {
      def validate(s: String): ValidationResult[String] =
        ValidationResult.Valid("string: " + s)
    }

    given emailValidator: Validator[Email] with {
      def validate(e: Email): ValidationResult[Email] =
        ValidationResult.Valid("email: " + e)
    }

    def validateWithStringValidator(s: String)(using v: Validator[String]): ValidationResult[String] =
      v.validate(s)

    def validateWithEmailValidator(e: Email)(using v: Validator[Email]): ValidationResult[Email] =
      v.validate(e)

    val email: Email = "user@example.com"

    validateWithStringValidator(email)(using stringValidator) match {
      case ValidationResult.Valid(value) => assertEquals(value, "string: user@example.com")
      case _ => fail("Expected Valid")
    }

    validateWithEmailValidator(email)(using emailValidator) match {
      case ValidationResult.Valid(value) => assertEquals(value, "email: user@example.com")
      case _ => fail("Expected Valid")
    }
  }

  test("Given prioritization should handle potentially ambiguous givens via scoping") {
    type UserName = String

    object validators {
      given generalValidator: Validator[String] with {
        def validate(value: String): ValidationResult[String] =
          ValidationResult.Valid("from general validator: " + value)
      }
      given userNameValidator: Validator[UserName] with {
        def validate(value: UserName): ValidationResult[UserName] =
          ValidationResult.Valid("from specific validator: " + value)
      }
    }

    // Test with specific validator in scope
    {
      import validators.userNameValidator
      val userName: UserName = "test"
      val result = summon[Validator[UserName]].validate(userName)
      result match {
        case ValidationResult.Valid(value) => assertEquals(value, "from specific validator: test")
        case _ => fail("Expected Valid")
      }
    }

    // Test with general validator in scope
    {
      import validators.generalValidator
      val userName: UserName = "test"
      val result = summon[Validator[String]].validate(userName)
      result match {
        case ValidationResult.Valid(value) => assertEquals(value, "from general validator: test")
        case _ => fail("Expected Valid")
      }
    }
  }

  test("Given prioritization should handle inheritance hierarchies correctly") {
    trait Animal
    case class Dog(name: String) extends Animal
    case class Cat(lives: Int) extends Animal

    given animalValidator: Validator[Animal] with {
      def validate(value: Animal): ValidationResult[Animal] = ValidationResult.Valid(value)
    }

    given dogValidator: Validator[Dog] with {
      def validate(value: Dog): ValidationResult[Dog] =
        if (value.name.nonEmpty) ValidationResult.Valid(value)
        else ValidationResult.invalid(ValidationErrors.ValidationError("Dog name cannot be empty"))
    }

    given catValidator: Validator[Cat] with {
      def validate(value: Cat): ValidationResult[Cat] =
        if (value.lives > 0) ValidationResult.Valid(value)
        else ValidationResult.invalid(ValidationErrors.ValidationError("Cat must have at least one life"))
    }

    // Test success cases for specific types
    assertEquals(summon[Validator[Dog]].validate(Dog("Rex")), ValidationResult.Valid(Dog("Rex")))
    assertEquals(summon[Validator[Cat]].validate(Cat(9)), ValidationResult.Valid(Cat(9)))

    // Test that the more general Animal validator is also found and used
    val dogAsAnimal: Animal = Dog("Rex")
    assertEquals(summon[Validator[Animal]].validate(dogAsAnimal), ValidationResult.Valid(dogAsAnimal))

    val catAsAnimal: Animal = Cat(9)
    assertEquals(summon[Validator[Animal]].validate(catAsAnimal), ValidationResult.Valid(catAsAnimal))

    // Test failure cases for specific types
    summon[Validator[Dog]].validate(Dog("")) match {
      case ValidationResult.Invalid(errors) =>
        assert(errors.head.message.contains("Dog name cannot be empty"))
      case _ => fail("Expected Invalid")
    }

    summon[Validator[Cat]].validate(Cat(0)) match {
      case ValidationResult.Invalid(errors) =>
        assert(errors.head.message.contains("Cat must have at least one life"))
      case _ => fail("Expected Invalid")
    }
  }

}

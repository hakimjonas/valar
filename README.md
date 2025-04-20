# valar – Type-Safe Validation for Scala 3

Valar is a validation library for Scala 3 designed for clarity and ease of use. It leverages Scala 3's type system and
metaprogramming (macros) to help you define complex validation rules with less boilerplate, while providing structured,
detailed error messages useful for debugging or user feedback.

## Key Features:

* **Type Safety:** Clearly distinguish between valid results and accumulated errors at compile time using
  `ValidationResult[A]`. Eliminate runtime errors caused by unexpected validation states.
* **Minimal Boilerplate:** Derive `Validator` instances automatically for case classes using compile-time macros,
  significantly reducing repetitive validation logic. Focus on your rules, not the wiring.
* **Flexible Error Handling:** Choose the strategy that fits your use case:
    * **Error Accumulation** (default): Collect all validation failures, ideal for reporting multiple issues (e.g., in
      UIs or API responses).
    * **Fail-Fast**: Stop validation immediately on the first failure, suitable for performance-sensitive pipelines.
* **Actionable Error Reports:** Generate detailed `ValidationError` objects containing precise field paths, validation
  rule specifics (like expected vs. actual values), and optional codes/severity, making debugging and error reporting
  straightforward.
* **Scala 3 Idiomatic:** Built specifically for Scala 3, embracing features like extension methods, given instances,
  opaque types, and macros for a modern, expressive API.

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "net.ghoula" %% "valar" % "0.1.9"
```

## Basic Usage Example

Here's a basic example illustrating how to use Valar for validating a simple case class:

```scala
import net.ghoula.valar.{Validator, ValidationResult}
import net.ghoula.valar.ValidationResult.{Valid, Invalid}
import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.Validator.deriveValidatorMacro

case class User(name: String, age: Option[Int])

given Validator[String] with {
  def validate(value: String): ValidationResult[String] =
    nonEmpty(value, _ => "Name must not be empty")
}

given Validator[Int] with {
  def validate(value: Int): ValidationResult[Int] =
    positiveInt(value, i => s"Age must be non-negative, got $i")
}

// Automatically derive Validator for User
given Validator[User] = deriveValidatorMacro

val user = User("", Some(-10))

val result = Validator[User].validate(user)

result match {
  case Valid(validUser) => println(s"Valid user: $validUser")
  case Invalid(errors) =>
    println("Validation Failed:")
    println(errors.map(_.prettyPrint(indent = 2)).mkString("\n"))
}
```

**Output**:

```
Validation Failed:
  name: Name must not be empty (expected: non-empty string, got: "")
  age: Age must be non-negative, got -10 (expected: >= 0, got: -10)
```

## Defining Custom Validators

Valar allows you to define custom validators easily by implementing the `Validator` typeclass. Here's an example
demonstrating how you might define a validator for an `Age` type:

```scala
import net.ghoula.valar.*
import net.ghoula.valar.ValidationHelpers.*

opaque type Age = Int

object Age

:
def apply(value: Int): ValidationResult[Age] =
  positiveInt(value, i => s"Age must be non-negative, got $i").map(_.asInstanceOf[Age])

given Validator[Age] with

def validate(value: Age): ValidationResult[Age] = Age(value)

// Usage Examples:

val validAge = Age(25) // Valid
val invalidAge = Age(-3) // Invalid

validAge match
case ValidationResult.Valid(age)
=> println(s"Valid age: $age")
case ValidationResult.Invalid(errors)
=> println(errors.head.prettyPrint())

invalidAge match
case ValidationResult.Valid(age)
=> println(s"Valid age: $age")
case ValidationResult.Invalid(errors)
=> println(errors.head.prettyPrint())
```

**Output:**

```
Valid age: 25
Age must be non-negative, got -3 (expected: >= 0, got: -3)
```

## Core Components

### ValidationResult

Represents the outcome of validation as either `Valid(value)` or `Invalid(errors)`:

```scala
enum ValidationResult[+A]:
  case Valid(value: A)
  case Invalid(errors: Vector[ValidationError])
```

### ValidationError

Opaque type providing rich context for validation errors, including:

- **message**: Human-readable description of the error.
- **fieldPath**: Path to the field causing the error (e.g., `user.address.street`).
- **code**: Optional application-specific error codes.
- **severity**: Optional severity indicator (`Error`, `Warning`).
- **expected/actual**: Information about expected and actual values.

### Validator[A]

A typeclass defining validation logic for a given type:

```scala
trait Validator[A]:
  def validate(a: A): ValidationResult[A]
```

Validators can be automatically derived for case classes:

```scala
given Validator[User] = deriveValidatorMacro
```

### Helper Methods

Common validations like non-empty strings, numeric ranges, regex patterns, and more:

```scala
nonEmpty("value")
positiveInt(5)
regexMatch("abc123", "[a-z]+[0-9]+")()
```

## License

Valar is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.


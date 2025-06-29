# Valar – Type-Safe Validation for Scala 3

[![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar_3?style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar_3)
[![Scala CI and GitHub Release](https://github.com/hakimjonas/valar/actions/workflows/scala.yml/badge.svg)](https://github.com/hakimjonas/valar/actions/workflows/scala.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

Valar is a validation library for Scala 3 designed for clarity and ease of use. It leverages Scala 3's type system and
metaprogramming (macros) to help you define complex validation rules with less boilerplate, while providing structured,
detailed error messages useful for debugging or user feedback.

## Key Features:

* **Type Safety:** Clearly distinguish between valid results and accumulated errors at compile time using
  `ValidationResult[A]`. Eliminate runtime errors caused by unexpected validation states.
* **Named Tuple Support:** 🆕 Field-aware error messages for Scala 3.7's named tuples, with preserved backward compatibility.
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
libraryDependencies += "net.ghoula" %% "valar" % "0.3.0"
````

## Basic Usage Example

Here's a basic example illustrating how to use Valar for validating a simple case class:

*Note: Valar provides default validators for `String` (non-empty) and `Int` (non-negative). This example explicitly defines `given` instances to demonstrate how you can define custom logic or override the defaults.*

```scala mdoc
import net.ghoula.valar.{ValidationResult, Validator}
import net.ghoula.valar.ValidationResult.{Invalid, Valid}
import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.Validator.deriveValidatorMacro

/**
 * This code demonstrates how to use the Valar library for validation in Scala.
 * It includes a custom validator for a case class `User` with the defined fields `name` and `age`.
 * The code also shows how to validate an instance of `User` and handle the validation result.
 *
 * Key components:
 * - The `Validator` trait is used to define custom validation logic for different types.
 * - The `ValidationResult` class represents the outcome of the validation process.
 * - The `deriveValidatorMacro` automatically derives a validator for the `User` case class.
 * - The `ValidationHelpers` object provides helper functions for common validation tasks.
 * - The `ValidationErrors` object defines error messages and codes for validation failures.
 */
case class User(name: String, age: Option[Int])

// Define a validator for String using the Validator for String
given Validator[String] with {
  def validate(value: String): ValidationResult[String] =
    nonEmpty(value, _ => "Name must not be empty")
}

// Define a validator for Option[Int] using the Validator for Int
given Validator[Int] with {
  def validate(value: Int): ValidationResult[Int] =
    positiveInt(value, i => s"Age must be non-negative, got $i")
}

// Automatically derive Validator for User using givens above
given Validator[User] = deriveValidatorMacro

// Create an instance of User with invalid age
val user = User("", Some(-10))

// Validate the user instance
val result: ValidationResult[User] = Validator[User].validate(user)

// Handle the validation result
result match {
  case Valid(validUser) => println(s"Valid user: $validUser")
  case Invalid(errors) =>
    println("Validation Failed:")
    println(errors.map(_.prettyPrint(indent = 2)).mkString("\n"))
}
```

## Named Tuples Support 🆕

Valar 0.3.0 adds support for Scala 3.7's named tuples, providing improved error messages that include actual field names:

```scala mdoc
import net.ghoula.valar.*

// Define a named tuple type with meaningful field names
type PersonTuple = (name: String, age: Int, email: String)

// Automatic derivation works seamlessly with named tuples
given Validator[PersonTuple] = Validator.deriveValidatorMacro

val invalidPerson: PersonTuple = (name = "", age = -10, email = "bad-email")
val tupleResult = summon[Validator[PersonTuple]].validate(invalidPerson)

// Error messages now include actual field names for enhanced debugging!
tupleResult match {
  case ValidationResult.Valid(validPerson) =>
    println(s"Valid person: $validPerson")
  case ValidationResult.Invalid(errors) =>
    errors.foreach { error =>
      println(s"Field: ${error.fieldPath.mkString(".")}")
      println(s"Error: ${error.message}")
    }
    // Output includes meaningful field names:
    // Field: name
    // Error: String must not be empty
    // Field: age  
    // Error: Int must be non-negative
    // Field: email
    // Error: String must not be empty
}
```

**Key Benefits:**
- **Enhanced debugging** - Error messages include meaningful field names
- **Zero runtime overhead** - Field names are extracted at compile time
- **Future-proof** - Compatible with the upcoming Scala 3 LTS version

```scala
// Regular tuples continue to work as before
val regularTuple: (String, Int) = ("", -5)
val validator = summon[Validator[(String, Int)]]
validator.validate(regularTuple) // Works but shows generic positions in errors
```

## Defining Custom Validators

Valar allows you to define custom validators easily by implementing the `Validator` typeclass. Here's an example demonstrating how you might define a validator for an `Age` type:

```scala mdoc
import net.ghoula.valar.*
import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationHelpers.*
import net.ghoula.valar.ValidationResult.{Invalid, Valid}
import scala.util.Either

// Example using ValidationHelpers directly
val emailRegex = "[^@]+@[^@]+\\.[^@]+".r
val emailValidationResult = regexMatch("user@example.com", emailRegex)(email => s"Invalid email format: $email")
println(s"Email validation result: $emailValidationResult")

// Create a module to encapsulate an opaque type with validation
object MyAgeModule {

  // Define an opaque type to enforce validation at compile time
  opaque
  type Age = Int

  object Age {
    /**
     * Validates an age value returning Either for integration with other APIs
     *
     * @param value The integer to validate as an age
     * @return Either a valid Age or ValidationError
     */
    def validateEither(value: Int): Either[ValidationError, Age] = {
      Either.cond(
        0 <= value && value <= 130,
        value: Age,
        ValidationError("❌ Must be between 0 and 130.")
      )
    }

    /**
     * Primary constructor that performs validation and returns ValidationResult
     *
     * @param value The integer to validate as an age
     * @return ValidationResult containing either valid Age or validation errors
     */
    def apply(value: Int): ValidationResult[Age] = {
      validateEither(value) match {
        case Right(age) => Valid(age)
        case Left(err) => Invalid(Vector(err))
      }
    }

    /**
     * Unwraps the opaque type to access the underlying Int
     *
     * @param a The Age instance to unwrap
     * @return The underlying Int value
     */
    def unwrap(a: Age): Int = a

    // Define a Validator instance for Age to integrate with Valar's validation system
    given ageValidator: Validator[Age] with {
      def validate(value: Age): ValidationResult[Age] = Age.apply (value)
    }
  }
}

// Usage example showing how to work with the validated type

import MyAgeModule.Age

println("--- Testing Validated Age Type ---")

// Validate using Either-based API
val validAgeResult = Age.validateEither(25)
val invalidAgeResult = Age.validateEither(-3)

// Handle validation results
validAgeResult match {
  case Right(age) => println(s"Valid age: $age")
  case Left(e) => println(s"Invalid creation: ${e.prettyPrint()}")
}

invalidAgeResult match {
  case Right(age) => println(s"Valid age: $age")
  case Left(e) => println(s"Invalid creation: ${e.prettyPrint()}")
}

```

## Core Components

### ValidationResult

Represents the outcome of validation as either `Valid(value)` or `Invalid(errors)`:

```scala
import net.ghoula.valar.ValidationErrors.ValidationError

enum ValidationResult[+A] {
  case Valid(value: A)
  case Invalid(errors: Vector[ValidationError])
}
```

### ValidationError

Opaque type providing rich context for validation errors, including:

- **message**: Human-readable description of the error.
- **fieldPath**: Path to the field causing the error (e.g., `user.address.street`).
- **code**: Optional application-specific error codes.
- **severity**: Optional severity indicator (`Error`, `Warning`).
- **expected/actual**: Information about expected and actual values.
- **children**: Nested errors for structured reporting.

### Validator[A]

A typeclass defining validation logic for a given type:

```scala
import net.ghoula.valar.ValidationResult

trait Validator[A] {
  def validate(a: A): ValidationResult[A]
}
```

Validators can be automatically derived for case classes using `deriveValidatorMacro`:

```scala mdoc
import net.ghoula.valar.Validator
import net.ghoula.valar.Validator.deriveValidatorMacro

case class Config(host: String, port: Int)

// Requires Validator[String] and Validator[Int] to be in scope (provided by built-ins via import)
// Use explicit name for the given instance
given configValidator: Validator[Config] = deriveValidatorMacro
```

**Important Note on Derivation:** Automatic derivation with `deriveValidatorMacro` requires implicit `Validator` instances to be available in scope for **all** field types within the case class. If a validator for any field type is missing, **compilation will fail**. This strictness ensures that all fields are explicitly considered during validation, preventing silent omissions. See the "Built-in Validators" section below for types supported out-of-the-box.

### Helper Methods

Common validations like non-empty strings, numeric ranges, regex patterns, and more are available in `net.ghoula.valar.ValidationHelpers`:

```scala
import net.ghoula.valar.ValidationHelpers.*

/**
 * Helper methods for common validations demonstrate both usage and results
 */

// String validation - ensures string is not empty
nonEmpty("value") // Returns Valid("value")

// Numeric validations
positiveInt(5) // Validates integer is positive
inRange(3, 1, 10)() // Validates value is within the inclusive range [1, 10]

// Pattern matching validation
regexMatch("abc123", "[a-z]+[0-9]+")() // Validates string matches the pattern

// Option handling
required(Some("value")) // Ensures Option contains a value
optional(Option.empty[String]) // Validates inner value if Some, passes if None
```

## Built-in Validators

Valar provides `given Validator` instances out-of-the-box for many common types to ease setup and support derivation. This includes:

* **Scala Primitives:** `Int` (non-negative), `String` (non-empty), `Boolean`, `Long`, `Double` (finite), `Float` (finite), `Byte`, `Short`, `Char`, `Unit`.
* **Other Scala Types:** `BigInt`, `BigDecimal`, `Symbol`.
* **Common Java Types:** `java.util.UUID`, `java.time.Instant`, `java.time.LocalDate`, `java.time.LocalDateTime`, `java.time.ZonedDateTime`, `java.time.LocalTime`, `java.time.Duration`.
* **Standard Collections:** `Option`, `List`, `Vector`, `Seq`, `Set`, `Array`, `ArraySeq`, `Map` (provided validators exist for their element/key/value types).
* **Tuple Types:** 
  * **Named tuples** 🆕 - Enhanced error messages with actual field names
  * **Regular tuples** - Backward compatible validation (field names not available in errors)
* **Intersection (`&`) and Union (`|`) Types:** Provided corresponding validators for the constituent types exist.

Most built-in validators for scalar types (excluding those with obvious constraints like `Int`, `String`, `Float`, `Double`) are **pass-through** validators, meaning they always return `Valid` by default. This ensures derivation compiles without imposing arbitrary rules. You should define custom validators if you need specific constraints for these types (e.g., range checks for `Long`, specific formats for `UUID`).

## Migration Guide

### From 0.2.x to 0.3.0

**Named Tuples** are a **completely optional** enhancement. Your existing code continues to work unchanged:

```scala
// ✅ This still works exactly as before
val regularTuple: (String, Int) = ("test", 42)
val validator = summon[Validator[(String, Int)]]
validator.validate(regularTuple)

// ✅ New: Enhanced error messages with named tuples
type NamedTuple = (name: String, age: Int)
val namedTuple: NamedTuple = (name = "test", age = 42)
val namedValidator = summon[Validator[NamedTuple]]
namedValidator.validate(namedTuple) // Errors include field names!
```

**Breaking Changes:** None! This is a backward-compatible release.

**To adopt named tuples:**
1. Define your tuple types with field names: `type Person = (name: String, age: Int)`
2. Use automatic derivation: `given Validator[Person] = Validator.deriveValidatorMacro`
3. Enjoy enhanced error messages with actual field names

## Compatibility

- **Scala:** 3.4+ (recommended: 3.7+)
- **Dependencies:** None (zero external dependencies)

> **Note on Scala 3.7**: Scala 3.7 introduces changes to Givens prioritization which may affect how validators are resolved when multiple instances are available. If you define custom validators that could potentially conflict (like for type aliases or inheritance hierarchies), you may need to be more explicit about which validator to use or test thoroughly with your specific Scala version. See the [Scala documentation](https://scala-lang.org/2024/08/19/given-priority-change-3.7.html) for details.

## License

Valar is licensed under the **MIT License**. See the [LICENSE](https://github.com/hakimjonas/valar/blob/main/LICENSE) file for details.
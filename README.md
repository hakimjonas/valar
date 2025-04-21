
# Valar – Type-Safe Validation for Scala 3

Valar is a validation library for Scala 3 designed for clarity and ease of use. It leverages Scala 3's type system and metaprogramming (macros) to help you define complex validation rules with less boilerplate, while providing structured, detailed error messages useful for debugging or user feedback.

## Key Features:

* **Type Safety:** Clearly distinguish between valid results and accumulated errors at compile time using `ValidationResult[A]`. Eliminate runtime errors caused by unexpected validation states.
* **Minimal Boilerplate with Derivation:** Derive `Validator` instances automatically for case classes using compile-time macros. Focus on your rules, not the wiring. (*Note: Derivation requires validators for all field types.*)
* **Flexible Error Handling:** Choose the strategy that fits your use case:
    * **Error Accumulation** (default): Collect all validation failures, ideal for reporting multiple issues (e.g., in UIs or API responses).
    * **Fail-Fast**: Stop validation immediately on the first failure, suitable for performance-sensitive pipelines.
* **Actionable Error Reports:** Generate detailed `ValidationError` objects containing precise field paths, validation rule specifics (like expected vs. actual values), and optional codes/severity, making debugging and error reporting straightforward.
* **Scala 3 Idiomatic:** Built specifically for Scala 3, embracing features like extension methods, given instances, opaque types, and macros for a modern, expressive API.
* **Built-in Support:** Includes validators for standard Scala primitives and common JDK types like `UUID` and `java.time.*` to streamline setup and derivation (see "Built-in Validators" below).

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "net.ghoula" %% "valar" % "0.2.0"
````

## Basic Usage Example

Here's a basic example illustrating how to use Valar for validating a simple case class:

*Note: Valar provides default validators for `String` (non-empty) and `Int` (non-negative). This example explicitly defines `given` instances to demonstrate how you can define custom logic or override the defaults.*

```scala
import net.ghoula.valar.{Validator, ValidationResult}
import net.ghoula.valar.ValidationResult.{Valid, Invalid}
import net.ghoula.valar.ValidationErrors.ValidationError
// Import built-in validators (including String, Int, Option) and derivation helper
import net.ghoula.valar.Validator.given
import net.ghoula.valar.Validator.deriveValidatorMacro

case class User(name: String, age: Option[Int])

// Validator derivation now uses the built-in validators for String, Int, and Option[Int]
// Use an explicit name for the derived validator
given userValidator: Validator[User] = deriveValidatorMacro

// Example with invalid data according to built-in rules
val userInvalid = User("", Some(-10))
// userInvalid: User = User(name = "", age = Some(value = -10))
val resultInvalid = Validator[User].validate(userInvalid) // Uses implicit userValidator
// resultInvalid: ValidationResult[User] = Invalid(
//   errors = Vector(
//     InternalValidationError(
//       message = "Invalid field: name, field type: String: String must not be empty",
//       fieldPath = List("name"),
//       children = Vector(),
//       code = None,
//       severity = None,
//       expected = Some(value = "non-empty string"),
//       actual = Some(value = "")
//     ),
//     InternalValidationError(
//       message = "Invalid field: age, field type: Option: Int must be non-negative",
//       fieldPath = List("age"),
//       children = Vector(),
//       code = None,
//       severity = None,
//       expected = Some(value = ">= 0"),
//       actual = Some(value = "-10")
//     )
//   )
// ) // Uses implicit userValidator

resultInvalid match {
  case Valid(validUser) => println(s"Valid user: $validUser") // Should not happen
  case Invalid(errors) => {
    println("Validation Failed for invalid User:")
    // Output will reflect errors from built-in nonEmptyStringValidator and positiveIntValidator
    println(errors.map(_.prettyPrint(indent = 2)).mkString("\n"))
  }
}
// Validation Failed for invalid User:
// name: Invalid field: name, field type: String: String must not be empty (expected: non-empty string) (got: )
// age: Invalid field: age, field type: Option: Int must be non-negative (expected: >= 0) (got: -10)

// Example with valid data according to built-in rules
val userValid = User("Alice", Some(30))
// userValid: User = User(name = "Alice", age = Some(value = 30))
val resultValid = Validator[User].validate(userValid)
// resultValid: ValidationResult[User] = Valid(
//   value = User(name = "Alice", age = Some(value = 30))
// )

resultValid match {
  case Valid(validUser) => println(s"\nValid user: $validUser")
  case Invalid(errors) => {
    println("Validation Failed for valid User (UNEXPECTED):") // Should not happen
    println(errors.map(_.prettyPrint(indent = 2)).mkString("\n"))
  }
}
// 
// Valid user: User(Alice,Some(30))
```

## Defining Custom Validators

Valar allows you to define custom validators easily by implementing the `Validator` typeclass. Here's an example demonstrating how you might define a validator for an `Age` type:

```scala
import net.ghoula.valar.*
import net.ghoula.valar.ValidationHelpers.*

// Example: Opaque type for Age validated to be non-negative
opaque type Age = Int

object Age {

  // Define the validation rule using a helper
  private def checkAge(value: Int): ValidationResult[Int] =
    positiveInt(value, i => s"Age must be non-negative, got $i")

  // Public smart constructor returning ValidationResult[Age]
  def apply(value: Int): ValidationResult[Age] =
    // If checkAge is Valid(int), map lifts the Int to Age (safe inside companion)
    checkAge(value).map(validatedInt => validatedInt)

  // Validator instance for the opaque type
  // Use explicit name for the given instance
  given ageValidator: Validator[Age] with {
    def validate(value: Age): ValidationResult[Age] =
      // Re-validate the underlying Int. If Valid(_), map preserves the original Age type.
      checkAge(value).map(_ => value)
  }

  // Optional: Users might add extensions to safely expose the value if needed
  // extension (age: Age) def value: Int = age
}

// --- Usage Example ---

val validAgeResult: ValidationResult[Age] = Age(25) // Use smart constructor
// validAgeResult: ValidationResult[Age] = Valid(value = 25) // Use smart constructor
val invalidAgeResult: ValidationResult[Age] = Age(-3)
// invalidAgeResult: ValidationResult[Age] = Invalid(
//   errors = Vector(
//     InternalValidationError(
//       message = "Age must be non-negative, got -3",
//       fieldPath = List(),
//       children = Vector(),
//       code = None,
//       severity = None,
//       expected = Some(value = ">= 0"),
//       actual = Some(value = "-3")
//     )
//   )
// )

println(s"Creating Age(25): $validAgeResult")
// Creating Age(25): Valid(25)
// Expected: Creating Age(25): Valid(25)

println(s"Creating Age(-3): $invalidAgeResult")
// Creating Age(-3): Invalid(Vector(InternalValidationError(Age must be non-negative, got -3,List(),Vector(),None,None,Some(>= 0),Some(-3))))
// Expected: Creating Age(-3): Invalid(Vector(InternalValidationError(Age must be non-negative, got -3,...)))

// Example using the validator on an existing valid Age instance
validAgeResult.toOption.foreach { age => // <--- CORRECTED: Added .toOption
  val validationResult = Validator[Age].validate(age)
  println(s"Validating existing Age $age: $validationResult")}
// Validating existing Age 25: Valid(25)
```

## Core Components

### ValidationResult

Represents the outcome of validation as either `Valid(value)` or `Invalid(errors)`:

```scala
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
trait Validator[A] {
  def validate(a: A): ValidationResult[A]
}
```

Validators can be automatically derived for case classes using `deriveValidatorMacro`:

```scala
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

nonEmpty("value")
positiveInt(5)
inRange(3, 1, 10)() // Validates 3 is in [1, 10]
regexMatch("abc123", "[a-z]+[0-9]+")()
required(Some("value")) // Validates Option is Some
optional(Option.empty[String]) // Validates inner value if Some, passes if None
```

## Built-in Validators

Valar provides `given Validator` instances out-of-the-box for many common types to ease setup and support derivation. This includes:

* **Scala Primitives:** `Int` (non-negative), `String` (non-empty), `Boolean`, `Long`, `Double` (finite), `Float` (finite), `Byte`, `Short`, `Char`, `Unit`.
* **Other Scala Types:** `BigInt`, `BigDecimal`, `Symbol`.
* **Common Java Types:** `java.util.UUID`, `java.time.Instant`, `java.time.LocalDate`, `java.time.LocalDateTime`, `java.time.ZonedDateTime`, `java.time.LocalTime`, `java.time.Duration`.
* **Standard Collections:** `Option`, `List`, `Vector`, `Seq`, `Set`, `Array`, `ArraySeq`, `Map` (provided validators exist for their element/key/value types).
* **Tuple Types:** Validators for tuples are derived implicitly when deriving for case classes.
* **Intersection (`&`) and Union (`|`) Types:** Provided corresponding validators for the constituent types exist.

Most built-in validators for scalar types (excluding those with obvious constraints like `Int`, `String`, `Float`, `Double`) are **pass-through** validators, meaning they always return `Valid` by default. This ensures derivation compiles without imposing arbitrary rules. You should define custom validators if you need specific constraints for these types (e.g., range checks for `Long`, specific formats for `UUID`).

## License

Valar is licensed under the **MIT License**. See the [LICENSE](https://github.com/hakimjonas/valar/blob/main/LICENSE) file for details.
# Valar - Type-Safe Validation for Scala 3

[![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_3?label=maven-central&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-core_3)
[![Scala CI and GitHub Release](https://github.com/hakimjonas/valar/actions/workflows/scala.yml/badge.svg)](https://github.com/hakimjonas/valar/actions/workflows/scala.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

Valar is a validation library for Scala 3. It uses Scala 3's type system and inline metaprogramming to define validation
rules with minimal boilerplate, providing structured error messages for debugging or user feedback.

## What's New in 0.5.X

* **ValidationObserver**: A trait for observing validation outcomes without altering the flow. Useful for logging,
  metrics, or auditing. Zero overhead when not used.
* **valar-translator Module**: Internationalization (i18n) support for validation error messages via the `Translator`
  typeclass.
* **Enhanced ValarSuite**: Updated testing utilities in `valar-munit`.
* **Reworked Derivation**: Uses modern Scala 3 inline metaprogramming for compile-time validation.
* **MiMa Checks**: Binary compatibility verification between versions.
* **Improved Documentation**: Updated scaladoc and module-level README files.

## Key Features

* **Type Safety:** Distinguish between valid results and accumulated errors at compile time using `ValidationResult[A]`.
* **Minimal Boilerplate:** Derive `Validator` instances automatically for case classes using compile-time derivation.
* **Flexible Error Handling:**
    * **Error Accumulation** (default): Collect all validation failures for reporting multiple issues.
    * **Fail-Fast**: Stop on the first failure for performance-sensitive pipelines.
* **Detailed Error Reports:** `ValidationError` objects with field paths, expected vs. actual values, and optional
  codes/severity.
* **Named Tuple Support:** Field-aware error messages for Scala 3.7's named tuples.
* **Scala 3 Idiomatic:** Uses extension methods, given instances, opaque types, and inline metaprogramming.

## Extensibility Pattern

Valar is extensible through the `ValidationObserver` pattern, which integrates with external systems without modifying
core validation logic.

### The ValidationObserver Pattern

```scala
trait ValidationObserver {
  def onResult[A](result: ValidationResult[A]): Unit
}
```

Properties:

* **Zero Overhead**: Default no-op observer is eliminated by the compiler
* **Non-Intrusive**: Observes results without altering the validation flow
* **Composable**: Works with other Valar features and can be chained

### Extension Examples

Current uses:

- **Logging**: Log validation outcomes
- **Metrics**: Collect validation statistics
- **Auditing**: Track validation events

Planned:

- **valar-cats-effect**: Async validation with IO-based observers
- **valar-zio**: ZIO-based validation with resource management

## Available Artifacts

Valar provides artifacts for both JVM and Scala Native platforms:

| Module         | Platform | Artifact ID                  | Maven Central                                                                                                                                                                                                    |
|----------------|----------|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Core**       | JVM      | valar-core_3                 | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-core_3)                                 |
| **Core**       | Native   | valar-core_native0.5_3       | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_native0.5_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-core_native0.5_3)             |
| **MUnit**      | JVM      | valar-munit_3                | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-munit_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-munit_3)                               |
| **MUnit**      | Native   | valar-munit_native0.5_3      | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-munit_native0.5_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-munit_native0.5_3)           |
| **Translator** | JVM      | valar-translator_3           | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-translator_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-translator_3)                     |
| **Translator** | Native   | valar-translator_native0.5_3 | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-translator_native0.5_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-translator_native0.5_3) |

> **Note:** When using the `%%%` operator in sbt, the correct platform-specific artifact will be selected automatically.

## Performance

### Complexity Characteristics

| Operation | Time Complexity | Space Complexity | Notes |
|-----------|----------------|------------------|-------|
| Case class derivation | O(1) - compile-time | N/A | Zero runtime cost, fully inlined |
| Single field validation | O(1) | O(1) | Typically <100ns for simple types |
| Collection validation (List, Vector, etc.) | O(n) | O(n) | n = collection size, with optional size limits |
| Nested case class | O(fields) | O(errors) | Accumulates errors across all fields |
| Union type validation | O(types) | O(errors) | Tries each type in the union |

### Best Practices

1. **Use ValidationConfig limits** for untrusted input to prevent DoS:
   ```scala
   given ValidationConfig = ValidationConfig.strict // Limits collections to 10,000 elements
   ```

2. **Choose the right strategy**:
   - **Error accumulation** (default): Collects all errors, best for user feedback
   - **Fail-fast** (`.flatMap`): Stops at first error, best for performance

3. **Avoid expensive operations** in validators:
   - Database lookups
   - Network calls
   - Heavy computation

   Consider `AsyncValidator` for I/O-bound validation.

4. **Pre-validate at boundaries**: Check size limits before calling Valar:
   ```scala
   if (collection.size > 10000) return BadRequest("Too large")
   ```

### Benchmark Results

Detailed benchmarks available in the [valar-benchmarks module](https://github.com/hakimjonas/valar/blob/main/valar-benchmarks/README.md).

Key findings:
- Simple validations: ~10-50 nanoseconds
- Case class derivation: Zero runtime overhead (compile-time only)
- Collection validation: Linear with collection size
- `ValidationObserver` with no-op has no runtime impact

## Additional Resources

- [Performance Benchmarks](https://github.com/hakimjonas/valar/blob/main/valar-benchmarks/README.md): JMH benchmark results
- [Testing Guide](https://github.com/hakimjonas/valar/blob/main/valar-munit/README.md): ValarSuite testing utilities
- [Internationalization](https://github.com/hakimjonas/valar/blob/main/valar-translator/README.md): i18n support
- [Troubleshooting Guide](https://github.com/hakimjonas/valar/blob/main/TROUBLESHOOTING.md): Common issues and solutions

## Installation

Add the following to your build.sbt:

```scala
// The core validation library (JVM & Scala Native)
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.6.0"

// Optional: For internationalization (i18n) support
libraryDependencies += "net.ghoula" %%% "valar-translator" % "0.6.0"

// Optional: For enhanced testing with MUnit
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.6.0" % Test
```

## Basic Usage Example

Here's a basic example of validating a case class. Valar provides default validators for String (non-empty) and Int (
non-negative).

```scala
import net.ghoula.valar.*
import net.ghoula.valar.ValidationErrors.ValidationError
import net.ghoula.valar.ValidationResult.{Invalid, Valid}
import net.ghoula.valar.ValidationHelpers.*

case class User(name: String, age: Option[Int])

// Define a custom validator for String
given Validator[String] with {
  def validate(value: String): ValidationResult[String] =
    nonEmpty(value, _ => "Name must not be empty")
}

// Define a custom validator for Int
given Validator[Int] with {
  def validate(value: Int): ValidationResult[Int] =
    nonNegativeInt(value, i => s"Age must be non-negative, got $i")
}

// Automatically derive a Validator for the case class User using the givens above
given Validator[User] = Validator.deriveValidatorMacro

val user = User("", Some(-10))
val result: ValidationResult[User] = Validator[User].validate(user)

result match {
  case Valid(validUser) => println(s"Valid user: $validUser")
  case Invalid(errors) =>
    println("Validation Failed:")
    println(errors.map(_.prettyPrint(indent = 2)).mkString("\n"))
}
```

## Testing with valar-munit

The optional valar-munit module provides ValarSuite, a trait that offers powerful, validation-specific assertions to
make your tests clean and expressive.

```scala
import net.ghoula.valar.*
import net.ghoula.valar.munit.ValarSuite

class UserValidationSuite extends ValarSuite {
  // A given Validator for User must be in scope
  given Validator[User] = Validator.deriveValidatorMacro

  test("a valid user should pass validation") {
    val result = Validator[User].validate(User("John", Some(25)))
    val validUser = assertValid(result) // Fails test if Invalid, returns User if Valid
    assertEquals(validUser.name, "John")
  }

  test("a single validation error should be reported correctly") {
    val result = Validator[User].validate(User("", Some(25)))
    // Use assertHasOneError for the common case of a single error
    assertHasOneError(result) { error =>
      assertEquals(error.fieldPath, List("name"))
      assert(error.message.contains("empty"))
    }
  }

  test("multiple validation errors should be accumulated") {
    val result = Validator[User].validate(User("", Some(-10)))
    // Use assertInvalid for testing error accumulation
    assertInvalid(result) { errors =>
      assertEquals(errors.size, 2)
      assert(errors.exists(_.fieldPath.contains("name")))
      assert(errors.exists(_.fieldPath.contains("age")))
    }
  }
}
```

## Core Components

### ValidationResult

Represents the outcome of validation as either Valid(value) or Invalid(errors):

```scala
import net.ghoula.valar.ValidationErrors.ValidationError

enum ValidationResult[+A] {
  case Valid(value: A)
  case Invalid(errors: Vector[ValidationError])
}
```

### ValidationError

Opaque type providing rich context for validation errors, including:

* **message**: Human-readable description of the error.
* **fieldPath**: Path to the field causing the error (e.g., user.address.street).
* **code**: Optional application-specific error codes.
* **severity**: Optional severity indicator (Error, Warning).
* **expected/actual**: Information about expected and actual values.
* **children**: Nested errors for structured reporting.

### Validator[A]

A typeclass defining validation logic for a given type:

```scala
import net.ghoula.valar.ValidationResult

trait Validator[A] {
  def validate(a: A): ValidationResult[A]
}
```

Validators can be automatically derived for case classes using deriveValidatorMacro.

**Important Note on Derivation:** Automatic derivation with deriveValidatorMacro requires implicit Validator instances
to be available in scope for **all** field types within the case class. If a validator for any field type is missing, *
*compilation will fail**. This strictness ensures that all fields are explicitly considered during validation. See the "
Built-in Validators" section for types supported out-of-the-box.

## Built-in Validators

Valar provides pass-through `Validator` instances for common types to enable derivation. All built-in validators accept
any value - constraints are opt-in via `ValidationHelpers`.

**Supported types:**

* **Scala Primitives:** Int, String, Boolean, Long, Double, Float, Byte, Short, Char, Unit
* **Other Scala Types:** BigInt, BigDecimal, Symbol
* **Java Types:** UUID, Instant, LocalDate, LocalDateTime, ZonedDateTime, LocalTime, Duration
* **Collections:** Option, List, Vector, Seq, Set, Array, ArraySeq, Map
* **Tuple Types:** Named tuples and regular tuples
* **Composite Types:** Intersection (&) and Union (|) types

**Opt-in constraints** (from `ValidationHelpers`):

```scala
import net.ghoula.valar.ValidationHelpers.*

// Define constrained validators when you need them
given Validator[Int] with {
  def validate(i: Int) = nonNegativeInt(i)
}

given Validator[String] with {
  def validate(s: String) = nonEmpty(s)
}
```

Available constraint helpers: `nonNegativeInt`, `nonEmpty`, `finiteFloat`, `finiteDouble`, `minLength`, `maxLength`,
`regexMatch`, `inRange`, `oneOf`.

## ValidationObserver Pattern

The `ValidationObserver` trait is more than just a logging mechanism—it's the **foundational pattern** for extending
Valar with custom functionality. This pattern allows you to:

- **Integrate with external systems** (logging, metrics, monitoring)
- **Add side effects** without modifying validation logic
- **Build composable extensions** that work together seamlessly
- **Maintain zero overhead** when extensions aren't needed

```scala
import net.ghoula.valar.*
import org.slf4j.LoggerFactory

// Define a custom observer that logs validation results
given loggingObserver: ValidationObserver with {
  private val logger = LoggerFactory.getLogger("ValidationAnalytics")

  def onResult[A](result: ValidationResult[A]): Unit = result match {
    case ValidationResult.Valid(_) =>
      logger.info("Validation succeeded")
    case ValidationResult.Invalid(errors) =>
      logger.warn(s"Validation failed with ${errors.size} errors: ${errors.map(_.message).mkString(", ")}")
  }
}

// Use the observer in your validation flow
val result = Validator[User].validate(user)
  .observe() // The observer's onResult is called here
  .map(validatedUser => validatedUser.copy(name = validatedUser.name.trim))
```

### Building Custom Extensions

When building extensions for Valar, follow the ValidationObserver pattern:

```scala
// Your custom extension trait
trait MyCustomExtension extends ValidationObserver {
  def onResult[A](result: ValidationResult[A]): Unit = {
    // Your custom logic here
  }
}

// Usage remains clean and composable
val result = Validator[User].validate(user)
  .observe() // Uses your custom extension
  .map(processUser)
```

Key features of ValidationObserver:

* **Zero Overhead**: When using the default no-op observer, the compiler eliminates all observer-related code
* **Non-Intrusive**: Observes validation results without altering the validation flow
* **Chainable**: Works seamlessly with other operations in the validation pipeline
* **Flexible**: Can be used for logging, metrics, alerting, or any other side effect

## Internationalization with valar-translator

The `valar-translator` module provides internationalization (i18n) support for validation error messages:

```scala
import net.ghoula.valar.*
import net.ghoula.valar.translator.Translator

// --- Example Setup ---
// In a real application, this would come from a properties file or other i18n system.
val translations: Map[String, String] = Map(
  "error.string.nonEmpty" -> "The field must not be empty.",
  "error.int.nonNegative" -> "The value cannot be negative.",
  "error.unknown" -> "An unexpected validation error occurred."
)

// --- Implementation of the Translator trait ---
given myTranslator: Translator with {
  def translate(error: ValidationError): String = {
    // Use the error's `code` to find the right translation key.
    val translationKey = error.code.getOrElse("error.unknown")
    translations.getOrElse(
      translationKey,
      error.message // Fall back to the original message if no translation is found
    )
  }
}

// Use the translator in your validation flow
val result = Validator[User].validate(user)
  .observe() // Optional: observe the raw result first
  .translateErrors() // Translate errors for user presentation
```

The `valar-translator` module is designed to:

* Integrate with any i18n library through the `Translator` typeclass
* Compose cleanly with other Valar features like ValidationObserver
* Provide a clear separation between validation logic and presentation concerns

## Migration Guide

For detailed migration instructions, see [MIGRATION.md](https://github.com/hakimjonas/valar/blob/main/MIGRATION.md).

**Latest: v0.6.0** - Breaking change: built-in validators are now pass-through. See the migration guide for details.

## Security Considerations

When using Valar with untrusted user input, please be aware of the following security considerations:

### Regular Expression Denial of Service (ReDoS)

**Warning:** The `regexMatch` methods that accept `String` patterns are vulnerable to ReDoS attacks when used with untrusted input.

**Safe Practice:**
```scala
// SAFE - Use pre-compiled regex patterns
val emailPattern = "^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$".r
regexMatch(userInput, emailPattern)(_ => "Invalid email")
```

**Unsafe Practice:**
```scala
// UNSAFE - Never pass user-provided patterns!
val userPattern = request.getParameter("pattern")
regexMatch(value, userPattern)(_ => "Invalid")  // ReDoS vulnerability!
```

### Input Size Limits

Valar provides built-in protection against resource exhaustion through `ValidationConfig`:

```scala
// For untrusted user input - strict limits
given ValidationConfig = ValidationConfig.strict // Max 10,000 elements

// For trusted internal data - permissive limits
given ValidationConfig = ValidationConfig.permissive // Max 1,000,000 elements

// For complete control - custom limits
given ValidationConfig = ValidationConfig(
  maxCollectionSize = Some(5000),
  maxNestingDepth = Some(20)
)
```

When a collection exceeds the configured limit, validation fails immediately '''before''' processing any elements, preventing:
- Memory exhaustion from extremely large collections
- CPU exhaustion from processing millions of elements
- Application hang or DoS attacks

**Important:** Always use `ValidationConfig.strict` or custom limits when validating untrusted user input.

### Error Information Disclosure

`ValidationError` objects include detailed information about what was expected vs. what was received. When exposing validation errors to end users:
- Review error messages for sensitive information
- Consider using the `valar-translator` module to provide user-friendly, sanitized messages
- Be cautious about exposing internal field names or structure

## Compatibility

* **Scala:** 3.7+
* **Platforms:** JVM, Scala Native
* **Dependencies:** valar-core has a Compile dependency on `io.github.cquiroz:scala-java-time` to provide robust,
  cross-platform support for the `java.time` API.

## License

Valar is licensed under the **MIT License**. See the [LICENSE](https://github.com/hakimjonas/valar/blob/main/LICENSE)
file for details.

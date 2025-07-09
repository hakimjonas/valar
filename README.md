# **Valar – Type-Safe Validation for Scala 3**

[![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_3?label=maven-central&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-core_3)
[![Scala CI and GitHub Release](https://github.com/hakimjonas/valar/actions/workflows/scala.yml/badge.svg)](https://github.com/hakimjonas/valar/actions/workflows/scala.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

Valar is a validation library for Scala 3 designed for clarity and ease of use. It leverages Scala 3's type system and
metaprogramming (macros) to help you define complex validation rules with less boilerplate, while providing structured,
detailed error messages useful for debugging or user feedback.

## **✨ What's New in 0.5.X**

* **🔍 ValidationObserver**: A new trait in `valar-core` for observing validation outcomes without altering the flow,
  perfect for logging, metrics collection, or auditing with zero overhead when not used.
* **🌐 valar-translator Module**: New internationalization (i18n) support for validation error messages through the
  `Translator` typeclass.
* **🧪 Enhanced ValarSuite**: Updated testing utilities in `valar-munit` now used in `valar-translator` for more robust
  validation testing.
* **⚡ Reworked Macros**: Simpler, more performant, and more modern macro implementations for better compile-time
  validation.
* **🛡️ MiMa Checks**: Added binary compatibility verification to ensure smooth upgrades between versions.
* **📚 Improved Documentation**: Comprehensive updates to scaladoc and module-level README files for a better developer
  experience.

## **Key Features**

* **Type Safety:** Clearly distinguish between valid results and accumulated errors at compile time using
  ValidationResult[A]. Eliminate runtime errors caused by unexpected validation states.
* **Minimal Boilerplate:** Derive Validator instances automatically for case classes using compile-time macros,
  significantly reducing repetitive validation logic. Focus on your rules, not the wiring.
* **Flexible Error Handling:** Choose the strategy that fits your use case:
    * **Error Accumulation** (default): Collect all validation failures, ideal for reporting multiple issues (e.g., in
      UIs or API responses).
    * **Fail-Fast**: Stop validation immediately on the first failure, suitable for performance-sensitive pipelines.
* **Actionable Error Reports:** Generate detailed ValidationError objects containing precise field paths, validation
  rule specifics (like expected vs. actual values), and optional codes/severity.
* **Named Tuple Support:** Field-aware error messages for Scala 3.7's named tuples, with preserved backward
  compatibility.
* **Scala 3 Idiomatic:** Built specifically for Scala 3, embracing features like extension methods, given instances,
  opaque types, and macros for a modern, expressive API.

## **Extensibility Pattern**

Valar is designed to be extensible through the **ValidationObserver pattern**, which provides a clean, type-safe way to
integrate with external systems without modifying the core validation logic.

### The ValidationObserver Pattern

The `ValidationObserver` trait serves as the foundational pattern for extending Valar with cross-cutting concerns:

```scala
trait ValidationObserver {
  def onResult[A](result: ValidationResult[A]): Unit
}
```

This pattern offers several advantages:

* **Zero Overhead**: When using the default no-op observer, the compiler eliminates all observer-related code
* **Non-Intrusive**: Observes validation results without altering the validation flow
* **Composable**: Works seamlessly with other Valar features and can be chained
* **Type-Safe**: Leverages Scala's type system for compile-time safety

### Examples of Extensions Using This Pattern

Current implementations are following this pattern:

- **Logging**: Log validation outcomes for debugging and monitoring
- **Metrics**: Collect validation statistics for performance analysis
- **Auditing**: Track validation events for compliance and security

Future extensions planned:

- **valar-cats-effect**: Async validation with IO-based observers
- **valar-zio**: ZIO-based validation with resource management
- **Context-aware validation**: Observers that can access request-scoped data

## **Available Artifacts**

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

## **Additional Resources**

- 📊 **[Performance Benchmarks](https://github.com/hakimjonas/valar/blob/main/valar-benchmarks/README.md)**: Detailed JMH benchmark results and analysis
- 🧪 **[Testing Guide](https://github.com/hakimjonas/valar/blob/main/valar-munit/README.md)**: Enhanced testing utilities with ValarSuite
- 🌐 **[Internationalization](https://github.com/hakimjonas/valar/blob/main/valar-translator/README.md)**: i18n support for validation error messages
## **Installation**

Add the following to your build.sbt:

```scala
// The core validation library (JVM & Scala Native)
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.5.0"

// Optional: For internationalization (i18n) support
libraryDependencies += "net.ghoula" %%% "valar-translator" % "0.5.0"

// Optional: For enhanced testing with MUnit
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.5.0" % Test
```

## **Basic Usage Example**

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

## **Testing with valar-munit**

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

## **Core Components**

### **ValidationResult**

Represents the outcome of validation as either Valid(value) or Invalid(errors):

```scala
import net.ghoula.valar.ValidationErrors.ValidationError

enum ValidationResult[+A] {
  case Valid(value: A)
  case Invalid(errors: Vector[ValidationError])
}
```

### **ValidationError**

Opaque type providing rich context for validation errors, including:

* **message**: Human-readable description of the error.
* **fieldPath**: Path to the field causing the error (e.g., user.address.street).
* **code**: Optional application-specific error codes.
* **severity**: Optional severity indicator (Error, Warning).
* **expected/actual**: Information about expected and actual values.
* **children**: Nested errors for structured reporting.

### **Validator[A]**

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

## **Built-in Validators**

Valar provides given Validator instances out-of-the-box for many common types to ease setup and support derivation. This
includes:

* **Scala Primitives:** Int (non-negative), String (non-empty), Boolean, Long, Double (finite), Float (finite), Byte,
  Short, Char, Unit.
* **Other Scala Types:** BigInt, BigDecimal, Symbol.
* **Common Java Types:** java.util.UUID, java.time.Instant, java.time.LocalDate, java.time.LocalDateTime,
  java.time.ZonedDateTime, java.time.LocalTime, java.time.Duration.
* **Standard Collections:** Option, List, Vector, Seq, Set, Array, ArraySeq, Map (provided validators exist for their
  element/key/value types).
* **Tuple Types:** Named tuples and regular tuples.
* **Intersection (&) and Union (|) Types:** Provided corresponding validators for the constituent types exist.

Most built-in validators for scalar types (excluding those with obvious constraints like Int, String, Float, Double) are
**pass-through** validators. You should define custom validators if you need specific constraints for these types.

## **ValidationObserver, The Core Extensibility Pattern**

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
val result = User.validate(user)
  .observe() // The observer's onResult is called here
  .map(_.toUpperCase)
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
val result = User.validate(user)
  .observe() // Uses your custom extension
  .map(processUser)
```

Key features of ValidationObserver:

* **Zero Overhead**: When using the default no-op observer, the compiler eliminates all observer-related code
* **Non-Intrusive**: Observes validation results without altering the validation flow
* **Chainable**: Works seamlessly with other operations in the validation pipeline
* **Flexible**: Can be used for logging, metrics, alerting, or any other side effect

## **Internationalization with valar-translator**

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
    // Logic to look up the error's key in your translation map.
    // The `.getOrElse` provides a safe fallback.
    translations.getOrElse(
      error.key.getOrElse("error.unknown"),
      error.message // Fall back to the original message if the key is not found
    )
  }
}

// Use the translator in your validation flow
val result = User.validate(user)
  .observe() // Optional: observe the raw result first
  .translateErrors() // Translate errors for user presentation
```

The `valar-translator` module is designed to:

* Integrate with any i18n library through the `Translator` typeclass
* Compose cleanly with other Valar features like ValidationObserver
* Provide a clear separation between validation logic and presentation concerns

## **Migration Guide from v0.4.8 to v0.5.0**

Version 0.5.0 introduces several new features while maintaining backward compatibility with v0.4.8:

1. **New ValidationObserver trait** for observing validation outcomes
2. **New valar-translator module** for internationalization support
3. **Enhanced ValarSuite** with improved testing utilities
4. **Reworked macros** for better performance and modern Scala 3 features
5. **MiMa checks** to ensure binary compatibility

To upgrade to v0.5.0, update your build.sbt:

```scala
// Update core library
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.5.0"

// Add the optional translator module (if needed)
libraryDependencies += "net.ghoula" %%% "valar-translator" % "0.5.0"

// Update testing utilities (if used)
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.5.0" % Test
```

Your existing validation code will continue to work without any changes.

## **Migration Guide from v0.3.0 to v0.4.8**

The main breaking change in v0.4.0 was the **artifact name change** from valar to valar-core to support the modular
architecture.

1. **Update build.sbt**:

```scala
// Replace this:
libraryDependencies += "net.ghoula" %% "valar" % "0.3.0"

// With this (note the triple %%% for cross-platform support):
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.4.8-bundle"
```

## **Compatibility**

* **Scala:** 3.7+
* **Platforms:** JVM, Scala Native
* **Dependencies:** valar-core has a Compile dependency on `io.github.cquiroz:scala-java-time` to provide robust,
  cross-platform support for the `java.time` API.

## **License**

Valar is licensed under the **MIT License**. See the [LICENSE](https://github.com/hakimjonas/valar/blob/main/LICENSE)
file for details.
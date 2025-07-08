# valar-core

[![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_3?label=maven-central&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-core_3)
[![Scala CI and GitHub Release](https://github.com/hakimjonas/valar/actions/workflows/scala.yml/badge.svg)](https://github.com/hakimjonas/valar/actions/workflows/scala.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

The `valar-core` module provides the core validation functionality for Valar, a validation library for Scala 3 designed for clarity and ease of use. It leverages Scala 3's type system and metaprogramming (macros) to help you define complex validation rules with less boilerplate, while providing structured, detailed error messages.

## Key Components

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

### ValidationObserver

The `ValidationObserver` trait provides a mechanism to decouple validation logic from cross-cutting concerns such as logging, metrics collection, or auditing:

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
      logger.warn(s"Validation failed with ${errors.size} errors")
  }
}

// Use the observer in your validation flow
val result = User.validate(user).observe()
```

Key features of ValidationObserver:
* **Zero Overhead**: When using the default no-op observer, the compiler eliminates all observer-related code
* **Non-Intrusive**: Observes validation results without altering the validation flow
* **Chainable**: Works seamlessly with other operations in the validation pipeline
* **Flexible**: Can be used for logging, metrics, alerting, or any other side effect

## Built-in Validators

Valar provides given Validator instances out-of-the-box for many common types to ease setup and support derivation. This includes:

* **Scala Primitives:** Int (non-negative), String (non-empty), Boolean, Long, Double (finite), Float (finite), Byte, Short, Char, Unit.
* **Other Scala Types:** BigInt, BigDecimal, Symbol.
* **Common Java Types:** java.util.UUID, java.time.Instant, java.time.LocalDate, java.time.LocalDateTime, java.time.ZonedDateTime, java.time.LocalTime, java.time.Duration.
* **Standard Collections:** Option, List, Vector, Seq, Set, Array, ArraySeq, Map (provided validators exist for their element/key/value types).
* **Tuple Types:** Named tuples and regular tuples.
* **Intersection (&) and Union (|) Types:** Provided corresponding validators for the constituent types exist.

Most built-in validators for scalar types (excluding those with obvious constraints like Int, String, Float, Double) are **pass-through** validators. You should define custom validators if you need specific constraints for these types.

## Usage

For detailed usage examples and more information, please refer to the [main Valar documentation](https://github.com/hakimjonas/valar).
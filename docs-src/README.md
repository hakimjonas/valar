# **Valar – Type-Safe Validation for Scala 3**

[![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_3?label=maven-central&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-core_3)
[![Scala CI and GitHub Release](https://github.com/hakimjonas/valar/actions/workflows/scala.yml/badge.svg)](https://github.com/hakimjonas/valar/actions/workflows/scala.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

Valar is a validation library for Scala 3 designed for clarity and ease of use. It leverages Scala 3's type system and
metaprogramming (macros) to help you define complex validation rules with less boilerplate, while providing structured,
detailed error messages useful for debugging or user feedback.

## **✨ What's New in 0.4.8**

* **🚀 Bundle Mode**: All modules now offer a `-bundle` version that includes all dependencies, making it easier to use
  in projects.
* **🎯 Platform-Specific Artifacts**: Valar provides dedicated artifacts for both JVM (`valar-core_3`, `valar-munit_3`)
  and Scala Native (`valar-core_native0.5_3`, `valar-munit_native0.5_3`) platforms.
* **📦 Modular Architecture**: The library is split into focused modules: `valar-core` for core validation functionality
  and the optional `valar-munit` for enhanced testing utilities.

## **Key Features**

* **Type Safety:** Clearly distinguish between valid results and accumulated errors at compile time using
  ValidationResult\[A\]. Eliminate runtime errors caused by unexpected validation states.
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

## **Available Artifacts**

Valar provides artifacts for both JVM and Scala Native platforms:

| Module    | Platform | Artifact ID             | Standard Version                                                                                                                                                                                       | Bundle Version                                                                                                                                                                                                           |
|-----------|----------|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Core**  | JVM      | valar-core_3            | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-core_3)                       | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_3?label=bundle&style=flat-square&classifier=bundle)](https://central.sonatype.com/artifact/net.ghoula/valar-core_3)                       |
| **Core**  | Native   | valar-core_native0.5_3  | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_native0.5_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-core_native0.5_3)   | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-core_native0.5_3?label=bundle&style=flat-square&classifier=bundle)](https://central.sonatype.com/artifact/net.ghoula/valar-core_native0.5_3)   |
| **MUnit** | JVM      | valar-munit_3           | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-munit_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-munit_3)                     | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-munit_3?label=bundle&style=flat-square&classifier=bundle)](https://central.sonatype.com/artifact/net.ghoula/valar-munit_3)                     |
| **MUnit** | Native   | valar-munit_native0.5_3 | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-munit_native0.5_3?label=latest&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-munit_native0.5_3) | [![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-munit_native0.5_3?label=bundle&style=flat-square&classifier=bundle)](https://central.sonatype.com/artifact/net.ghoula/valar-munit_native0.5_3) |

The **bundle versions** (with `-bundle` suffix) include all dependencies, making them easier to use in projects that
don't need fine-grained dependency control.

> **Note:** When using the `%%%` operator in sbt, the correct platform-specific artifact will be selected automatically.

## **Installation**

Add the following to your build.sbt:

```scala
// The core validation library (JVM & Scala Native)
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.4.8"

// Optional: For enhanced testing with MUnit
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.4.8" % Test

// Alternatively, use bundle versions with all dependencies included
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.4.8-bundle"
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.4.8-bundle" % Test
```

## **Basic Usage Example**

Here's a basic example of validating a case class. Valar provides default validators for String (non-empty) and Int (non-negative).

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
to be available in scope for **all** field types within the case class. If a validator for any field type is missing, 
**compilation will fail**. This strictness ensures that all fields are explicitly considered during validation. See the
"Built-in Validators" section for types supported out-of-the-box.

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

## **Migration Guide from v0.3.0**

The main breaking change since v0.4.0 is the **artifact name has changed** from valar to valar-core to support the new
modular architecture.

1. **Update build.sbt**:
   ```scala
   // Replace this:
   libraryDependencies += "net.ghoula" %% "valar" % "0.3.0"

   // With this (note the triple %%% for cross-platform support):
   libraryDependencies += "net.ghoula" %%% "valar-core" % "0.4.8"
   ```

2. **Add optional testing utilities** (if desired):
   ```scala
   libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.4.8" % Test
   ```

3. **For simplified dependency management** (optional):
   ```scala
   // Use bundle versions with all dependencies included
   libraryDependencies += "net.ghoula" %%% "valar-core" % "0.4.8-bundle"
   libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.4.8-bundle" % Test
   ```

Your existing validation code will continue to work without any changes.

## **Compatibility**

* **Scala:** 3.7+
* **Platforms:** JVM, Scala Native
* **Dependencies:** valar-core has a Compile dependency on `io.github.cquiroz:scala-java-time` to provide robust,
  cross-platform support for the `java.time` API.

## **License**

Valar is licensed under the **MIT License**. See the [LICENSE](https://github.com/hakimjonas/valar/blob/main/LICENSE)
file for details.

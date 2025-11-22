# Migration Guide

## Migrating from v0.5.0 to v0.6.0

Version 0.6.0 includes a **breaking change** to built-in validators.

### Breaking Change: Pass-Through Validators

Built-in validators for `Int`, `String`, `Float`, and `Double` are now **pass-through** (accept all values). Previously
they enforced constraints:

| Type   | v0.5.0 (constrained)         | v0.6.0 (pass-through) |
|--------|------------------------------|-----------------------|
| Int    | Rejects negative values      | Accepts all values    |
| String | Rejects empty strings        | Accepts all values    |
| Float  | Rejects NaN/Infinity         | Accepts all values    |
| Double | Rejects NaN/Infinity         | Accepts all values    |

**Why this change?** The opinionated defaults limited Valar's use as a general-purpose foundation. Users validating
temperatures (negative values valid), legacy data (empty strings valid), or scientific data (NaN meaningful) had to
fight the library's defaults.

### Migration Steps

If you relied on the constrained defaults, define explicit validators using `ValidationHelpers`:

```scala
import net.ghoula.valar.*
import net.ghoula.valar.ValidationHelpers.*

// Restore v0.5.0 behavior for Int
given Validator[Int] with {
  def validate(i: Int) = nonNegativeInt(i)
}

// Restore v0.5.0 behavior for String
given Validator[String] with {
  def validate(s: String) = nonEmpty(s)
}

// Restore v0.5.0 behavior for Float
given Validator[Float] with {
  def validate(f: Float) = finiteFloat(f)
}

// Restore v0.5.0 behavior for Double
given Validator[Double] with {
  def validate(d: Double) = finiteDouble(d)
}
```

## Migrating from v0.4.8 to v0.6.0

If upgrading directly from v0.4.8, you'll get all features from v0.5.0 plus the v0.6.0 changes:

**New in v0.5.0:**
1. **ValidationObserver trait** for observing validation outcomes without altering the flow
2. **valar-translator module** for internationalization support of validation error messages
3. **Enhanced ValarSuite** with improved testing utilities
4. **Reworked derivation** using modern Scala 3 inline metaprogramming
5. **MiMa checks** to ensure binary compatibility between versions

**New in v0.6.0:**
- **Breaking**: Built-in validators are now pass-through (see v0.5.0 to v0.6.0 migration above)
- Internal DRY refactoring for better maintainability

### Update build.sbt:

```scala
// Update core library
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.6.0"

// Add the optional translator module (if needed)
libraryDependencies += "net.ghoula" %%% "valar-translator" % "0.6.0"

// Update testing utilities (if used)
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.6.0" % Test
```

If you relied on the constrained defaults for Int/String/Float/Double, see the v0.5.0 to v0.6.0 migration section above.

### Using the New Features

#### Core Extensibility Pattern (ValidationObserver)

The ValidationObserver pattern has been added to valar-core as the **standard way to extend Valar**. This pattern provides:

* A consistent API for integrating with external systems
* Zero-cost abstractions when extensions aren't used
* Type-safe composition with other Valar features

Future Valar modules (like valar-cats-effect and valar-zio) will build upon this pattern, making it the **recommended approach** for anyone building custom Valar extensions.

The ValidationObserver trait allows you to observe validation results without altering the flow:

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
  .observe()  // The observer's onResult is called here
  .map(_.toUpperCase)
```

Key features of ValidationObserver:

* **Zero Overhead**: When using the default no-op observer, the compiler eliminates all observer-related code
* **Non-Intrusive**: Observes validation results without altering the validation flow
* **Chainable**: Works seamlessly with other operations in the validation pipeline
* **Flexible**: Can be used for logging, metrics, alerting, or any other side effect

#### valar-translator

The valar-translator module provides internationalization support:

```scala
import net.ghoula.valar.*
import net.ghoula.valar.translator.Translator

// --- Example Setup ---
// In a real application, this would come from a properties file or other i18n system.
val translations: Map[String, String] = Map(
  "error.string.nonEmpty" -> "The field must not be empty.",
  "error.int.nonNegative" -> "The value cannot be negative.",
  "error.unknown"         -> "An unexpected validation error occurred."
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
  .observe()  // Optional: observe the raw result first
  .translateErrors()  // Translate errors for user presentation
```

The `valar-translator` module is designed to:

* Integrate with any i18n library through the `Translator` typeclass
* Compose cleanly with other Valar features like ValidationObserver
* Provide a clear separation between validation logic and presentation concerns

## Migrating from v0.3.0 to v0.4.8

The main breaking change in v0.4.0 was the **artifact name change** from valar to valar-core to support the new modular architecture.

### Update build.sbt:

```scala
// Replace this:
libraryDependencies += "net.ghoula" %% "valar" % "0.3.0"

// With this (note the triple %%% for cross-platform support):
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.4.8-bundle"

// Add optional testing utilities (if desired):
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.4.8-bundle" % Test
```

> **Note:** v0.4.8 used bundle versions (`-bundle` suffix) that included all dependencies. Starting from v0.5.0, we've moved to the standard approach without bundle versions for simpler dependency management.

### Available Artifacts for v0.4.8

The `%%%` operator in sbt will automatically select the appropriate artifact for your platform (JVM or Native). For v0.4.8, only bundle versions are available:

| Module | Platform | Artifact ID             | Bundle Version                                              |
|--------|----------|-------------------------|-------------------------------------------------------------|
| Core   | JVM      | valar-core_3            | `"net.ghoula" %% "valar-core" % "0.4.8-bundle"`             |
| Core   | Native   | valar-core_native0.5_3  | `"net.ghoula" % "valar-core_native0.5_3" % "0.4.8-bundle"`  |
| MUnit  | JVM      | valar-munit_3           | `"net.ghoula" %% "valar-munit" % "0.4.8-bundle"`            |
| MUnit  | Native   | valar-munit_native0.5_3 | `"net.ghoula" % "valar-munit_native0.5_3" % "0.4.8-bundle"` |

Your existing validation code will continue to work without any changes.

## Note on Scala 3.7+ Givens Prioritization

Scala 3.7 changes how the compiler resolves given instances when multiple candidates are available. Previously, the
compiler would select the most specific subtype, but now it chooses based on different prioritization rules.

This may affect your code if:

* You have multiple validator instances for the same type or related types (e.g., through type aliases or inheritance).
* You rely on implicit resolution to select the correct validator.

### Potential Issues

The most common issue is with type aliases, where you might have defined:

```scala
type Email = String

// General validator
given Validator[String] with { ... }

// More specific validator
given Validator[Email] with { ... }

// Which one gets used? In 3.6 vs. 3.7 it might be different!
val result = summon[Validator[Email]].validate(email)
```

### Solutions

1. **Explicit imports**: Place validators in objects and import only the ones you need.

    ```scala
    object validators {
      given stringValidator: Validator[String] with { ... }
      given emailValidator: Validator[Email] with { ... }
    }

    // Be explicit about which one to use
    import validators.emailValidator
    ```

2. **Named instances**: Give your validators explicit names and use them directly.

    ```scala
    given generalStringValidator: Validator[String] with { ... }
    given specificEmailValidator: Validator[Email] with { ... }

    // Use the specific one explicitly
    val result = specificEmailValidator.validate(email)
    ```

3. **Extension methods**: Define your validation logic as extension methods to avoid ambiguity.

    ```scala
    extension (email: Email) {
      def validate: ValidationResult[Email] = { ... }
    }
    ```

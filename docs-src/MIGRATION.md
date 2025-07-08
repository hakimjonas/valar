# Migration Guide

## Migrating from v0.4.8 to v0.5.0

Version 0.5.0 introduces several new features while maintaining backward compatibility with v0.4.8:

1. **New ValidationObserver trait** for observing validation outcomes without altering the flow
2. **New valar-translator module** for internationalization support of validation error messages
3. **Enhanced ValarSuite** with improved testing utilities
4. **Reworked macros** for better performance and modern Scala 3 features
5. **MiMa checks** to ensure binary compatibility between versions

### Update build.sbt:

```scala
// Update core library
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.5.0"

// Add optional translator module (if needed)
libraryDependencies += "net.ghoula" %%% "valar-translator" % "0.5.0"

// Update testing utilities (if used)
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.5.0" % Test

// Alternatively, use bundle versions with all dependencies included
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.5.0-bundle"
libraryDependencies += "net.ghoula" %%% "valar-translator" % "0.5.0-bundle"
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.5.0-bundle" % Test
```

Your existing validation code will continue to work without any changes.

### Using the New Features

#### ValidationObserver

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
      logger.warn(s"Validation failed with ${errors.size} errors")
  }
}

// Use the observer in your validation flow
val result = User.validate(user).observe()
```

#### valar-translator

The valar-translator module provides internationalization support:

```scala
import net.ghoula.valar.*
import net.ghoula.valar.translator.Translator

// Implement the Translator trait with your i18n library
given myTranslator: Translator with {
  def translate(error: ValidationError): String = {
    // Logic to look up the error's key and format with its arguments
    I18n.lookup(error.key.getOrElse("error.unknown"), error.args)
  }
}

// Use the translator in your validation flow
val result = User.validate(user).translateErrors()
```

## Migrating from v0.3.0 to v0.4.8

The main breaking change since v0.4.0 is the artifact name has changed from valar to valar-core to support the new modular
architecture.

### Update build.sbt:

```scala
// Replace this:
libraryDependencies += "net.ghoula" %% "valar" % "0.3.0"

// With this (note the triple %%% for cross-platform support):
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.4.8"

// Add optional testing utilities (if desired):
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.4.8" % Test

// Alternatively, use bundle versions with all dependencies included:
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.4.8-bundle"
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.4.8-bundle" % Test
```

### Available Artifacts

The `%%%` operator in sbt will automatically select the appropriate artifact for your platform (JVM or Native). If you need to reference a specific artifact directly, here are all the available options:

| Module | Platform | Artifact ID             | Standard Version                                     | Bundle Version                                              |
|--------|----------|-------------------------|------------------------------------------------------|-------------------------------------------------------------|
| Core   | JVM      | valar-core_3            | `"net.ghoula" %% "valar-core" % "0.4.8"`             | `"net.ghoula" %% "valar-core" % "0.4.8-bundle"`             |
| Core   | Native   | valar-core_native0.5_3  | `"net.ghoula" % "valar-core_native0.5_3" % "0.4.8"`  | `"net.ghoula" % "valar-core_native0.5_3" % "0.4.8-bundle"`  |
| MUnit  | JVM      | valar-munit_3           | `"net.ghoula" %% "valar-munit" % "0.4.8"`            | `"net.ghoula" %% "valar-munit" % "0.4.8-bundle"`            |
| MUnit  | Native   | valar-munit_native0.5_3 | `"net.ghoula" % "valar-munit_native0.5_3" % "0.4.8"` | `"net.ghoula" % "valar-munit_native0.5_3" % "0.4.8-bundle"` |

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

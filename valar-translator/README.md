# valar-translator

[![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-translator_3?label=maven-central&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-translator_3)
[![Scala CI and GitHub Release](https://github.com/hakimjonas/valar/actions/workflows/scala.yml/badge.svg)](https://github.com/hakimjonas/valar/actions/workflows/scala.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

The `valar-translator` module provides internationalization (i18n) support for Valar's validation error messages. It introduces a `Translator` typeclass that allows you to integrate with any i18n library to convert structured validation errors into localized, human-readable strings.

## Installation

Add the valar-translator dependency to your build.sbt:

```scala
libraryDependencies += "net.ghoula" %%% "valar-translator" % "0.5.0"
```

## Usage

The module provides a `Translator` trait and an extension method, `translateErrors()`, on `ValidationResult`.

### 1. Implement the `Translator` Trait

Create a `given` instance of `Translator` that contains your localization logic. This typically involves looking up a key from a resource bundle.

```scala
import net.ghoula.valar.translator.Translator
import net.ghoula.valar.ValidationErrors.ValidationError

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
```

### 2. Call `translateErrors()`

Chain the `.translateErrors()` method to your validation call. It will use the in-scope `given Translator` to transform the error messages.

```scala
val result = User.validate(someData) // An Invalid ValidationResult
val translatedResult = result.translateErrors()

// translatedResult now contains errors with localized messages
```

## Integration with the ValidationObserver Extensibility Pattern

The `valar-translator` module is built to work seamlessly with Valar's extensibility system, specifically the **ValidationObserver pattern** that forms the foundation for all Valar extensions.

This architectural alignment means that the translator module integrates naturally with other extensions that follow the same pattern:

* **ValidationObserver Pattern (from `valar-core`)**: The foundation for all extensions, enabling side effects without changing the validation result
* **Translator (from `valar-translator`)**: Built on top of the core pattern, transforming validation errors for localization

While these serve different purposes, they're designed to work together in a clean, composable way:

A common workflow is to first use the `ValidationObserver` to log or collect metrics on the raw, untranslated error, and then use the `Translator` to prepare the error for user presentation.

```scala
// Given a defined extension using the ValidationObserver pattern
given metricsObserver: ValidationObserver with {
  def onResult[A](result: ValidationResult[A]): Unit = {
    // Record validation metrics to your monitoring system
  }
}

// And a translator implementation for localization
given myTranslator: Translator with {
  def translate(error: ValidationError): String = {
    // Translate errors using your i18n system
  }
}

// Both extensions work together through the same pattern
val result = User.validate(invalidUser)
  // First, observe the raw result using the core ValidationObserver pattern
  .observe()  
  // Then, translate the errors for presentation (also built on the same pattern)
  .translateErrors()

// This demonstrates how all Valar extensions follow the same architectural pattern,
// allowing them to compose together seamlessly
```

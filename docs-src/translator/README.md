# valar-translator

[![Maven Central](https://img.shields.io/maven-central/v/net.ghoula/valar-translator_3?label=maven-central&style=flat-square)](https://central.sonatype.com/artifact/net.ghoula/valar-translator_3)
[![Scala CI and GitHub Release](https://github.com/hakimjonas/valar/actions/workflows/scala.yml/badge.svg)](https://github.com/hakimjonas/valar/actions/workflows/scala.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

The `valar-translator` module provides internationalization (i18n) support for Valar's validation error messages. It introduces a `Translator` typeclass that allows you to integrate with any i18n library to convert structured validation errors into localized, human-readable strings.

## Usage

The module provides a `Translator` trait and an extension method, `translateErrors()`, on `ValidationResult`.

### 1. Implement the `Translator` Trait

Create a `given` instance of `Translator` that contains your localization logic. This typically involves looking up a key from a resource bundle.

```scala
import net.ghoula.valar.translator.Translator
import net.ghoula.valar.ValidationErrors.ValidationError

// Assuming you have an I18n library
given myTranslator: Translator with {
  def translate(error: ValidationError): String = {
    // Logic to look up the error's key and format with its arguments
    I18n.lookup(
      error.key.getOrElse("error.unknown"),
      error.args
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

## Composing with Core Features (like ValidationObserver)

The `valar-translator` module is designed to compose cleanly with features from the core library. A prime example is the **`ValidationObserver` pattern**, a general-purpose tool for side effects (like logging or metrics) that is **available directly in `valar-core`**.

While the two patterns serve different purposes, they can be chained together for a powerful workflow:

* **`ValidationObserver` (Side Effect)**: Reacts to a result without changing it.
* **`Translator` (Data Transformation)**: Refines a result by localizing error messages.

A common workflow is to first use the `ValidationObserver` to log or collect metrics on the raw, untranslated error, and then use the `Translator` to prepare the error for user presentation.

```scala
// Given a defined `metricsObserver` from your application
// and a `myTranslator` from this module...

val result = User.validate(invalidUser)
  // 1. First, observe the raw result using the core ValidationObserver.
  .observe()
  // 2. Then, translate the errors for presentation using the Translator.
  .translateErrors()

// The final `result` contains user-friendly, translated messages,
// while the original, structured error was sent to your metrics system.
```

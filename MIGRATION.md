# Migration Guide

## Migrating from v0.3.0 to v0.4.0

The main breaking change in v0.4.0 is the artifact name has changed from valar to valar-core to support the new modular
architecture.

### Update build.sbt:

```scala
// Replace this:
libraryDependencies += "net.ghoula" %% "valar" % "0.3.0"

// With this (note the triple %%% for cross-platform support):
libraryDependencies += "net.ghoula" %%% "valar-core" % "0.4.0"

// Add optional testing utilities (if desired):
libraryDependencies += "net.ghoula" %%% "valar-munit" % "0.4.0" % Test
```

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

// Which one gets used? In 3.6 vs 3.7 it might be different!
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

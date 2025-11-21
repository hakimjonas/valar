# Valar Design

This document explains the technical design choices in Valar and how Scala 3's type system enables its implementation.

## Typeclass Derivation: Scala 2 vs Scala 3

### Scala 2 Approach

In Scala 2, automatic typeclass derivation required external libraries:

**Shapeless** - Generic programming via HLists:
```scala
import shapeless._

trait Validator[A] {
  def validate(a: A): ValidationResult[A]
}

object Validator {
  // Derive using Generic and HList machinery
  implicit def deriveHNil: Validator[HNil] = ...
  implicit def deriveHCons[H, T <: HList](
    implicit hv: Validator[H], tv: Validator[T]
  ): Validator[H :: T] = ...
  implicit def deriveGeneric[A, R](
    implicit gen: Generic.Aux[A, R], rv: Validator[R]
  ): Validator[A] = ...
}
```

Problems:
- Complex implicit resolution chains
- Slow compile times for large case classes
- Cryptic error messages ("could not find implicit value for...")
- Runtime overhead from HList conversions

**Magnolia** - Macro-based derivation:
```scala
import magnolia1._

object Validator extends AutoDerivation[Validator] {
  def join[A](ctx: CaseClass[Validator, A]): Validator[A] = new Validator[A] {
    def validate(a: A) = {
      ctx.parameters.foldLeft(ValidationResult.Valid(a)) { (acc, param) =>
        // validate each parameter
      }
    }
  }
}
```

Better than Shapeless but still:
- Requires macro annotation or explicit derivation calls
- Limited compile-time introspection
- Error messages still opaque

### Scala 3 Approach

Scala 3 provides first-class support for typeclass derivation through:

1. **Mirrors** - Compile-time type information
2. **Inline/Quotes** - Type-safe metaprogramming
3. **Match types** - Type-level computation

Valar's implementation:

```scala
import scala.deriving.Mirror
import scala.quoted.*

object Validator {
  // User-facing API
  inline def derive[T](using m: Mirror.ProductOf[T]): Validator[T] =
    ${ deriveSyncValidatorImpl[T, m.MirroredElemTypes, m.MirroredElemLabels]('m) }

  // Compile-time implementation
  private def deriveSyncValidatorImpl[T: Type, Elems <: Tuple: Type, Labels <: Tuple: Type](
    m: Expr[Mirror.ProductOf[T]]
  )(using q: Quotes): Expr[Validator[T]] = {
    // Extract field names and types at compile time
    // Generate validation code for each field
    // Return fully inlined validator
  }
}
```

Advantages:
- **Zero runtime overhead** - all derivation happens at compile time
- **Clear error messages** - Valar reports exactly which field validators are missing
- **Direct field access** - no HList conversion, uses `Select.unique` for zero-cast access
- **Type-safe** - the quotes API ensures generated code is well-typed

### Compile-Time Validation

Valar validates that all required validators exist before generating code:

```scala
// If you write:
case class User(name: String, age: Int, data: MyCustomType)
given Validator[User] = Validator.derive

// And MyCustomType has no Validator, you get:
// error: Cannot derive Validator for User: missing validators for 1 field(s).
//   - Field 'data' of type MyCustomType
//     Add: given Validator[MyCustomType] = ...
```

This is implemented by checking `Expr.summon[Validator[FieldType]]` for each field at compile time and collecting all failures before reporting.

## Architecture

### Core Types

```
ValidationResult[+A]
├── Valid(value: A)
└── Invalid(errors: Vector[ValidationError])

Validator[A]           - Synchronous validation
AsyncValidator[A]      - Asynchronous validation (Future-based)
ValidationObserver     - Side-effect hook for logging/metrics
ValidationConfig       - Runtime configuration (collection limits)
```

### Field Access Strategy

Valar uses different strategies depending on the type being validated:

| Type | Strategy | Cast Required |
|------|----------|---------------|
| Case class | `Select.unique(term, fieldName)` | No |
| Regular tuple | `Select.unique(term, "_N")` | No |
| Named tuple | `productElement(index)` | Yes (matches stdlib) |

This was determined by examining how the Scala 3.7.4 standard library handles named tuples - they also use `productElement` with a cast.

### Error Accumulation

By default, Valar accumulates all errors rather than failing fast:

```scala
// zip combines results, accumulating errors
val result = validateName(name).zip(validateAge(age)).zip(validateEmail(email))

// flatMap is fail-fast (stops on first error)
val result = for {
  n <- validateName(name)
  a <- validateAge(age)
  e <- validateEmail(email)
} yield User(n, a, e)
```

The `Invalid` case uses `Vector[ValidationError]` for efficient concatenation during accumulation.

## Why Inline Metaprogramming?

Valar uses Scala 3's inline metaprogramming (quotes/reflect API) rather than runtime reflection because:

1. **Performance** - No runtime type inspection or reflection calls
2. **Type safety** - Generated code is checked by the compiler
3. **Early errors** - Missing validators are caught at compile time
4. **Optimization** - The compiler can inline and optimize the generated code

The term "inline metaprogramming" reflects modern Scala 3 terminology. The quotes/reflect API is the standard approach for compile-time code generation, replacing Scala 2's `scala.reflect` macros.

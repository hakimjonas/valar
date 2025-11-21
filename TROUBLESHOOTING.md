# Valar Troubleshooting Guide

This guide helps you diagnose and resolve common issues when using Valar.

## Table of Contents

- [Compilation Errors](#compilation-errors)
- [Runtime Issues](#runtime-issues)
- [Performance Problems](#performance-problems)
- [Security Concerns](#security-concerns)
- [Best Practices](#best-practices)

---

## Compilation Errors

### Error: "Cannot derive Validator for X: missing validator for field type Y"

**Problem:**
```scala
case class User(name: String, age: Int, customType: MyCustomType)
given Validator[User] = Validator.derive

// Compile error: Cannot derive Validator for User:
// missing validator for field type MyCustomType
```

**Cause:** Valar's automatic derivation requires a `Validator` instance to be in scope for **every** field type in your case class. `MyCustomType` doesn't have a validator.

**Solution:** Define a validator for the missing type:

```scala
// Option 1: Provide a custom validator
given Validator[MyCustomType] with {
  def validate(value: MyCustomType): ValidationResult[MyCustomType] = {
    // Your validation logic
    ValidationResult.Valid(value)
  }
}

// Option 2: If it's another case class, derive it first
given Validator[MyCustomType] = Validator.derive

// Now this will work
given Validator[User] = Validator.derive
```

**Prevention:** Always ensure validators exist for all field types before deriving a case class validator.

---

### Error: "ambiguous implicit values" or "diverging implicit expansion"

**Problem:**
```scala
// Multiple validators in scope for the same type
given validator1: Validator[String] = ...
given validator2: Validator[String] = ...

val result = Validator[String].validate("test") // Ambiguous!
```

**Cause:** Multiple `given` instances of the same validator type are in scope, and the compiler doesn't know which to use.

**Solution 1 - Use explicit priority:**
```scala
// Make one validator lower priority
object LowPriorityValidators {
  given validator1: Validator[String] = ...
}

given validator2: Validator[String] = ... // This takes priority
```

**Solution 2 - Use explicit instances:**
```scala
val result = validator2.validate("test") // Explicitly use validator2
```

**Solution 3 - Scope your validators:**
```scala
object StrictValidation {
  given strictStringValidator: Validator[String] = ...
}

object PermissiveValidation {
  given permissiveStringValidator: Validator[String] = ...
}

// Import only the one you need
import StrictValidation.given
```

---

### Error: "value deriveValidatorMacro is not a member of object Validator"

**Problem:**
```scala
given Validator[User] = Validator.deriveValidatorMacro
// Error: value deriveValidatorMacro is not a member of object Validator
```

**Cause:** You're using the old API from Valar 0.4.x or earlier.

**Solution:** Use the new `derive` method:

```scala
// Old (0.4.x):
given Validator[User] = Validator.deriveValidatorMacro

// New (0.5.x+):
given Validator[User] = Validator.derive
```

See [MIGRATION.md](MIGRATION.md) for complete migration guide.

---

## Runtime Issues

### Issue: ValidationResult shows "Programmer error: Cannot create Invalid ValidationResult from an empty error vector"

**Problem:**
```scala
val errors = Vector.empty[ValidationError]
ValidationResult.invalid(errors) // Runtime error!
```

**Cause:** You're trying to create an `Invalid` result with no errors, which violates Valar's invariants.

**Solution 1 - Check before creating Invalid:**
```scala
if (errors.nonEmpty) ValidationResult.invalid(errors)
else ValidationResult.Valid(value) // Or handle the empty case appropriately
```

**Solution 2 - Use fromEitherErrors for external data:**
```scala
// This handles the empty case safely
ValidationResult.fromEitherErrors(eitherWithPossiblyEmptyErrors)
```

---

### Issue: Collection validation fails with "Collection size exceeds maximum allowed size"

**Problem:**
```scala
val largeList = List.fill(20000)(1)
val result = Validator[List[Int]].validate(largeList)
// Invalid: Collection size (20000) exceeds maximum allowed size (10000)
```

**Cause:** You have a `ValidationConfig.strict` in scope that limits collection sizes for security.

**Solution 1 - Use appropriate config for your use case:**
```scala
// For untrusted user input - keep strict limits
given ValidationConfig = ValidationConfig.strict

// For internal, trusted data - use permissive or default
given ValidationConfig = ValidationConfig.permissive

// For complete trust - no limits
given ValidationConfig = ValidationConfig.default
```

**Solution 2 - Custom limits:**
```scala
given ValidationConfig = ValidationConfig(
  maxCollectionSize = Some(50000) // Your custom limit
)
```

**Solution 3 - Pre-validate size:**
```scala
if (userList.size > 10000) {
  // Handle oversized input at application boundary
  return BadRequest("List too large")
}
// Now validate with Valar
val result = Validator[List[Data]].validate(userList)
```

---

## Performance Problems

### Issue: Validation of large collections is slow

**Problem:** Validating collections with thousands of elements takes several seconds.

**Diagnosis:**
```scala
val start = System.nanoTime()
val result = Validator[List[ComplexType]].validate(bigList)
val duration = (System.nanoTime() - start) / 1_000_000
println(s"Validation took ${duration}ms")
```

**Solutions:**

**1. Use ValidationConfig size limits** (prevents DoS):
```scala
given ValidationConfig = ValidationConfig.strict // Limits to 10,000 elements
```

**2. Use fail-fast validation** (stops on first error):
```scala
// Instead of accumulating all errors
bigList.foldLeft[ValidationResult[List[A]]](Valid(Nil)) { (acc, item) =>
  acc.flatMap { list =>  // flatMap = fail-fast
    validator.validate(item).map(v => list :+ v)
  }
}
```

**3. Parallel validation** (for independent items):
```scala
import scala.concurrent.{Future, ExecutionContext}

// Use AsyncValidator for concurrent validation
given ec: ExecutionContext = ...
val asyncValidator = AsyncValidator[ComplexType]

val futureResults: Future[List[ValidationResult[ComplexType]]] =
  Future.traverse(bigList)(item => asyncValidator.validateAsync(item))
```

**4. Simplify validation rules:**
- Remove expensive validation logic for large batches
- Use sampling: validate every Nth item instead of all items
- Move complex validation to a background job

---

### Issue: Stack overflow with deeply nested case classes

**Problem:**
```scala
case class Node(value: Int, children: List[Node])

val deeplyNested = Node(1, List(Node(2, List(Node(3, List(...))))))
Validator[Node].validate(deeplyNested) // StackOverflowError!
```

**Cause:** Recursive validation of deeply nested structures exceeds JVM stack depth.

**Solution 1 - Limit nesting depth at data boundary:**
```scala
def checkDepth[A](node: Node, maxDepth: Int = 100): Either[String, Node] = {
  def loop(n: Node, depth: Int): Either[String, Unit] = {
    if (depth > maxDepth) Left(s"Nesting depth exceeds $maxDepth")
    else n.children.foldLeft[Either[String, Unit]](Right(())) {
      case (Right(_), child) => loop(child, depth + 1)
      case (left, _) => left
    }
  }
  loop(node, 0).map(_ => node)
}
```

**Solution 2 - Use iterative validation:**
```scala
// Instead of recursive derivation, write custom iterative validator
given Validator[Node] with {
  def validate(root: Node): ValidationResult[Node] = {
    val queue = scala.collection.mutable.Queue(root)
    while (queue.nonEmpty) {
      val node = queue.dequeue()
      // Validate this node
      queue.enqueueAll(node.children)
    }
    ValidationResult.Valid(root)
  }
}
```

---

## Security Concerns

### Issue: Application hangs when validating user input with regex

**Problem:** User provides input, validation never returns.

**Cause:** Regular Expression Denial of Service (ReDoS) attack. See [README.md Security Considerations](README.md#security-considerations).

**Solution:**

**1. Never accept user regex patterns:**
```scala
// ❌ VULNERABLE
val userPattern = request.getParameter("pattern")
regexMatch(value, userPattern)

// ✅ SAFE
val emailPattern = "^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$".r
regexMatch(value, emailPattern)
```

**2. Use pre-compiled, tested regex:**
```scala
object SafePatterns {
  val email: Regex = "^[\\w+\\-.]+@[\\w\\-.]+\\.[a-z]{2,}$".r
  val phone: Regex = "^\\+?[1-9]\\d{1,14}$".r
  val alphanumeric: Regex = "^[a-zA-Z0-9]+$".r
}

regexMatch(userInput, SafePatterns.email)
```

**3. Validate input size before regex:**
```scala
if (userInput.length > 1000) {
  return ValidationResult.invalid(ValidationError("Input too long"))
}
regexMatch(userInput, safePattern)
```

---

### Issue: Validation errors expose sensitive information

**Problem:** Error messages shown to users contain internal field names, database IDs, or sensitive data.

**Solution: Use valar-translator for user-facing errors:**

```scala
import net.ghoula.valar.translator.Translator

// Define translations that sanitize errors
given Translator with {
  def translate(error: ValidationError): String = {
    error.code match {
      case Some("validation.user.email") => "Please provide a valid email address"
      case Some("validation.user.password") => "Password does not meet requirements"
      case _ => "Validation failed" // Generic message for unknown errors
    }
  }
}

// Translate before showing to users
val result = Validator[User].validate(untrustedInput)
val userFriendlyResult = result.translateErrors()
```

---

## Best Practices

### When should I use fail-fast vs error accumulation?

**Use Error Accumulation (default - `zip`, `mapN`):**
- Form validation (show all errors at once)
- API request validation (return all issues)
- Configuration file validation
- Batch processing with reporting

```scala
// Accumulates all errors
val result = (
  validateName(user.name).zip(
    validateEmail(user.email)
  ).zip(
    validateAge(user.age)
  )
).map { case ((name, email), age) => User(name, email, age) }
```

**Use Fail-Fast (`flatMap`, `zipFailFast`):**
- Performance-critical paths
- Expensive validations (database lookups, API calls)
- Validations with dependencies (later checks need earlier results)
- Security checks (stop processing on first failure)

```scala
// Stops at first error
val result = for {
  name <- validateName(user.name)
  email <- validateEmail(user.email) // Only runs if name valid
  age <- validateAge(user.age)       // Only runs if email valid
} yield User(name, email, age)
```

---

### How do I validate optional fields?

Valar provides automatic `Option` handling:

```scala
case class User(
  name: String,           // Required
  email: Option[String],  // Optional
  age: Option[Int]        // Optional
)

// Define validators for the inner types
given Validator[String] = ... // For both name and email
given Validator[Int] = ...

// Derive automatically - None is always valid, Some(x) validates x
given Validator[User] = Validator.derive

// Explicit option validation
val emailOpt: Option[String] = Some("invalid")
Validator[Option[String]].validate(emailOpt)
// If Some(x), validates x; if None, always Valid(None)
```

For required optionals:
```scala
import net.ghoula.valar.ValidationHelpers.*

val emailOpt: Option[String] = None
required(emailOpt, "Email is required")
// Invalid: Required value must not be empty/null
```

---

### How do I compose validators?

```scala
// Method 1: Sequential composition with flatMap
val composedValidator: Validator[String] with {
  def validate(s: String): ValidationResult[String] = {
    nonEmpty(s).flatMap { str =>
      minLengthValidator(str, 5)()
    }.flatMap { str =>
      regexMatch(str, emailPattern)()
    }
  }
}

// Method 2: Applicative composition with zip
val userValidator: Validator[User] with {
  def validate(user: User): ValidationResult[User] = {
    (validateName(user.name)
      .zip(validateEmail(user.email))
      .zip(validateAge(user.age))
    ).map { case ((name, email), age) => user }
  }
}

// Method 3: Using for-comprehension (fail-fast)
def validateUser(user: User): ValidationResult[User] = for {
  name <- validateName(user.name)
  email <- validateEmail(user.email)
  age <- validateAge(user.age)
} yield user.copy(name = name, email = email, age = age)
```

---

## Still Having Issues?

1. **Check the version:** Ensure you're using the latest version of Valar
2. **Review documentation:** See [README.md](README.md) for comprehensive examples
3. **Report bugs:** [GitHub Issues](https://github.com/hakimjonas/valar/issues)
4. **Migration help:** See [MIGRATION.md](MIGRATION.md) if upgrading from older versions

---

## Quick Reference

| Problem | Quick Fix |
|---------|-----------|
| Missing validator for field | Define `given Validator[FieldType]` before deriving |
| Ambiguous implicits | Use explicit validator or scope imports |
| Empty error vector | Use `fromEitherErrors` or check `errors.nonEmpty` |
| Collection too large | Adjust `ValidationConfig` or pre-validate size |
| Slow validation | Use fail-fast, limits, or parallel validation |
| Stack overflow | Limit nesting depth or use iterative validation |
| ReDoS vulnerability | Never accept user regex patterns |
| Sensitive data in errors | Use `valar-translator` to sanitize messages |

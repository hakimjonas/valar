# Valar Examples

Advanced usage patterns and examples.

## Async Validation

Use `AsyncValidator` when validation requires I/O operations like database lookups or API calls.

```scala
import net.ghoula.valar.*
import scala.concurrent.{Future, ExecutionContext}

case class User(email: String, username: String)

// Simulate async checks
def emailExists(email: String)(using ec: ExecutionContext): Future[Boolean] =
  Future { /* database lookup */ true }

def usernameAvailable(username: String)(using ec: ExecutionContext): Future[Boolean] =
  Future { /* database lookup */ true }

// Define async validators
given ec: ExecutionContext = ExecutionContext.global

given AsyncValidator[String] with {
  def validateAsync(value: String): Future[ValidationResult[String]] =
    Future.successful(
      if (value.nonEmpty) ValidationResult.Valid(value)
      else ValidationResult.invalid(ValidationError("Value must not be empty"))
    )
}

// Custom async validator for User with database checks
given AsyncValidator[User] with {
  def validateAsync(user: User): Future[ValidationResult[User]] = {
    for {
      emailCheck <- emailExists(user.email)
      usernameCheck <- usernameAvailable(user.username)
    } yield {
      val errors = Vector.newBuilder[ValidationError]

      if (emailCheck)
        errors += ValidationError("Email already registered")
          .withFieldPath("email")

      if (!usernameCheck)
        errors += ValidationError("Username not available")
          .withFieldPath("username")

      val errs = errors.result()
      if (errs.isEmpty) ValidationResult.Valid(user)
      else ValidationResult.Invalid(errs)
    }
  }
}

// Usage
val user = User("test@example.com", "newuser")
val resultFuture: Future[ValidationResult[User]] =
  AsyncValidator[User].validateAsync(user)
```

### Mixing Sync and Async

You can derive an `AsyncValidator` that uses synchronous validators for fields:

```scala
case class Registration(user: User, acceptedTerms: Boolean)

// Sync validator for Boolean
given Validator[Boolean] with {
  def validate(value: Boolean): ValidationResult[Boolean] =
    if (value) ValidationResult.Valid(value)
    else ValidationResult.invalid(ValidationError("Must accept terms"))
}

// Derive async validator - uses sync validators wrapped in Future.successful
given AsyncValidator[Registration] = AsyncValidator.derive
```

## Validator Composition

### Sequential Composition (Fail-Fast)

Use `flatMap` or for-comprehensions when later validations depend on earlier ones:

```scala
import net.ghoula.valar.*
import net.ghoula.valar.ValidationHelpers.*

def validatePassword(password: String): ValidationResult[String] = {
  for {
    p <- nonEmpty(password, _ => "Password required")
    p <- minLength(p, 8, _ => "Password must be at least 8 characters")
    p <- if (p.exists(_.isUpper)) ValidationResult.Valid(p)
         else ValidationResult.invalid(ValidationError("Password must contain uppercase"))
    p <- if (p.exists(_.isDigit)) ValidationResult.Valid(p)
         else ValidationResult.invalid(ValidationError("Password must contain a digit"))
  } yield p
}

// Stops at first failure
validatePassword("short")  // Invalid: "Password must be at least 8 characters"
validatePassword("longenoughbutnoupperodigit")  // Invalid: "Password must contain uppercase"
```

### Parallel Composition (Error Accumulation)

Use `zip` to validate independent fields and collect all errors:

```scala
case class RegistrationForm(
  username: String,
  email: String,
  password: String,
  confirmPassword: String
)

def validateForm(form: RegistrationForm): ValidationResult[RegistrationForm] = {
  val usernameResult = nonEmpty(form.username, _ => "Username required")
    .map(_ => form.username)

  val emailResult = nonEmpty(form.email, _ => "Email required")
    .flatMap(e => regexMatch(e, "^[^@]+@[^@]+$".r)(_ => "Invalid email format"))
    .map(_ => form.email)

  val passwordResult = validatePassword(form.password)

  val confirmResult =
    if (form.password == form.confirmPassword) ValidationResult.Valid(form.confirmPassword)
    else ValidationResult.invalid(ValidationError("Passwords do not match"))

  // Combine all validations - accumulates errors
  usernameResult
    .zip(emailResult)
    .zip(passwordResult)
    .zip(confirmResult)
    .map { case (((_, _), _), _) => form }
}
```

### Reusable Validator Combinators

Build validators from smaller pieces:

```scala
object Validators {
  def nonEmptyString: Validator[String] = new Validator[String] {
    def validate(s: String) =
      if (s.trim.nonEmpty) ValidationResult.Valid(s.trim)
      else ValidationResult.invalid(ValidationError("Must not be empty"))
  }

  def inRange(min: Int, max: Int): Validator[Int] = new Validator[Int] {
    def validate(n: Int) =
      if (n >= min && n <= max) ValidationResult.Valid(n)
      else ValidationResult.invalid(
        ValidationError(s"Must be between $min and $max")
          .withExpected(s"$min-$max")
          .withActual(n.toString)
      )
  }

  def matchesRegex(pattern: scala.util.matching.Regex, message: String): Validator[String] =
    new Validator[String] {
      def validate(s: String) =
        if (pattern.matches(s)) ValidationResult.Valid(s)
        else ValidationResult.invalid(ValidationError(message))
    }
}

// Compose into domain validators
object UserValidators {
  val username: Validator[String] = new Validator[String] {
    def validate(s: String) =
      Validators.nonEmptyString.validate(s).flatMap { trimmed =>
        Validators.matchesRegex("^[a-zA-Z0-9_]+$".r, "Username can only contain letters, numbers, and underscores")
          .validate(trimmed)
      }
  }

  val age: Validator[Int] = Validators.inRange(0, 150)
}
```

## Union Types

Valar can validate union types when validators exist for each member:

```scala
// Define validators for each type in the union
given Validator[String] with {
  def validate(s: String) =
    if (s.nonEmpty) ValidationResult.Valid(s)
    else ValidationResult.invalid(ValidationError("String must not be empty"))
}

given Validator[Int] with {
  def validate(n: Int) =
    if (n >= 0) ValidationResult.Valid(n)
    else ValidationResult.invalid(ValidationError("Int must be non-negative"))
}

// Union type validator is derived automatically
val unionValidator = summon[Validator[String | Int]]

unionValidator.validate("hello")  // Valid("hello")
unionValidator.validate(42)       // Valid(42)
unionValidator.validate("")       // Invalid: String must not be empty
unionValidator.validate(-1)       // Invalid: Int must be non-negative
```

### Discriminated Unions with Enums

```scala
enum PaymentMethod {
  case CreditCard(number: String, expiry: String, cvv: String)
  case BankTransfer(iban: String, bic: String)
  case Crypto(walletAddress: String)
}

// Validators for each case
given Validator[PaymentMethod.CreditCard] with {
  def validate(cc: PaymentMethod.CreditCard) = {
    val numberValid = cc.number.length == 16 && cc.number.forall(_.isDigit)
    val cvvValid = cc.cvv.length == 3 && cc.cvv.forall(_.isDigit)

    if (numberValid && cvvValid) ValidationResult.Valid(cc)
    else ValidationResult.invalid(ValidationError("Invalid credit card details"))
  }
}

given Validator[PaymentMethod.BankTransfer] with {
  def validate(bt: PaymentMethod.BankTransfer) = {
    if (bt.iban.nonEmpty && bt.bic.nonEmpty) ValidationResult.Valid(bt)
    else ValidationResult.invalid(ValidationError("IBAN and BIC required"))
  }
}

given Validator[PaymentMethod.Crypto] with {
  def validate(c: PaymentMethod.Crypto) = {
    if (c.walletAddress.startsWith("0x")) ValidationResult.Valid(c)
    else ValidationResult.invalid(ValidationError("Invalid wallet address"))
  }
}

// Validate any payment method
def validatePayment(pm: PaymentMethod): ValidationResult[PaymentMethod] = pm match {
  case cc: PaymentMethod.CreditCard => Validator[PaymentMethod.CreditCard].validate(cc)
  case bt: PaymentMethod.BankTransfer => Validator[PaymentMethod.BankTransfer].validate(bt)
  case c: PaymentMethod.Crypto => Validator[PaymentMethod.Crypto].validate(c)
}
```

## Intersection Types

Intersection types validate when the value satisfies all component validators:

```scala
trait Named { def name: String }
trait Aged { def age: Int }

case class Person(name: String, age: Int) extends Named with Aged

given Validator[Named] with {
  def validate(n: Named) =
    if (n.name.nonEmpty) ValidationResult.Valid(n)
    else ValidationResult.invalid(ValidationError("Name required"))
}

given Validator[Aged] with {
  def validate(a: Aged) =
    if (a.age >= 0) ValidationResult.Valid(a)
    else ValidationResult.invalid(ValidationError("Age must be non-negative"))
}

// For intersection types, both validators must pass
val person = Person("Alice", 30)

// Validate against both traits
val namedResult = Validator[Named].validate(person)
val agedResult = Validator[Aged].validate(person)

// Combine results
val combined = namedResult.zip(agedResult).map { case (_, _) => person }
```

## Nested Case Classes

Valar automatically validates nested structures:

```scala
case class Address(street: String, city: String, postalCode: String)
case class Company(name: String, address: Address)
case class Employee(name: String, company: Company)

// Define validators for leaf types
given Validator[String] with {
  def validate(s: String) =
    if (s.nonEmpty) ValidationResult.Valid(s)
    else ValidationResult.invalid(ValidationError("Must not be empty"))
}

// Derive validators bottom-up
given Validator[Address] = Validator.derive
given Validator[Company] = Validator.derive
given Validator[Employee] = Validator.derive

// Validation errors include full field paths
val invalid = Employee("", Company("", Address("", "", "")))
val result = Validator[Employee].validate(invalid)

// Errors will have paths like:
// - "name"
// - "company.name"
// - "company.address.street"
// - "company.address.city"
// - "company.address.postalCode"
```

## Collection Validation

Collections are validated element by element:

```scala
case class Order(items: List[OrderItem])
case class OrderItem(productId: String, quantity: Int)

given Validator[OrderItem] with {
  def validate(item: OrderItem) = {
    val productValid = item.productId.nonEmpty
    val quantityValid = item.quantity > 0

    if (productValid && quantityValid) ValidationResult.Valid(item)
    else {
      val errors = Vector.newBuilder[ValidationError]
      if (!productValid) errors += ValidationError("Product ID required")
      if (!quantityValid) errors += ValidationError("Quantity must be positive")
      ValidationResult.Invalid(errors.result())
    }
  }
}

given Validator[Order] = Validator.derive

// With ValidationConfig to limit collection size
given ValidationConfig = ValidationConfig.strict  // Max 10,000 items

val order = Order(List(
  OrderItem("", 1),      // Invalid: no product ID
  OrderItem("abc", 0),   // Invalid: quantity not positive
  OrderItem("xyz", 5)    // Valid
))

val result = Validator[Order].validate(order)
// Accumulates errors from all invalid items
```

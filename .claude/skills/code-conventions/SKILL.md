---
name: code-conventions
description: Use Java 21, prefer var for obvious local variables, and avoid unnecessary variables when writing or refactoring Java code.
---

# Java Code Conventions

Use these conventions when writing or refactoring Java code.

## Java Version

Use **Java 21** as the target language version.

Prefer modern Java features.

Examples:

```java
record User(String id, String name) {}
```

## Use `var` When Possible

Use `var` for local variables. The team works in IntelliJ, where inlay hints show the inferred
type, so `var` is preferred even when the type is **not** obvious from the right-hand side.

Good:

```java
var users = userRepository.findAll();
var request = new HttpRequest();
var name = user.getName();
var result = process(data);
```

Prefer `var` over spelling out the declared type for local variables, including `new X(...)`,
factory calls, and casts.

## Avoid Unnecessary Variables

Do not introduce variables that are used only once and do not improve readability.

Not ideal:

```java
var name = user.getName();
return name;
```

Better:

```java
return user.getName();
```

Not ideal:

```java
var response = service.call(request);
return ResponseEntity.ok(response);
```

Better:

```java
return ResponseEntity.ok(service.call(request));
```

Keep intermediate variables when they make the code easier to read, avoid duplicate work, or explain the meaning of a value.

Good:

```java
var isExpired = token.expiresAt().isBefore(Instant.now());

if (isExpired) {
    throw new TokenExpiredException();
}
```

## Text Blocks for Embedded Samples

Use **text blocks** (`"""..."""`) for embedded JSON, XML, or other multi-character message
samples instead of single-line string literals with escaped quotes (`\"`). This applies to
production code and tests alike, and keeps the sample readable as the format it represents.

Avoid:

```java
var customer = "{\"id\": 1, \"name\": \"Alice\"}";
var element = "<customer id=\"1\"><name>Alice</name></customer>";
```

Good:

```java
var customer = """
        {"id": 1, "name": "Alice"}""";

var element = """
        <customer id="1"><name>Alice</name></customer>""";
```

Text blocks may be passed directly as method arguments; a separate local variable is not required.

## Prefer `formatted` Over String Concatenation

When a string *interleaves* text and values — a value in the middle, or more than one value —
build it with `String.formatted(...)` placeholders rather than `+` concatenation. This keeps the
message readable as a single template and applies to production code, log messages, exception
messages, and test assertion messages.

Avoid:

```java
throw new IllegalArgumentException("Part '" + name + "' has type '" + type + "'.");
```

Good:

```java
throw new IllegalArgumentException("Part '%s' has type '%s'.".formatted(name, type));
```

The exception is a **single value appended at the end** of one literal. There, plain `+` reads
better than a template, so prefer it:

```java
throw new IllegalStateException("Cannot parse body: " + e.getMessage());
assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
```

## General Style

Prefer simple, readable code over clever code.

Use meaningful names.

Keep methods short and focused.

Avoid unnecessary comments. Write comments only when they explain why something is done, not what the code already says.

Prefer early returns over deeply nested conditionals.

Good:

```java
if (user == null) {
    return Optional.empty();
}

return Optional.of(user.toDto());
```

## Formatting

Follow the standard Java formatting style used by the project.

Use one statement per line.

Use braces for `if`, `else`, `for`, and `while` blocks, even for single-line bodies.

Good:

```java
if (enabled) {
    start();
}
```

Avoid:

```java
if (enabled) start();
```
 
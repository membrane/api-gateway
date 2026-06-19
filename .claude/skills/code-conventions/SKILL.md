---
name: Java Code Conventions
description: Use Java 21, prefer var for obvious local variables, and avoid unnecessary variables when writing or refactoring Java code.
---

# Java Code Conventions

Use these conventions when writing or refactoring Java code.

## Java Version

Use **Java 21** as the target language version.

Prefer modern Java features where they improve readability.

## Use `var` When Possible

Use `var` for local variables when the type is obvious from the right-hand side.

Good:

```java
var users = userRepository.findAll();
var request = new HttpRequest();
var name = user.getName();
```

## Avoid Unnecessary Variables

Do not introduce variables that are used only once and do not improve readability.

Not ideal:

```java
var response = service.call(request);
return ResponseEntity.ok(response);
```

Better:

```java
return ResponseEntity.ok(service.call(request));
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

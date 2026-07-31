---
name: find-example
description: Find the runnable examples and tutorials that demonstrate a Membrane interceptor or config element, given its XML name (the @MCElement value), e.g. "is there an example for <call>?", "which tutorials use the rewriter?", "show me examples of apiKey". Use whenever the user wants to locate existing examples or tutorials under distribution/examples or distribution/tutorials for a given element / plugin / interceptor, or asks whether one has any. This is the usage-finding counterpart to find-interceptor-impl (which finds the Java class).
---

# Find Examples and Tutorials

Membrane ships runnable demos in two trees:

- `distribution/examples/<category>/<name>/` — a self-contained example, usually
  with a `proxies.xml` and/or `apis.yaml` config plus a `README.md`.
- `distribution/tutorials/<category>/NN-Title.yaml` — numbered, self-teaching
  tutorial steps (one YAML file each).

An element appears in these as a config tag — `<call .../>` in XML or `- call:`
in YAML. This skill greps both trees for that tag and reports where the element
is actually used (not just mentioned in prose). It's the inverse of
`find-interceptor-impl`: that maps a name to its Java class, this maps a name to
its demos.

## Usage

Run the helper with the element name (the `@MCElement(name = "...")` value):

```bash
.claude/skills/find-example/find-example.sh call
```

It lists example **directories** (with the first line of each README as a
summary) and tutorial **files**:

```
Examples using <call>:
  distribution/examples/orchestration/call-get   To provide simpler interfaces ...
  distribution/examples/orchestration/call-post  To automate backend interactions ...

Tutorials using <call>:
  distribution/tutorials/orchestration/20-For-Loop-Call.yaml
  distribution/tutorials/orchestration/40-Authentication-Call.yaml
```

Narrow to one tree when that's all the user wants:

```bash
.claude/skills/find-example/find-example.sh rewriter --tutorials
.claude/skills/find-example/find-example.sh apiKey   --examples
```

## Behaviour to trust

- **Matches config, not prose.** Only `proxies.xml`, `*.yaml` and `*.yml` are
  searched, against both the XML (`<name ...>`) and YAML (`name:`) tag forms, so
  a README that merely names the element won't produce a false hit.
- **Examples are deduped to their directory**; tutorials are listed per file
  (they're individual steps sharing one category directory).
- **No match → exit 3.** Either the element genuinely has no demo, or the name is
  off. Confirm the name with `find-interceptor-impl` and retry.

## When to also point docs at what you find

If you're improving an element's reference docs (see the
`optimize-interceptor-docs` skill), mention any examples/tutorials you find in
the class-level `@description` **prose** — `@see` is dropped by the doc renderer.
Reference the directory (`examples/orchestration`), not a specific file.

# CLAUDE.md

Membrane API Gateway — a lightweight Java API gateway for REST, GraphQL, and legacy SOAP/WSDL
services, configurable in YAML or XML. Upstream: https://github.com/membrane/api-gateway

## Working principles

Behavioral guidelines to reduce common coding mistakes; they compose with the project-specific
conventions below.
**Tradeoff:** these bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think before coding
**Don't assume silently. Surface tradeoffs.**
- State assumptions explicitly rather than picking one silently.
- If multiple reasonable interpretations exist, name them instead of just choosing.
- Ask before proceeding only on judgment calls with real cost to being wrong (data model, auth,
  hard-to-undo changes). For everything else, state your assumption and proceed — don't stall on
  low-stakes ambiguity.
- If a simpler approach exists, say so, even if it means pushing back.

### 2. Simplicity first
**No speculative abstraction.**
- No features, flexibility, or config beyond what was asked.
- No abstractions for single-use code.
- If it could be a third of the length, rewrite it shorter.

### 3. Surgical changes
**Touch only what you must.**
- Don't "improve," reformat, or refactor adjacent code, even if it's messy.
- Match existing style even where you'd choose differently.
- Remove imports/vars/functions your change made unused; leave pre-existing dead code alone
  (mention it, don't touch it).
- Every changed line should trace to the request.

### 4. Verify before declaring done
- For bug fixes: reproduce with a failing test first, then fix.
- For new logic: add tests for the cases that matter (invalid input, edge cases), not exhaustive
  coverage.

### 5. Never push on your own
- Never run `git push` (or open/merge a PR) without the user explicitly asking for it in that
  moment. Committing locally is fine; pushing is not the default next step.

## Modules

Maven multi-module reactor (root `pom.xml`), Java 21 (`javac.source/target`):

- `annot` — the `@MCElement`/`@MCAttribute`/... annotations that drive the config grammar;
  also generates `router-conf.xsd`.
- `core` — the router engine and all built-in interceptors/plugins. Primary library code.
- `distribution` — assembles the runnable `.zip` (`membrane.sh`/`membrane.cmd`, `conf/proxies.xml`,
  `tutorials/`, `examples/`). Also owns the tutorial/example integration tests.
  `distribution/router/conf/` is what actually ships as the zip's `conf/` (see
  `src/assembly/distribution.xml`); `distribution/conf/` is a separate, unpackaged scratch
  directory for the programmer's own local testing — never treat it as shipped config or
  reference it from tutorials/examples/docs.
- `war` — packages `core` for deployment into a servlet container (Tomcat, Jetty).
- `test` — shared test utilities (HTTP client helpers, fixtures), depended on as `test` scope.

## Build

```sh
mvn install -DskipTests          # full build, skip tests
mvn -pl core -am -DskipTests package   # one module + its dependencies
```

## Testing

- **Default to running only the tests affected by a change** — the class(es)/package(s) touched,
  not the whole suite. Never run a full unit test run (`mvn test` / `mvn -pl core -am test`) or
  the full distribution IT suite without asking the user first; these are slow and often fail on
  unrelated/network-dependent tests offline.
- For a single `core` test class or package, use `test/scripts/run-core-test.sh <FQCN or package>`
  — it drives `com.predic8.membrane.devtools.SingleTestRunner` (a permanent JUnit Platform
  Launcher entry point under `core/src/test/java`, so it compiles with the normal test build —
  no per-run codegen) and matches Surefire's `argLine`/CWD so results agree with a real Maven
  run. For a single distribution/tutorial IT, use the `run-example-test` skill.
- **`-Dtest=ClassName` does NOT isolate a class in `core`.** Surefire is bound to
  `UnitTests.java`, a JUnit Platform `@Suite` with `@SelectPackages("com.predic8")` — the suite
  engine ignores Surefire's class filter and runs the whole package regardless (including
  network-dependent tests that fail offline). To run one class fast, use the JUnit Platform
  Launcher directly (build a classpath with `test-classes` first, then `target/classes`, then
  deps) rather than `-Dtest`.
- **Tutorial/example tests are Failsafe ITs that run against the *built* distribution**, not the
  source tree: `DistributionExtractingTestcase` unzips `distribution/target/membrane-api-gateway-*.zip`
  and runs the real `membrane.sh` against it. Rebuild first — `mvn clean install -DskipTests` at
  the repo root — or edits to tutorials/config/`core` are invisible to the test run.
  `-Dit.test=Foo` does **not** filter (the Failsafe entry point `ExampleTests.java` hardcodes
  `@SelectPackages`), so a single-test invocation still runs the whole ~6 min suite. Use the
  `run-example-test` skill for a fast single-test path.
  `-am` also rebuilds `annot`, where `SpringConfigXSDErrorsTest` asserts English `javac`
  diagnostics and fails under a non-English JVM locale — `-DskipTests` avoids that.
- **On macOS, the JVM ignores `LANG`/`LC_ALL`.** If the dev machine's default locale isn't
  English, pass `-Duser.language=en -Duser.country=US` explicitly — and note the child
  `membrane.sh` process spawned by distribution ITs only inherits it via `JAVA_OPTS`, not the
  parent JVM's system properties.
- **Fixed test ports**: `OAuth2Test` (core) and the security tutorial ITs bind `2000`/`3000`/`7007`.
  A manually running Membrane instance or an IDE-launched JVM holding one of these ports causes
  misleading failures (`PortOccupiedException` inside a passing-looking suite, or a bare
  `TimeoutException` from `waitForMembrane()`). Check `lsof -nP -tiTCP:2000 -sTCP:LISTEN` (and
  `7007`) before assuming a config regression.
- Every new function or feature must be covered by at least one test — before writing a new test class, check `<module>/src/test/java/<mirrored package>/` for an existing test class covering that production class and add a test method there; only create a new `<ClassName>Test` class if none exists yet.
- Test observable behavior — inputs/outputs, edge cases (zero/identical values, boundaries), and any documented invariants. Do not write tests for record accessors, generated `equals`/`hashCode`/`toString`, or plain getters/setters — there's no behavior there to break.
- Test classes mirror the package of the class under test (e.g. `com.predic8.membrane.core.util.URLUtil` → `com.predic8.membrane.core.util.URLUtilTest`).
- Prefer a few tests that pin down real behavior (known-value checks, symmetry/round-trip properties) over exhaustive trivial cases.

## Configuration grammar (annotations)

Config elements are Java classes annotated in the `annot` module and rendered into both XML and
YAML. See `docs/DEVELOPING.md` for the full annotation reference
(`@MCElement`, `@MCAttribute`, `@MCChildElement`, `@MCTextContent`, `@MCOtherAttributes`, `@Required`).
Every annotated setter needs a matching getter.

## Reference docs (Javadoc → membrane-api.io)

Class/method Javadoc on `@MCElement` classes is parsed by a custom doc generator, **not**
standard Javadoc rendering:

- Only these block tags are read: `@topic`, `@description`, `@example`, `@default`,
  `@explanation` (deprecated, use `@description`), `@deprecated`, `@yaml`. Plain prose before the
  first tag is silently dropped — everything must live inside a tag.
- `@topic` belongs only on top-level flow/API elements (`component=true`, usable at the top
  level) — not on nested/child-only config elements.
- Class-level `@description` must not describe individual attributes (that belongs in each
  setter's own `@description`); it may name technologies/values in prose.
- Doc examples must cite tutorials (`distribution/tutorials/...`) only — never
  `distribution/examples` (being phased out). Omit the link entirely if no tutorial exists yet.
- Use HTML markup (`<pre><code>...</code></pre>` for code, not `{@code}`), no named HTML
  entities, don't open with an empty tag like `<p/>`.

Use the `optimize-interceptor-docs` skill when writing/polishing these docs, `find-interceptor-impl`
to go from an XML tag name to its Java class, and `find-example`/`create-tutorial` for
example/tutorial discovery and scaffolding.

## Code style

- Don't abbreviate parameter names in public interfaces (private methods: fine) — `docs/CONVENTIONS.md`.
- Prefer `SequencedCollection.getFirst()` over `.get(0)`; custom list-like wrappers (e.g.
  `ValidationErrors`) should expose a delegating `getFirst()`.
- SLF4J everywhere; no `System.out` in production code.
- Attack/validation-detection log lines (e.g. XXE/DOCTYPE detection) are intentionally `info`,
  not `warn` — that's an ops-tunable level, not a severity bug to flag in review.
- Prefer pure methods where practical: same input → same output, minimal side effects; push I/O and mutation to the edges of a call chain.
- Prefer `final` fields, parameters, and locals; a variable that must be reassigned is a signal to extract a helper method instead.
- Treat data as immutable by default — prefer immutable collections (`List.of`, `Collections.unmodifiableList`) or defensive copies over mutating a caller-owned array/collection in place.
- Use Streams only where they read more clearly than an equivalent loop; don't force a stream onto logic a plain loop expresses better.
- Model control flow declaratively where it fits: pattern matching (`switch` over sealed types/records, pattern `instanceof`) over long `if`/`else` chains.
- No hidden side effects in getter-like methods — anything that mutates state, logs, or does I/O should be named and called out explicitly, not buried in a computation.
- Keep methods small and cohesive with a single responsibility; make result and exception behavior explicit — return a value (or `Optional`) for expected outcomes, reserve exceptions for actual failures, and declare checked exceptions rather than swallowing them.

## Git hygiene

Never commit customer-derived artifacts — real customer names, WSDLs, XML, or sample payloads —
even as test fixtures. Generate synthetic equivalents (`example.com`-style) or reuse existing
generic fixtures instead.

## Release notes

When drafting release notes (`release-notes` skill), exclude internal refactors with no
user-visible effect and routine recurring maintenance (doc/javadoc polishing) — readers care
about behavior, config, and capability changes only.

## `docs/SECURITY.md`

The supported-versions table intentionally lists only the current minor line even though older
lines (e.g. 6.5.x) still receive maintenance releases sometimes.

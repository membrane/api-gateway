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
- `war` — packages `core` for deployment into a servlet container (Tomcat, Jetty).
- `test` — shared test utilities (HTTP client helpers, fixtures), depended on as `test` scope.
- `maven-plugin`, `openapi-parser-shaded` — not in the root reactor; build/inspect separately if touched.

## Build

```sh
mvn install -DskipTests          # full build, skip tests
mvn -pl core -am -DskipTests package   # one module + its dependencies
```

## Testing

- Full unit test run: `mvn test`. Single module: `mvn -pl core -am test`.
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
lines (e.g. 6.5.x) still receive maintenance releases — that's a deliberate business decision
steering older-version support toward commercial plans, not a doc bug.

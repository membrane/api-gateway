# CLAUDE.md

Membrane API Gateway — a lightweight Java API gateway for REST, GraphQL, and legacy SOAP/WSDL
services, configurable in YAML or XML. Upstream: https://github.com/membrane/api-gateway

## Git & Commit Policy

- NEVER commit, push, or open/merge a PR unless the user explicitly asks for it in that moment.
  Stage nothing automatically; report what changed and wait.
- Use `Closes #<issue>` in commit messages when the work resolves a filed issue.
- Never commit customer-derived artifacts — real customer names, WSDLs, XML, or sample payloads,
  not even as test fixtures. Generate synthetic equivalents (`example.com`-style) or reuse
  existing generic fixtures.

## Working principles

- Don't refactor, reformat, or "improve" adjacent code, even if it's messy. Remove
  imports/vars/methods your change made unused; leave pre-existing dead code alone (mention it,
  don't touch it).
- For bug fixes: reproduce with a failing test first, then fix.
- If a simpler approach than the one requested exists, say so, even if it means pushing back.

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

- **To run tests, use the `test-runner` agent** — it owns this repo's run mechanics: the
  `-Dtest`/`-Dit.test` suite traps that silently run the wrong scope, `run-core-test.sh` for a
  single `core` class, rebuilding the distribution zip before tutorial/example ITs, macOS locale
  flags, and the fixed-port (2000/3000/7007) conflicts that fake a regression. Don't hand-roll
  the Maven invocation.
- **Run only the tests affected by a change** — the class(es)/package(s) touched, and report pass
  counts. Never run a full unit test run (`mvn test` / `mvn -pl core -am test`) or the full
  distribution IT suite without asking first; these are slow and often fail on
  unrelated/network-dependent tests offline.
- Every new function or feature needs at least one test. Before writing a new test class, check
  `<module>/src/test/java/<mirrored package>/` for an existing test class covering that
  production class and add a method there; only create `<ClassName>Test` if none exists.
- Test classes mirror the package of the class under test (`…core.util.URLUtil` →
  `…core.util.URLUtilTest`) and are package-private, as are `@Test` methods.
- Before running Membrane tests, check that ports 2000/2001/3000/7007/9000 are free
  (`lsof -nP -iTCP:2000,2001,3000,7007,9000 -sTCP:LISTEN`); an IDE-launched Membrane instance
  frequently blocks test runs.
- Integration tests can be flaky for environmental reasons (TIME_WAIT collisions on macOS, accept
  backlog limits). Triage a failure as environmental before changing product code.

## Configuration grammar (annotations)

Config elements are Java classes annotated in the `annot` module and rendered into both XML and
YAML. See `docs/DEVELOPING.md` for the full annotation reference
(`@MCElement`, `@MCAttribute`, `@MCChildElement`, `@MCTextContent`, `@MCOtherAttributes`, `@Required`).
Every annotated setter needs a matching getter.

## YAML `$schema=` version comments

The `# yaml-language-server: $schema=https://www.membrane-api.io/vX.Y.Z.json` header comment on
any `.yaml`/`.yml` file in the repo (not just `distribution/tutorials/` — the release tooling
walks the whole project tree) is rewritten automatically as part of cutting a release
(`ConsistentVersionNumbers.java`, run from the `release-pr.yml` GitHub Action). Don't hand-edit
it, and don't flag a stale/mismatched version as a bug in review.

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

- Interceptors must be thread safe (`Interceptor` javadoc): one instance per config element serves
  every request thread, and `<call>`/internal routing can re-enter it on its own thread. Keep
  per-request state in locals or on the `Exchange`; fields hold configuration, written before
  `init()` and read-only afterwards.
- Don't abbreviate parameter names in public interfaces (private methods: fine) — `docs/CONVENTIONS.md`.
- Prefer `SequencedCollection.getFirst()` over `.get(0)`; custom list-like wrappers (e.g.
  `ValidationErrors`) should expose a delegating `getFirst()`.
- Prefer `final` fields, parameters, and locals, and immutable data over mutating caller-owned
  collections: `List.of` or `List.copyOf` for an immutable snapshot;
  `Collections.unmodifiableList` only as a read-only *view* — it still reflects later changes to
  the backing list, so use it when that live behaviour is intended, not as a defensive copy.
- SLF4J everywhere; no `System.out` in production code.
- Attack/validation-detection log lines (e.g. XXE/DOCTYPE detection) are intentionally `info`,
  not `warn` — that's an ops-tunable level, not a severity bug to flag in review.

## Release notes

When drafting release notes (`release-notes` skill), exclude internal refactors with no
user-visible effect and routine recurring maintenance (doc/javadoc polishing) — readers care
about behavior, config, and capability changes only.

## `docs/SECURITY.md`

The supported-versions table intentionally lists only the current minor line even though older
lines (e.g. 6.5.x) still receive maintenance releases sometimes.

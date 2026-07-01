# SQL-injection rule transpiler

`SqlInjectionProtectionInterceptor` (in the core module) enforces regular-expression rules derived from the
[OWASP Core Rule Set](https://coreruleset.org/) file `REQUEST-942-APPLICATION-ATTACK-SQLI.conf`.

We do **not** interpret the original ModSecurity `SecRules` file at runtime (that would mean reimplementing the
ModSecurity engine, its transformation pipeline and the anomaly-scoring model). Instead the build-time tool
[`CrsSqliRuleTranspiler`](../src/main/java/com/predic8/membrane/build/CrsSqliRuleTranspiler.java) extracts the
`@rx` (plain regex) rules ahead of time into the Membrane-native resource
`core/src/main/resources/com/predic8/membrane/core/interceptor/sqlinjection/crs-sqli-rules.json`.

The transpiler lives in the distribution module's `src/main/java` and is **not** packaged into any Membrane jar
(this module's jar is skipped; only build/test helpers live here).

Intentionally dropped:

- the two `@detectSQLi` rules (942100 / 942101) — these call *libinjection*, for which no maintained,
  suitably-licensed Java port exists. Candidate for a future vendored engine.
- the CRS anomaly-scoring machinery (`setvar tx.*`, blocking evaluation) — the interceptor blocks on the first
  matching rule instead.

Every extracted CRS regex compiles unchanged under `java.util.regex.Pattern`.

## Build integration

The distribution build runs the transpiler in **check** mode (`process-classes` phase). If the committed
`crs-sqli-rules.json` ever drifts from this `.conf`, the build fails — the shipped rules can never silently go
stale.

## Regenerating against a new CRS release

```sh
TAG=v4.0.0   # or main
curl -fsSL \
  "https://raw.githubusercontent.com/coreruleset/coreruleset/$TAG/rules/REQUEST-942-APPLICATION-ATTACK-SQLI.conf" \
  -o distribution/crs-sqli-rules/REQUEST-942-APPLICATION-ATTACK-SQLI.conf

mvn -pl distribution -Pgenerate-sqli-rules process-classes
```

The build locates the CRS file in this directory by the pattern `REQUEST-942*.conf` (not a hard-coded name),
so a CRS file rename needs no build-configuration change. Keep exactly one such file here.

Then review and commit the updated `core/.../crs-sqli-rules.json`. The tool warns on stderr if a rule uses a
transformation not yet implemented in core's `Transformation` enum.

## Source and license

The rules are derived from the [OWASP Core Rule Set](https://github.com/coreruleset/coreruleset):

- Copyright (c) 2006-2020 Trustwave and contributors. All rights reserved.
- Copyright (c) 2021-2026 CRS project. All rights reserved.
- Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

The unmodified source file `REQUEST-942-APPLICATION-ATTACK-SQLI.conf` in this directory retains its upstream
copyright header, which also records the exact CRS version it came from. The generated rules retain their
original CRS rule id and message for attribution, and the bundled-component notice lives in the root
[`LICENSE`](../../LICENSE) file.

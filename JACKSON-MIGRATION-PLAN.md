# Jackson isolation & migration — session handoff (2026-06-27)

Working notes from a session that did two pieces of work and scoped a third. Everything below is
**uncommitted in the working tree, on `master`** (no branch yet). Recommended first action next
time: create a branch and commit Parts A and B (they are independent, complete, and green).

---

## Part A — Shaded Jackson-2 OpenAPI parser  ✅ DONE (uncommitted)

### Why
Goal is to let `core` move to **Jackson 3** later while `swagger-parser` (which is Jackson-2 only)
keeps Jackson 2. Solution: a new module that bundles the parser with **`com.fasterxml.jackson.*`
and `io.swagger.*` relocated** under `com.predic8.membrane.shaded.*`, so the parser's Jackson 2 is
private and cannot clash with whatever Jackson `core` uses.

### What changed
- **NEW module `openapi-parser-shaded/`** (source-less; `pom.xml` only): `maven-shade-plugin`
  bundles `io.swagger:*` + `com.fasterxml.jackson:*` and relocates both under
  `com.predic8.membrane.shaded.*`. Uses `ServicesResourceTransformer` (relocates the SPI files —
  Jackson `Module`/`JsonFactory`, swagger `SwaggerParserExtension`), `createDependencyReducedPom`
  + `promoteTransitiveDependencies` (so shared helper libs like snakeyaml / json-schema-validator
  stay normal deps, un-relocated).
- **Root `pom.xml`**: registered the module in `<modules>`, added it to `<dependencyManagement>`,
  added `maven-shade-plugin` to `<pluginManagement>` (was absent).
- **`core/pom.xml`**: replaced the two `swagger-parser` deps with `service-proxy-openapi-parser-shaded`.
- **~51 core files** (`core/src/{main,test}`): `import io.swagger.…` → `import
  com.predic8.membrane.shaded.io.swagger.…` (whole `openapi` package tree).
- **6 "boundary" files**: where code took a Jackson `ObjectMapper` *out of* swagger
  (`ObjectMapperFactory.createYaml()`) and used it as core's own type. The OpenAPI doc is carried
  as a **core** `JsonNode` (bridged once in `OpenAPIUtil.convert2Json` via swagger's `Json31` →
  bytes → core mapper), so every `omYaml` became `new YAMLMapper()` (core's Jackson):
  `OpenAPIRecordFactory`, `OpenAPIUtil`, `OpenAPIPublisher`, `OpenAPIPublisherInterceptor`,
  + tests `OpenAPITestUtils`, `OpenAPIPublisherInterceptorTest`.
- **`distribution/src/assembly/distribution.xml`**: excluded `io.swagger:*` from `lib/` (the
  reactor's assembly resolves the shaded module's *original* transitives, not the reduced pom, so
  the un-relocated swagger jars would otherwise ship as dead weight alongside the shaded jar).

### Key gotcha that drove the design
`json-schema-validator` (both `com.networknt` and the fge `com.github.java-json-tools`) is used by
`core` **directly** with un-relocated Jackson — so the shade bundles **only** swagger + Jackson and
leaves json-schema-validator shared/un-relocated. A fully self-contained jar (bundling everything +
relocating its Jackson) broke `core`'s direct use of those validators.

### Verified
- 3552 core tests pass (incl. `Swagger20Test`). Full reactor `mvn install` BUILD SUCCESS.
- Shaded jar: all classes under `com/predic8/membrane/shaded/…`, SPI files relocated, no leaks.
- core tree + distribution `lib/`: no un-relocated `io.swagger`; only core's own Jackson 2 visible.

---

## Part B — Retire fge (`com.github.java-json-tools`)  ✅ DONE (uncommitted)

### Why
fge is abandoned (2.2.14, ~2020), Jackson-2-only, draft-04 only. It was an extra Jackson-2 anchor
and dead weight (dragged in `javax.mail:mail:1.5.0-b01`, `libphonenumber`, etc.).

### Where fge was used (now all removed)
1. `core` `JSONSchemaValidator` (used by `KubernetesValidationInterceptor`, `@MCElement
   kubernetesValidation`). **Rewrote its internals fge → networknt**, preserving its exact contract
   (constructor, `List<String>` errors, null-`failureHandler` tolerance) so the k8s interceptor and
   `JSONValidatorError` are untouched. k8s schemas are draft 2020-12 (networknt default).
2. `distribution` test `ConfigSerializationTestYaml` — migrated fge → networknt.
3. Transitively via `swagger-parser-v2-converter → swagger-compat-spec-parser → fge`.

### What changed
- `core/.../schemavalidation/JSONSchemaValidator.java`: fge → networknt
  (`SchemaRegistry.withDefaultDialect(DRAFT_2020_12).getSchema(...).validate(...)`).
- `core/.../schemavalidation/JSONSchemaValidationTest.java`: one assertion `2 → 3` (networknt
  correctly flags all 3 violations where fge's lax draft-04 found 2).
- `distribution/.../ConfigSerializationTestYaml.java`: fge → networknt.
- `openapi-parser-shaded/pom.xml`: **excluded `io.swagger:swagger-compat-spec-parser`** from the v3
  swagger-parser — this was the only thing pulling fge. Swagger/OpenAPI **2.0 still works** via
  `swagger-parser-v2-converter` (confirmed by `Swagger20Test`); only the legacy Swagger **1.x**
  compat-spec validation layer is gone.
- `core/pom.xml`: removed `javax.mail:mail:1.5.0-b01` (existed solely for fge's format library).

### Verified
- 3552 core tests pass (incl. `Swagger20Test`). Full reactor BUILD SUCCESS.
- **0 fge-stack artifacts** on `core` / `war` / `distribution` classpaths and in distribution `lib/`.
- networknt jar (`json-schema-validator-2.0.1.jar`) still present for our validators.

### Capability note to confirm with the team
Dropping `swagger-compat-spec-parser` removes the legacy **Swagger 1.x** compatibility path. 2.0 is
unaffected. Almost certainly fine for Membrane, but flag it.

---

## Part C — Jackson 3 migration  ⏳ NEXT (scoped, not started)

### Feasibility: YES — blockers are cleared
- **Jackson 3.0 GA** since 2025-10-03 (3.1.x out). Production-ready.
- Shade (Part A) isolates the parser's Jackson 2; fge (Part B) removed. Both were blockers.
- **Spring not coupled to Jackson here** (no `MappingJackson*` / `Jackson2ObjectMapperBuilder` in
  core). Spring 6.2 has Jackson 3 support anyway.
- **networknt has a 3.x line on Jackson 3** — our JSON-schema validators move with it.
- **`jackson-annotations` stays `com.fasterxml.jackson.annotation`** in Jackson 3 → the 14
  annotation-only files need **no** import change.

### Scope (the work)
| Surface | Count |
|---|---|
| core main files importing `com.fasterxml.jackson.{core,databind,dataformat,datatype}` → `tools.jackson.*` | ~131 of 145 |
| core test files | 66 |
| `annot` module (YAML config-parsing framework + schema/k8s generators) | 34 |
| distribution | 1 |
| `new ObjectMapper(...)` sites → Jackson 3 construction (`JsonMapper.builder()` / `new JsonMapper()` / `new YAMLMapper(...)`) | 76 (core main) |
| `JsonProcessingException` / `JsonMappingException` sites — now **unchecked** → `throws`/`catch` churn | 71 (core main) |

Dependency changes:
- core jackson `com.fasterxml.jackson:*` → `tools.jackson:*` 3.x (groupId change, same artifactIds).
- **bump networknt 2.0.1 → 3.x** (Jackson 3) — this itself flips `json/JSONYAMLSchemaValidator`,
  `MembraneSchemaLoader`, `JSONSchemaVersionParser`, and the rewritten `JSONSchemaValidator`.
- `jackson-datatype-jsr310` folds into Jackson 3 core (drop the explicit dep — verify).
- `jackson-datatype-joda`: only 2 files (`AcmeRenewal`, `AcmeClient`). Verify a `tools.jackson`
  joda build exists, else drop joda usage.

### Biggest risk
The **`annot` YAML config-parsing framework** (`annot/.../yaml/parsing/GenericYamlParser`, the
`yaml/*` helpers, and `generator/**` schema/k8s generators) — this is how **every** Membrane config
loads. `core` depends on `annot` at runtime, so **annot must migrate first** and be rock-solid.

### Recommended phased plan (each phase gated by build + tests)
1. **annot** → Jackson 3 (YAML framework + generators). Build annot, regenerate k8s/membrane schemas,
   sanity-check config loading.
2. **core main** → Jackson 3 + **networknt 3.x bump**. Handle the 76 `new ObjectMapper()` sites and
   71 exception sites; module registration (jsr310 built-in; joda decision).
3. **core tests** (66 files) + distribution test.
4. **Coexistence check**: confirm the shaded Jackson 2 (`com.predic8.membrane.shaded.com.fasterxml.jackson`)
   and core's Jackson 3 (`tools.jackson`) live side by side with no leakage; run full reactor + a
   distribution OpenAPI example end-to-end.

### Useful references
- Jackson 3 migration guide: https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md
- networknt changelog (2.x = Jackson 2, 3.x = Jackson 3): https://github.com/networknt/json-schema-validator/blob/master/CHANGELOG.md

---

## Open items / decisions for next time
- **Commit Parts A & B**: branch off `master`, commit (two logical commits). Currently uncommitted.
- **Minor hygiene**: `core` uses `com.networknt.schema` directly but gets it transitively via
  `service-proxy-annot` — consider declaring it directly in `core/pom.xml` (pre-existing).
- `ConfigSerializationTestYaml` discovers **0 cases** (it looks for `proxies.yaml`; none exist —
  configs are `apis*.yaml`). Pre-existing and orthogonal to fge, but the test is effectively a no-op
  today; worth fixing separately.
- Confirm the **Swagger 1.x** drop (Part B capability note) is acceptable.
- Pre-existing **German-locale failure**: `annot` `SpringConfigXSDErrorsTest.mcElementNameMissing`
  fails under a German locale (unrelated to this work; blocks `-am` reactor test runs — build deps
  with `-DskipTests`, then run `core` tests separately).

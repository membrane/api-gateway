---
name: membrane-config
description: >-
  Generate a Membrane API Gateway configuration example or snippet — an
  apis.yaml (default) or, when explicitly asked, a legacy proxies.xml. Use this
  whenever the user wants a config, example, or snippet for Membrane: routing a
  port to a backend, a flow with plugins (setHeader, rateLimiter, basicAuthentication,
  openapi, choose/if, static/return, template, validator, oauth2, llmGateway, ...),
  TLS termination, SOAP/REST transformation, or "how do I configure X in Membrane".
  Also trigger for phrases like "membrane config", "apis.yaml example", "proxies.xml",
  "add a plugin to my gateway config", or "show me the YAML for ...". Grounds output
  in the project's real tutorials and verifies it against membrane.schema.json.
---

# Membrane configuration generator

Membrane is configured declaratively. The modern format is `apis.yaml`; a legacy
Spring `proxies.xml` format also exists. This skill produces correct, idiomatic
config by doing what a careful engineer does: copy the shape from a real working
example, confirm exact attribute names against the schema, and validate before
handing it over.

Two sources of truth, and they play different roles:
- **`distribution/tutorials/**/*.yaml`** — idiom and style. Heavily commented,
  curated, and the best examples in the repo. Treat these as gold.
  `distribution/examples/**` adds more (including `proxies.xml`).
- **`membrane.schema.json`** — what's actually allowed: every element, its exact
  attribute names, enum values, and nesting. The model's main failure mode is
  inventing plausible-but-wrong attribute names; the schema is how you avoid that.

Don't generate config from memory alone. The element set is large (~255
elements) and evolves; ground every snippet.

## Workflow

1. **Clarify intent only if needed.** What should the gateway do? Default to one
   `api` on port `2000` forwarding to a `target.url` unless the request implies
   otherwise. Don't over-ask — most requests map cleanly onto a known pattern.

2. **Find a close example.** Skim [references/cheatsheet.md](references/cheatsheet.md)
   for the matching pattern, then look at the real tutorial for the live version:
   ```bash
   grep -rl "rateLimiter:" distribution/tutorials distribution/examples
   ```
   Reading the closest tutorial is the single highest-leverage step — it carries
   ordering, nesting, and conventions you'd otherwise guess at.

3. **Confirm exact attributes against the schema** for every non-trivial element.
   Guessing attribute names is the main way these configs go wrong:
   ```bash
   python3 scripts/describe_element.py rateLimiter   # attributes + enums + children
   python3 scripts/describe_element.py --grep auth   # discover element names
   python3 scripts/describe_element.py --list        # all element ids
   ```

4. **Write the config** following the conventions below.

5. **Validate** (YAML only) — always, before presenting:
   ```bash
   python3 scripts/validate_config.py path/to/apis.yaml
   ```
   Fix every reported problem and re-run until it passes. If you only produced a
   snippet, drop it into a minimal `api:` skeleton in a temp file and validate
   that, so you're confident the snippet is structurally sound.

The scripts find the schema automatically: they prefer the locally built
`core/target/classes/com/predic8/membrane/core/config/json/membrane.schema.json`
and fall back to downloading the published `v7.2.3.json` (cached). They
bootstrap their own dependencies on first run, so just call them.

## Conventions

Match the tutorials so generated config looks native:

- **Schema header.** Start full files with
  `# yaml-language-server: $schema=https://www.membrane-api.io/v7.2.3.json` — it
  gives editors autocompletion and validation.
- **One top-level key per document.** Each YAML document has exactly one root key
  (`api`, `global`, `configuration`, `soapProxy`, `sslProxy`). Separate multiple
  definitions with a `---` line.
- **Default port 2000** (8443 for TLS), matching the tutorials.
- **Comment with purpose.** Tutorials briefly say what a block does and, where
  useful, a `curl` line to try it. Add short comments in that spirit — explain
  intent, don't narrate syntax.
- **Snippet vs. full file.** If the user asked for a snippet to drop into an
  existing config, return just the relevant `flow`/plugin block at the right
  indentation. If they asked for an example or a runnable config, return a
  complete file with the header and a `target` or `return`.

## Output

Present the config in a fenced ```yaml (or ```xml) block. Below it, add a line or
two on what it does and, when it makes the example concrete, how to run and test
it (e.g. `./membrane.sh -c apis.yaml` then a `curl`). State that you validated it
against the schema. If a requested feature isn't in the schema (the local build
can lag the newest features — e.g. some AI plugins), say so and point to the
tutorial you based it on instead of forcing it through validation.

## XML (proxies.xml)

Only when the user explicitly asks for XML / proxies.xml / the Spring format.
Same elements, camelCase tags, attributes instead of nested keys, wrapped in
`<spring:beans>...<router>`. Ground it in a real
`distribution/examples/**/proxies.xml`; see the XML section of
[references/cheatsheet.md](references/cheatsheet.md). `validate_config.py` does
not check XML, so lean harder on matching an existing example.

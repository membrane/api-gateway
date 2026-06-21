---
name: create-tutorial
description: Scaffold a new Membrane API Gateway tutorial in the api-gateway repo — the numbered self-teaching YAML under distribution/tutorials/<category>/, its support files and README links, and the matching auto-discovered integration test. Use whenever the user asks to create, add, write, or scaffold a tutorial (or a tutorial step), even if they only describe the gateway behavior they want to teach and don't say the word "skill" or name the files.
---

# Create a Tutorial

In this repo a *tutorial* is not prose — it is a **runnable, self-teaching YAML config**.
The leading comment block of the file is the lesson; the config below it is the working
example the reader runs. A complete tutorial therefore has three parts, and they must stay
in sync:

1. **The numbered YAML** in `distribution/tutorials/<category>/` — the lesson plus the config.
2. **Category support files** — `membrane.sh`, `membrane.cmd`, a `README.md` (and
   `run-docker.*` where the category offers Docker), so the reader can actually start it.
3. **An integration test** under `distribution/src/test/java/com/predic8/membrane/tutorials/…`
   so the tutorial can't silently rot. These tests unzip the built distribution and start
   Membrane against the tutorial's YAML, so a broken example fails CI.

Work through the steps below in order. The reader of a tutorial follows the file top-to-bottom
with no other docs open, so the single most important thing is that the YAML teaches itself.

## Step 1 — Choose the category and filename

Categories live under `distribution/tutorials/`: `getting-started`, `json`, `xml`, `advanced`,
`transformation`, `security`, `soap`, `orchestration`, `misc`, `ai/...`. Pick the one that fits;
only create a new category if nothing fits (see "New category" at the end).

Files are numbered `NN-Title.yaml` (e.g. `10-PathParameters.yaml`). The number sets reading
order. List the directory and pick the next gap — convention leaves room between numbers
(10, 20, 30…) so steps can be inserted later. Match the existing `PascalCase`-with-hyphens
title style.

## Step 2 — Write the YAML (the lesson lives here)

Open a sibling file in the same category and mirror its shape. Every tutorial YAML has the
same skeleton:

```yaml
# yaml-language-server: $schema=https://www.membrane-api.io/v7.2.3.json
#
# Tutorial: <Human Readable Name>
#
# <One or two sentences on what this teaches and why it matters.>
#
# 1.) Start Membrane:
#     ./membrane.sh -c NN-Title.yaml
#
# 2.) Call the API:
#     curl localhost:2000/...
#
#     <What the reader should see / observe.>
#
# 3.) Continue with file NN-Next.yaml      # the next step in the chain, if any
#
# Troubleshooting:                          # optional, only if a common pitfall exists
#     ...

api:
  port: 2000
  ...
```

Hold to these conventions — they're what makes the set feel like one coherent course:

- **Pin the schema line** to whatever sibling files use (grep one: `grep -m1 schema=
  distribution/tutorials/<category>/*.yaml`). It tracks the current release version, so don't
  invent a number.
- **The comments ARE the tutorial.** Number the steps. A learner should be able to start
  Membrane, make a call, and know what success looks like using only the comments in the file.
- **Keep the config minimal and focused** on the one concept the step teaches. Tutorials favor
  clarity over completeness; richer setups belong under `distribution/examples/`.
- **Port 2000** is the house convention for tutorial listeners unless the lesson needs more.
- If this file continues a chain, end the previous file's comment block with a
  "Continue with file NN-Title.yaml" pointer to the new one.

## Step 3 — Make the category runnable

The reader runs `./membrane.sh -c <file>` from the category directory, so that directory needs
the launcher scripts and a README.

- If `membrane.sh` / `membrane.cmd` are missing from the category dir, copy them verbatim from
  a sibling category — they are identical generic launchers that walk up to find `MEMBRANE_HOME`.
- Copy `run-docker.sh` / `run-docker.cmd` only if the category documents a Docker workflow.
- **`README.md`**: the category README points at the first file in the chain ("open
  [NN-First.yaml](NN-First.yaml) and follow the instructions there"). If you added the first
  file of a new category, write this README modeled on a sibling's.

## Step 4 — Write the integration test (don't skip this)

Without a test the example will drift out of sync with the code and break unnoticed — that's the
whole reason these tests exist. The harness starts Membrane from the **built distribution** with
your YAML and lets you assert against the live gateway.

Test package mirrors the category but with **underscores**: dir `getting-started` →
package `com.predic8.membrane.tutorials.getting_started`. The `ExampleTests` `@Suite` selects the
whole `com.predic8.membrane.tutorials` package, so a test placed correctly is **auto-discovered
— no registration needed.**

Each category has an `Abstract<Category>TutorialTest` that fixes `getTutorialDir()` (this is where
the hyphen dir name is restored). Your concrete test extends it, names the YAML, and asserts:

```java
/* <copy the Apache 2.0 license header verbatim from a sibling test — keep its year> */

package com.predic8.membrane.tutorials.<category_with_underscores>;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class <Name>TutorialTest extends Abstract<Category>TutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "NN-Title.yaml";
    }

    @Test
    void <whatItVerifies>() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/...")
        .then()
            .statusCode(200)
            .body(containsString("..."));
        // @formatter:on
    }
}
```

Notes:

- Copy the license header from an existing sibling test rather than retyping it, so the year and
  wording match the repo.
- Assert the **observable behavior the YAML comments promise** in Step 2 — if the lesson says
  "you should see Customer: 7", assert exactly that. The test is the executable form of the lesson.
- If you created a brand-new category, also create its `Abstract<Category>TutorialTest` modeled on
  an existing one — it just returns the hyphenated dir name from `getTutorialDir()`.

## Step 5 — Verify

Run the test through the existing `run-example-test` skill. Pass `-b` so the distribution is
rebuilt first: the tutorial YAML ships **inside** `distribution/target/*.zip`, and the tests run
from the unzipped distribution, so an un-rebuilt run would test the old YAML (or none).

```bash
.claude/skills/run-example-test/run-example-test.sh -b <Name>TutorialTest
```

A green run means the example actually starts and behaves as the lesson claims.

## New category (only when nothing fits)

1. `distribution/tutorials/<new-category>/` with the first `NN-*.yaml`, `membrane.sh`,
   `membrane.cmd`, and a `README.md`.
2. Add a section linking the new category in `distribution/tutorials/README.md`.
3. Create `com.predic8.membrane.tutorials.<new_category>.Abstract<NewCategory>TutorialTest`
   extending `AbstractMembraneTutorialTest`, returning the hyphenated dir from `getTutorialDir()`.
4. Add concrete tests as in Step 4.

## Checklist

- [ ] `NN-Title.yaml` created with schema line, numbered `# Tutorial:` comment block, minimal config
- [ ] Previous file in the chain points to it ("Continue with…"), if applicable
- [ ] Category has `membrane.sh`, `membrane.cmd`, `README.md` (and `run-docker.*` if used)
- [ ] `<Name>TutorialTest` in the underscore package, extends the category abstract base, has the license header
- [ ] Assertions match the behavior the YAML comments promise
- [ ] `run-example-test.sh -b <Name>TutorialTest` is green

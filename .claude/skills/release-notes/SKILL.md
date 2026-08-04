---
name: release-notes
description: Generate GitHub release notes for the Membrane api-gateway repo by collecting the commits between the last release and master, grouping them into Features / Improvements / Fixes / Security / Dependencies, and linking each to its PR. Use whenever the user wants to draft, extract, or write release notes / a changelog / "what changed since the last release", prepare notes for the next GitHub release, or asks "what's unreleased on master". The user may name a base release (e.g. "since 7.2.3") or let the skill detect the latest one.
---

# Release notes

Produce release notes for the next Membrane release by diffing **master against the last
release** and turning the intervening commits into a clean, grouped, PR-linked changelog —
the kind that goes on the project's GitHub Releases page.

Repo: `membrane/api-gateway`. PR URL pattern: `https://github.com/membrane/api-gateway/pull/<N>`.

## 1. Determine the range: `<base>..master`

The base is the **last released version**. Releases are marked by a commit whose subject is
`Release X.Y.Z (#PR)` (immediately followed by a `Snapshot version` commit). The local tags
lag behind, so detect the base from these commits, not from `git tag`.

- **If the user named a version** (e.g. "since 7.2.3"), use that commit as the base:
  ```bash
  # Note the trailing space — git uses POSIX regex, so `\b` is NOT supported here.
  BASE=$(git log -E --grep="^Release 7\.2\.3 " --format=%H master | head -1)
  ```
- **Otherwise auto-detect the most recent release:**
  ```bash
  git log -E --grep="^Release [0-9]+\.[0-9]+\.[0-9]+ " --format="%H %s" master | head -1
  ```
  Take its hash as `BASE` and read the version from its subject.

The **new version** being released is the pom version minus `-SNAPSHOT`:
```bash
grep -m1 '<version>' pom.xml   # e.g. 7.2.4-SNAPSHOT  ->  7.2.4
```

Sanity-check the range before writing anything:
```bash
git log --oneline $BASE..master | wc -l      # how many commits
git log --pretty="%s" $BASE..master          # the subjects to triage
```
If the range is empty (master == last release), say so and stop.

## 2. Collect and enrich

For each commit on `$BASE..master`, pull the subject and the trailing PR number:
```bash
git log --pretty="%s" $BASE..master
```
Most subjects end in `(#NNNN)` — that's the PR. Turn it into a link
`[#NNNN](https://github.com/membrane/api-gateway/pull/NNNN)`. If a subject has **two** trailing
numbers (e.g. `... (#2988) (#2995)`), the last one is the merge PR — link that one.

Read the subject for meaning; rewrite terse or `type:`-prefixed subjects into a user-facing
sentence. Strip conventional-commit prefixes (`feat:`, `fix:`, `chore:`, `build:`, `refactor:`,
`docs:`) from the displayed text — they only inform which group the entry belongs in.

## 3. Drop the noise

Omit commits that aren't worth a release-note line:
- Release plumbing: `Release X.Y.Z`, `Snapshot version`, version bumps of the project itself.
- Pure test-only changes (`Fix SomeTest`, `Add unit tests for ...`, `Refactor SomeTest`) —
  unless the test change documents a real behavior change worth surfacing.
- Trivial repo chores (`.gitignore`, formatting, typo-only commits, CI tweaks) with no user impact.
- **Internal refactors with no user-visible behavior** — renaming/reshaping a private helper,
  changing an internal method to resolve something differently, tidying internals. If a release
  reader who only writes config and calls the gateway wouldn't notice or care, leave it out.
  (e.g. "`isObjectOrArray` now resolves `$ref` schemas" — an internal helper change — is *not*
  release-note material.)
- **Routine maintenance that recurs every release** — generic doc/javadoc polishing, comment
  cleanups, ongoing tidying. If the line could be copy-pasted into any version's notes, drop it.
  (e.g. "Optimize the javadoc descriptions of interceptors" — happens every version — is *not*
  release-note material.)

When unsure, keep it — but keep it terse. Collapse several related dependency bumps into a single
"Dependencies" bullet rather than one line each.

## 4. Group and write

Group entries by intent, in this order (skip any empty section):

- **New Features** — new plugins/interceptors, new config elements, genuinely new capabilities
  (`feat:`, "Add <feature>", new tutorials/examples that ship new behavior).
- **Improvements** — enhancements & refactors to existing behavior, better logging, perf, docs.
- **Fixes** — bug fixes (`fix:`, "Fix ...").
- **Security** — anything security- or vulnerability-related (`Security fixes`, CVE/dependency
  hardening). Surface these even if small.
- **Dependencies** — dependency/BOM upgrades, collapsed into a few bullets.

Match the house style in [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md): a top-level `#`
version header, optional one-paragraph intro for a notable release, then `##` sections with
`-` bullets. Each bullet is one sentence in plain language with the PR link at the end.

```markdown
# 7.2.4

## New Features
- Add CorrelationId support with examples and tests [#2987](https://github.com/membrane/api-gateway/pull/2987)
- `oauth2client`: handle the client-credentials flow [#2969](https://github.com/membrane/api-gateway/pull/2969)

## Improvements
- OpenAPI validation now covers multipart, XML and form-url-encoded bodies [#2980](https://github.com/membrane/api-gateway/pull/2980)
- Log OpenAPI request/response validation failures [#2982](https://github.com/membrane/api-gateway/pull/2982)

## Fixes
- `ResolverMap.combine`: fix combining paths like `?foo=http://` [#2997](https://github.com/membrane/api-gateway/pull/2997)

## Security
- Reduce dependency vulnerabilities in `pom.xml` [#2973](https://github.com/membrane/api-gateway/pull/2973)
```

## 5. Deliver

By default, **print the notes in the conversation** so the user can paste them into the GitHub
release. The project also keeps per-version files in `distribution/release-notes/<version>.md` —
**only write that file if the user asks** to save it, and use the new version number as the
filename (e.g. `distribution/release-notes/7.2.4.md`).

End with a one-line footer the user can include:
`**Full changelog:** <base-version>...<new-version>` linking
`https://github.com/membrane/api-gateway/compare/v<base>...v<new>`.

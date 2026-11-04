---
name: release-notes
description: Write release notes for the Membrane api-gateway repo. Two jobs. (1) Add a release-note fragment for the current branch or PR to `distribution/release-notes/next/`. (2) Assemble the notes for the next release from those fragments plus the commits since the last release, grouped into Security / New Features / Improvements / Fixes / Breaking Changes / Dependencies with PR links, written to `distribution/release-notes/<version>.md`. Use whenever the user wants to add a release note for a change, draft, collect, or write release notes / a changelog, prepare notes for the next GitHub release, or asks "what's unreleased on master". The user may name a base release (e.g. "since 7.2.3") or let the skill detect the latest one.
---

# Release notes

Release notes for the next release are collected as **fragments**: one small file per change in
`distribution/release-notes/next/`. Each PR adds its own file, so PRs never conflict on a shared
notes file, and no version number is needed until the release.

Before a release, the fragments and the commit history since the last release are merged into
`distribution/release-notes/<version>.md`. That file becomes the body of the GitHub release
(`release-build.yml`), so it must exist before **Pull Request for Release** runs.

Repo: `membrane/api-gateway`. PR URL pattern: `https://github.com/membrane/api-gateway/pull/<N>`.

Decide which job the user wants:
- **Add a fragment** for a change ("add a release note for this", "release note for my PR"): section A.
- **Assemble** the notes for a release ("write the release notes", "what's unreleased"): section B.

## Fragment format

```markdown
---
type: breaking
area: Plugins
---
- `clusterNotification`: `/clustermanager/up`, `/down` and `/takeout` accept only POST. Other methods are answered with `405`. Scripts that call the cluster manager with GET have to use POST. See the migration guide.
```

- File name: a short kebab-case slug of the change, e.g. `cluster-notification-post.md`. Prefix the
  PR or issue number if it is known (`3672-cluster-notification-post.md`).
- `type`: `breaking`, `feature`, `improvement`, `fix`, `security` or `dependencies`.
- `area` (optional): the `###` subsection, e.g. `OpenAPI`, `Plugins`, `WSDL and SOAP`. Reuse an area
  that existing notes already use (`grep -h '^### ' distribution/release-notes/*.md | sort | uniq -c`).
- Body: one or more `-` bullets. A PR or issue link at the end of a bullet is optional; section B
  adds the PR link when it is missing.

## A. Add a fragment

1. Look at what the branch changes for users: `git log --oneline master..HEAD` and
   `git diff master...HEAD --stat`, then the relevant diffs. If nothing is user-visible (see
   section B, step 3), say so and don't write a fragment.
2. Pick `type` and `area`. A change that can break an existing configuration, client or custom
   code is `breaking`, even if it is also a fix.
3. Write the bullets in the house style (see "Style" below).
4. Create `distribution/release-notes/next/` if it does not exist, write the fragment, and show it
   to the user. Don't commit it.

## B. Assemble the notes for a release

### 1. Determine the range: `<base>..master`

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

The **new version** is decided when the release is cut, not by the pom. Ask the user for it unless
they named it. The pom version minus `-SNAPSHOT` (`grep -m1 '<version>' pom.xml`) is only a
suggestion.

If the range is empty (master == last release) and there are no fragments, say so and stop.

### 2. Collect

Two sources, used together:

**Fragments** are the primary source. Their text was written by the author of the change, so keep
it; only fix obvious mistakes and adapt it to the style below.
```bash
ls distribution/release-notes/next/*.md
```
For a fragment without a PR link, find the PR from the commit that added it:
```bash
git log --diff-filter=A --format=%s -- distribution/release-notes/next/<file>.md
```
The `(#NNNN)` at the end of that subject is the PR.

An existing `distribution/release-notes/<version>.md` that holds unreleased notes (written before
fragments were introduced) is a source as well: keep its entries.

**Commits** in `$BASE..master` fill the gaps:
```bash
git log --pretty="%s" $BASE..master
```
Most subjects end in `(#NNNN)` — that's the PR. Turn it into a link
`[#NNNN](https://github.com/membrane/api-gateway/pull/NNNN)`. If a subject has **two** trailing
numbers (e.g. `... (#2988) (#2995)`), the last one is the merge PR — link that one.

A commit whose PR already has a fragment or an existing entry is not skipped automatically: a PR
can change more than its fragment says. Inspect its diff and write an entry for any user-visible
change the fragment or entry does not describe. Skip only what is already described. For all other
commits, read the subject (and the diff if the subject is unclear) and write a user-facing
sentence. Strip conventional-commit prefixes (`feat:`, `fix:`, `chore:`, `build:`, `refactor:`,
`docs:`) from the displayed text — they only inform which group the entry belongs in.

At the end, list for the user the entries that came from commits rather than fragments, so they
can check them: these were written without the author's input.

### 3. Drop the noise

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

### 4. Group and write

Group entries in this order (skip any empty section). A fragment's `type` decides its section:

- **Security** (`security`): anything security- or vulnerability-related. Surface these even if small.
- **New Features** (`feature`): new plugins/interceptors, new config elements, genuinely new
  capabilities.
- **Improvements** (`improvement`): enhancements to existing behavior, better logging, performance.
- **Fixes** (`fix`): bug fixes.
- **Breaking Changes** (`breaking`): anything that can break an existing configuration, client or
  custom code. Name what to do instead, and point to `docs/MIGRATION-GUIDE.md` where it has a section.
- **Dependencies** (`dependencies`): dependency/BOM upgrades, collapsed into a few bullets.

Within a section, group by `area` under `###` headings when the section has entries from more than
one area. Order the areas as the previous release notes do; put new areas after them.

### 5. Deliver

Write `distribution/release-notes/<version>.md` (merge into it if it already exists), delete the
fragments it now contains, and show the result to the user. Don't commit. The user reviews and
commits it before running **Pull Request for Release** (`docs/RELEASE.md`).

If the user only asked what is unreleased, print the notes in the conversation instead and leave
the files alone.

End with a one-line footer the user can include:
`**Full changelog:** <base-version>...<new-version>` linking
`https://github.com/membrane/api-gateway/compare/v<base>...v<new>`.

## Style

Follow `distribution/release-notes/7.8.0.md`: a `# <version>` header, `##` sections, optional `###`
areas, `-` bullets. Each bullet says in plain language what changed for the user and, for breaking
changes, what to do; it ends with the PR link.

```markdown
# 7.9.0

## Fixes

### Plugins
- `prometheus`: the `version` label of `membrane_info` now contains the version as it is, e.g. `7.7.1` [#3686](https://github.com/membrane/api-gateway/pull/3686)

## Breaking Changes

### Plugins
- `clusterNotification`: `/clustermanager/up`, `/down` and `/takeout` accept only POST. Other methods are answered with `405`. See the migration guide [#3672](https://github.com/membrane/api-gateway/pull/3672)
```

#!/usr/bin/env bash
# Find the runnable examples and tutorials that use a Membrane config element,
# by its XML element name (the @MCElement(name = "...") value, e.g. "call").
#
# Searches distribution/examples (proxies.xml + apis.yaml) and
# distribution/tutorials (*.yaml). Reports example directories with their
# README summary, and tutorial files individually.
#
# Usage: find-example.sh <name> [--examples|--tutorials]
#   <name>         the config element name, e.g. "call", "apiKey", "rewriter"
#   --examples     only search distribution/examples
#   --tutorials    only search distribution/tutorials
#
# Exit codes: 0 = at least one usage found, 3 = none found, 2 = bad usage.

set -euo pipefail

name=""
want_examples=1
want_tutorials=1

for arg in "$@"; do
  case "$arg" in
    --examples)  want_tutorials=0 ;;
    --tutorials) want_examples=0 ;;
    -*)          echo "unknown option: $arg" >&2; exit 2 ;;
    *)           [[ -n "$name" ]] && { echo "give exactly one element name" >&2; exit 2; }
                 name="$arg" ;;
  esac
done

if [[ -z "${name// }" ]]; then
  echo "usage: $(basename "$0") <element-name> [--examples|--tutorials]" >&2
  exit 2
fi

# Resolve the repo root so the script works from any cwd.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

# Match the element used as a config tag, in either syntax:
#   XML  : <call ...>  <call/>  <call>
#   YAML : "- call:" or "call:" as a mapping key
xml_re="<${name}[[:space:]/>]"
yaml_re="(^|[[:space:]]|-[[:space:]])${name}:"

# Print files under $1 whose config uses the element. $2 = file glob list.
matching_files() {
  local dir="$1"; shift
  [[ -d "$dir" ]] || return 0
  grep -rElZ --include='proxies.xml' --include='*.yaml' --include='*.yml' \
    -e "$xml_re" "$dir" 2>/dev/null || true
  grep -rElZ --include='proxies.xml' --include='*.yaml' --include='*.yml' \
    -E "$yaml_re" "$dir" 2>/dev/null || true
}

found=0

if [[ $want_examples -eq 1 ]]; then
  # Dedupe to the example directory (parent of the matched config file).
  dirs="$(matching_files "$root/distribution/examples" \
    | tr '\0' '\n' | grep . | xargs -r -n1 dirname | sort -u || true)"
  if [[ -n "$dirs" ]]; then
    found=1
    echo "Examples using <$name>:"
    while IFS= read -r d; do
      rel="${d#"$root"/}"
      desc=""
      if [[ -f "$d/README.md" ]]; then
        # First non-empty, non-heading line of the README.
        desc="$(grep -m1 -vE '^[[:space:]]*(#|$)' "$d/README.md" 2>/dev/null \
          | cut -c1-90 || true)"
      fi
      if [[ -n "$desc" ]]; then
        printf '  %-48s  %s\n' "$rel" "$desc"
      else
        printf '  %s\n' "$rel"
      fi
    done <<< "$dirs"
  fi
fi

if [[ $want_tutorials -eq 1 ]]; then
  files="$(matching_files "$root/distribution/tutorials" \
    | tr '\0' '\n' | grep . | sort -u || true)"
  if [[ -n "$files" ]]; then
    found=1
    [[ $want_examples -eq 1 ]] && echo
    echo "Tutorials using <$name>:"
    while IFS= read -r f; do
      printf '  %s\n' "${f#"$root"/}"
    done <<< "$files"
  fi
fi

if [[ $found -eq 0 ]]; then
  echo "No examples or tutorials use <$name>." >&2
  echo "Check the element name with find-interceptor-impl, or it may have none." >&2
  exit 3
fi

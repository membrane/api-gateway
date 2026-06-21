#!/usr/bin/env bash
# Find the Java implementation class of a Membrane interceptor / config element
# by its XML element name (the @MCElement(name = "...") value).
#
# Usage: find-interceptor-impl.sh <name>
#   <name>  the XML config element name, e.g. "groovy", "log", "apiKey"
#
# Exit codes: 0 = exactly one match, 0 = several matches listed,
#             3 = no exact match (prints fuzzy candidates), 2 = bad usage.

set -euo pipefail

if [[ $# -ne 1 || -z "${1// }" ]]; then
  echo "usage: $(basename "$0") <interceptor-element-name>" >&2
  exit 2
fi

name="$1"

# Search roots: every module's main sources. @MCElement lives across modules
# (core, but also others), so don't hard-code core only.
roots=()
while IFS= read -r d; do roots+=("$d"); done < <(
  find . -type d -path '*/src/main/java' -not -path '*/target/*' 2>/dev/null
)
[[ ${#roots[@]} -eq 0 ]] && roots=(.)

# An @MCElement annotation is virtually always on one line; `name` may appear
# after other attributes (component=, id=, ...), so allow anything before it.
exact_re="@MCElement\([^)]*name *= *\"${name}\""

matches="$(grep -rEln --include='*.java' "$exact_re" "${roots[@]}" 2>/dev/null || true)"

if [[ -n "$matches" ]]; then
  count=$(printf '%s\n' "$matches" | grep -c .)
  if [[ "$count" -eq 1 ]]; then
    echo "Implementation class for <$name>:"
  else
    echo "$count classes declare name=\"$name\" (overrides/variants across modules):"
  fi
  while IFS= read -r f; do
    cls="$(grep -oE 'public( abstract| final)? class [A-Za-z0-9_]+' "$f" | head -1 | awk '{print $NF}')"
    echo "  ${cls:-?}  ->  $f"
  done <<< "$matches"
  exit 0
fi

# No exact match: offer the closest element names so the caller can correct a typo.
# Collect every declared name once, then try a substring match; if that finds
# nothing (e.g. a dropped letter), fall back to ever-shorter prefixes of the query.
all_names="$(grep -rhoE '@MCElement\([^)]*name *= *"[A-Za-z0-9]+"' --include='*.java' "${roots[@]}" 2>/dev/null \
  | grep -oE '"[A-Za-z0-9]+"' | tr -d '"' | sort -u)"

suggest=""
for q in "$name" "${name:0:4}" "${name:0:3}"; do
  [[ -z "$q" ]] && continue
  suggest="$(printf '%s\n' "$all_names" | grep -i "$q" || true)"
  [[ -n "$suggest" ]] && break
done

echo "No @MCElement named \"$name\" found." >&2
if [[ -n "$suggest" ]]; then
  echo "Did you mean one of these?" >&2
  printf '%s\n' "$suggest" | sed 's/^/  /' >&2
fi
exit 3

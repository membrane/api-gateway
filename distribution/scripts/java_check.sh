#!/bin/sh

required="${MEMBRANE_REQUIRED_JAVA_VERSION:-21}"
export MEMBRANE_REQUIRED_JAVA_VERSION="$required"

if ! command -v java >/dev/null 2>&1; then
  echo "Java is not installed. Membrane needs at least Java $required." >&2
  exit 1
fi

# Resolve script dir and call starter.jar one level up
script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec java -jar "$script_dir/../starter.jar" "$@"
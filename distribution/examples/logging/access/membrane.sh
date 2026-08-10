#!/bin/sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

JAVA_OPTS="-Dlog4j.configurationFile=${SCRIPT_DIR}/log4j2_access.xml ${JAVA_OPTS:-}"
export JAVA_OPTS

dir="$SCRIPT_DIR"
while [ "$dir" != "/" ]; do
  if [ -f "$dir/LICENSE.txt" ] && [ -f "$dir/scripts/run-membrane.sh" ]; then
    export MEMBRANE_HOME="$dir"
    export MEMBRANE_CALLER_DIR="$SCRIPT_DIR"
    exec sh "$dir/scripts/run-membrane.sh" "$@"
  fi
  dir=$(dirname "$dir")
done

echo "Could not locate Membrane root. Ensure directory structure is correct." >&2
exit 1

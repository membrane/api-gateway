#!/bin/sh
# Default config: uses proxies.xml next to this script; if missing → $MEMBRANE_HOME/conf/proxies.xml.
# JAVA_OPTS paths: use absolute paths, or prefix relative ones with $MEMBRANE_CONFIG_DIR (set below).
# Example: export JAVA_OPTS="-Xmx1g -Dlog4j.configurationFile=$MEMBRANE_CONFIG_DIR/logging/access/log4j2_access.xml"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
export MEMBRANE_CONFIG_DIR="$SCRIPT_DIR"

dir="$SCRIPT_DIR"
while [ "$dir" != "/" ]; do
  if [ -f "$dir/starter.jar" ] && [ -f "$dir/scripts/run-membrane.sh" ]; then
    exec sh "$dir/scripts/run-membrane.sh" "$@"
  fi
  dir=$(dirname "$dir")
done

echo "Could not locate Membrane root. Ensure directory structure is correct." >&2
exit 1

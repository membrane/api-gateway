#!/bin/sh
# Default config: ./proxies.xml (next to this script); fallback -> $MEMBRANE_HOME/conf/proxies.xml
# JAVA_OPTS: relative -D paths are resolved against $MEMBRANE_HOME.
# Examples:
#   export JAVA_OPTS='-Dlog4j.configurationFile=examples/logging/access/log4j2_access.xml'
#   ./membrane.sh -c path/to/proxies.xml

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

dir="$SCRIPT_DIR"
while [ "$dir" != "/" ]; do
  if [ -f "$dir/starter.jar" ] && [ -f "$dir/scripts/run-membrane.sh" ]; then
    export MEMBRANE_HOME="$dir"
    export MEMBRANE_CALLER_DIR="$SCRIPT_DIR"
    exec sh "$dir/scripts/run-membrane.sh" "$@"
  fi
  dir=$(dirname "$dir")
done

echo "Could not locate Membrane root. Ensure directory structure is correct." >&2
exit 1
#!/bin/sh
SCRIPTS_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
[ -n "${MEMBRANE_HOME:-}" ] && [ -f "$MEMBRANE_HOME/LICENSE.txt" ] || MEMBRANE_HOME=$(cd "$SCRIPTS_DIR/.." && pwd -P)
export MEMBRANE_HOME
: "${MEMBRANE_REQUIRED_JAVA_VERSION:=21}"
sh "$SCRIPTS_DIR/java_check.sh" || exit 1
exec sh "$SCRIPTS_DIR/start_router.sh" "$@"

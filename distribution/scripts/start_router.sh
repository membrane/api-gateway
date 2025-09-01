#!/bin/sh
[ -n "${MEMBRANE_HOME:-}" ] || { echo "start_router.sh: MEMBRANE_HOME not set" >&2; exit 1; }

CLASSPATH="$MEMBRANE_HOME/conf:$MEMBRANE_HOME/lib/*"

if [ "$#" -eq 0 ]; then
  if [ -n "${MEMBRANE_CONFIG_DIR:-}" ] && [ -f "$MEMBRANE_CONFIG_DIR/proxies.xml" ]; then
    set -- -c "$MEMBRANE_CONFIG_DIR/proxies.xml"
  elif [ -f "$MEMBRANE_HOME/conf/proxies.xml" ]; then
    set -- -c "$MEMBRANE_HOME/conf/proxies.xml"
  fi
fi

java ${JAVA_OPTS:-} -cp "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI "$@"
s=$?
[ $s -ne 0 ] && {
  echo "Membrane terminated with exit code $s" >&2
  echo "MEMBRANE_HOME: $MEMBRANE_HOME" >&2
  echo "CLASSPATH: $CLASSPATH" >&2
}
exit $s

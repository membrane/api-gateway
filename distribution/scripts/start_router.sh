#!/bin/sh
[ -n "${MEMBRANE_HOME:-}" ] || { echo "start_router.sh: MEMBRANE_HOME not set" >&2; exit 1; }

CLASSPATH="$MEMBRANE_HOME/conf:$MEMBRANE_HOME/lib/*"

if [ $# -eq 0 ]; then
  CONFIG_FOUND=""

  # Prio 1: apis.yaml|apis.yml in caller dir
  if [ -n "${MEMBRANE_CALLER_DIR:-}" ] && [ -f "$MEMBRANE_CALLER_DIR/apis.yaml" ]; then
    set -- -c "$MEMBRANE_CALLER_DIR/apis.yaml" "$@"
    CONFIG_FOUND=1
  elif [ -n "${MEMBRANE_CALLER_DIR:-}" ] && [ -f "$MEMBRANE_CALLER_DIR/apis.yml" ]; then
    set -- -c "$MEMBRANE_CALLER_DIR/apis.yml" "$@"
    CONFIG_FOUND=1

  # Prio 2: proxies.xml in caller dir
  elif [ -n "${MEMBRANE_CALLER_DIR:-}" ] && [ -f "$MEMBRANE_CALLER_DIR/proxies.xml" ]; then
    set -- -c "$MEMBRANE_CALLER_DIR/proxies.xml" "$@"
    CONFIG_FOUND=1

  # Prio 3: apis.yaml|yml in MEMBRANE_HOME
  elif [ -f "$MEMBRANE_HOME/conf/apis.yaml" ]; then
    set -- -c "$MEMBRANE_HOME/conf/apis.yaml" "$@"
    CONFIG_FOUND=1
  elif [ -f "$MEMBRANE_HOME/conf/apis.yml" ]; then
    set -- -c "$MEMBRANE_HOME/conf/apis.yml" "$@"
    CONFIG_FOUND=1

  # Prio 4: proxies.xml in MEMBRANE_HOME
  elif [ -f "$MEMBRANE_HOME/conf/proxies.xml" ]; then
    set -- -c "$MEMBRANE_HOME/conf/proxies.xml" "$@"
    CONFIG_FOUND=1
  fi

  if [ -z "$CONFIG_FOUND" ]; then
    echo "No configuration file found (apis.yaml, apis.yml or proxies.xml). Provide one of these or use -c <file>." >&2
    exit 1
  fi
fi


normalize_java_opts() {
  [ -n "${JAVA_OPTS:-}" ] || return 0

  set -- $JAVA_OPTS
  NEW_OPTS=
  for tok in "$@"; do
    case "$tok" in
      -D*=*)
        key=${tok%%=*}
        val=${tok#*=}
        case "$val" in
          /*|~/*|[A-Za-z]:/*|\\\\*|file:*|*://*) : ;;
          *) val="$MEMBRANE_HOME/$val" ;;
        esac
        tok="$key=$val"
        ;;
    esac
    NEW_OPTS="$NEW_OPTS $tok"
  done
  JAVA_OPTS=$NEW_OPTS
}
normalize_java_opts

java ${JAVA_OPTS:-} -cp "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI "$@"
s=$?
[ $s -ne 0 ] && {
  echo "Membrane terminated with exit code $s" >&2
  echo "MEMBRANE_HOME: $MEMBRANE_HOME" >&2
  echo "CLASSPATH: $CLASSPATH" >&2
}
exit $s

if [ -z "${MEMBRANE_HOME:-}" ]; then
  echo "start_router.sh: MEMBRANE_HOME not set" >&2
  exit 1
fi

CLASSPATH="$MEMBRANE_HOME/conf:$MEMBRANE_HOME/lib/*"

if [ "$#" -eq 0 ] && [ -f "proxies.xml" ]; then
  set -- -c "proxies.xml"
fi

java ${JAVA_OPTS:-} -cp "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI "$@"
status=$?
if [ $status -ne 0 ]; then
  echo "Membrane terminated with exit code $status" >&2
  echo "MEMBRANE_HOME: $MEMBRANE_HOME" >&2
  echo "CLASSPATH: $CLASSPATH" >&2
fi
exit $status

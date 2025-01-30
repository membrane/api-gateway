#!/bin/sh

required_version="21"

start() {
    membrane_home="$1"
    shift
    CLASSPATH="$membrane_home/conf:$membrane_home/lib/*"
    echo "Membrane Router running..."
    exec java $JAVA_OPTS -classpath "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI "$@"
}

resolve_membrane_home() {
    PRG="$1"

    # Resolve symlinks
    while [ -h "$PRG" ]; do
        ls=$(ls -ld "$PRG")
        link=$(expr "$ls" : '.*-> \(.*\)$')
        if expr "$link" : '/.*' > /dev/null; then
            PRG="$link"
        else
            PRG="$(dirname "$PRG")/$link"
        fi
    done

    saveddir=$(pwd)
    MEMBRANE_HOME=$(dirname "$PRG")
    MEMBRANE_HOME=$(cd "$MEMBRANE_HOME" && pwd)
    cd "$saveddir"

    echo "$MEMBRANE_HOME"
}

if ! ( _test=test && _="${_test#t}" ) >/dev/null 2>&1; then
    echo "WARNING: Shell does not support parameter expansion. Java version check disabled!" >&2
    echo "         Please ensure Java $required_version is installed." >&2
    MEMBRANE_HOME=$(resolve_membrane_home "$0")
    start "$MEMBRANE_HOME" "$@"
    exit 0
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Java is not installed. Membrane needs at least Java $required_version."
    exit 1
fi

version_line=$(java -version 2>&1 | grep "version" | head -n 1)

if [ -z "$version_line" ]; then
    echo "WARNING: Could not determine Java version. Make sure Java version is at least $required_version. Proceeding anyway..."
    MEMBRANE_HOME=$(resolve_membrane_home "$0")
    start "$MEMBRANE_HOME" "$@"
    exit 0
fi

full_version=${version_line#*version \"}
full_version=${full_version%%\"*}
current_version=${full_version%%.*}

case "$current_version" in
    ''|*[!0-9]*)
        echo "WARNING: Could not parse Java version. Make sure Java version is at least $required_version. Proceeding anyway..."
        MEMBRANE_HOME=$(resolve_membrane_home "$0")
        start "$MEMBRANE_HOME" "$@"
        exit 0
        ;;
esac

if [ "$current_version" -ge "$required_version" ]; then
    MEMBRANE_HOME=$(resolve_membrane_home "$0")
    start "$MEMBRANE_HOME" "$@"
    exit 0
else
    echo "Java version mismatch: Required=$required_version, Installed=$full_version"
    exit 1
fi
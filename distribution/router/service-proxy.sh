#!/bin/sh

required_version="21"

start() {
    membrane_home="$1"
    export CLASSPATH="$membrane_home/conf:$membrane_home/lib/*"
    echo "Starting: $membrane_home CL: $CLASSPATH"
    java -cp "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI
}

find_membrane_directory() {
    current="$1"

    while [ "$current" != "/" ]; do
        if [ -d "$current/conf" ] && [ -d "$current/lib" ]; then
            echo "$current"
            return 0
        fi
        current=$(dirname "$current")
    done

    return 1
}

start_membrane() {
    membrane_home=$(find_membrane_directory "$(pwd)")
    if [ $? -eq 0 ]; then
        start "$membrane_home"
    else
        echo "Could not start Membrane. Ensure the directory structure is correct."
    fi
}

if ! ( _test=test && _="${_test#t}" ) >/dev/null 2>&1; then
    echo "WARNING: Shell does not support parameter expansion. Java version check disabled!" >&2
    echo "         Please ensure Java $required_version is installed." >&2
    start_membrane
    exit 0
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Java is not installed. Membrane needs at least Java $required_version."
    exit 1
fi

version_line=$(java -version 2>&1 | grep "version" | head -n 1)

if [ -z "$version_line" ]; then
    echo "WARNING: Could not determine Java version. Make sure Java version is at least $required_version. Proceeding anyway..."
    start_membrane
    exit 0
fi

full_version=${version_line#*version \"}
full_version=${full_version%%\"*}
current_version=${full_version%%.*}

case "$current_version" in
    ''|*[!0-9]*)
        echo "WARNING: Could not parse Java version. Make sure Java version is at least $required_version. Proceeding anyway..."
        start_membrane
        exit 0
        ;;
esac

if [ "$current_version" -ge "$required_version" ]; then
    start_membrane
    exit 0
else
    echo "Java version mismatch: Required=$required_version, Installed=$full_version"
    exit 1
fi
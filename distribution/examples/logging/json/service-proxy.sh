#!/bin/sh

start() {
    membrane_home="$1"
    export CLASSPATH="$membrane_home/conf:$membrane_home/lib/*"
    echo "Starting: $membrane_home CL: $CLASSPATH"
    java -Dlog4j.configurationFile=$(pwd)/log4j2_json.xml -Dlog4j.debug=true  -cp "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI -c proxies.xml
    exit_code=$?
    if [ $exit_code -ne 0 ]; then
        echo "Membrane exited with error. Make sure Java 17 or higher is installed."
        exit $exit_code
    fi
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
        exit 1
    fi
}

if ! java -version >/dev/null 2>&1; then
    echo "Java is not installed or not working. Membrane needs at least Java 17."
    exit 1
fi

start_membrane
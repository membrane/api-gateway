#!/bin/sh

start() {
    membrane_home="$1"
    export CLASSPATH="$membrane_home/conf:$membrane_home/lib/*"
    echo "Starting: $membrane_home CL: $CLASSPATH"
    java -cp "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI -c proxies.xml
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

if [ -n "$MEMBRANE_HOME" ]; then
    start "$MEMBRANE_HOME"
else
    membrane_home=$(find_membrane_directory "$(pwd)")
    if [ $? -eq 0 ]; then
        start "$membrane_home"
    else
        echo "Could not find start Membrane. Set the MEMBRANE_HOME env variable."
    fi
fi

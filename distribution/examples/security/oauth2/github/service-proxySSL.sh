#!/bin/bash

find_membrane_directory() {
    candidate=${MEMBRANE_HOME:-$membrane_home}
    if [ -n "$candidate" ] && [ -f "$candidate/LICENSE.txt" ]; then
        echo "$candidate"
        return 0
    fi

    current="${1:-$(pwd)}"
    while [ "$current" != "/" ]; do
        if [ -f "$current/LICENSE.txt" ]; then
            echo "$current"
            return 0
        fi
        current=$(dirname "$current")
    done

    return 1
}

homeSet() {
    echo "MEMBRANE_HOME variable is now set"
    CLASSPATH="$MEMBRANE_HOME/conf:$MEMBRANE_HOME/starter.jar:$MEMBRANE_HOME/lib/*"
    export CLASSPATH
    echo "Membrane Router running..."
    java -classpath "$CLASSPATH" com.predic8.membrane.core.Starter -c proxiesSSL.xml
}


terminate() {
    echo "Starting of Membrane Router failed."
    echo "Please execute this script from the appropriate subfolder of MEMBRANE_HOME/examples/"
    exit 1
}

if ! MEMBRANE_HOME=$(find_membrane_directory "$(pwd)"); then
    echo "MEMBRANE_HOME variable is not set and could not be auto-detected."
    terminate
fi

export MEMBRANE_HOME
homeSet

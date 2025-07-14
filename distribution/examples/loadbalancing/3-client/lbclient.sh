#!/bin/bash
ARGUMENTS="$@"

homeSet() {
    echo "MEMBRANE_HOME is now set to $MEMBRANE_HOME"
    if [ -n "$JAVA_HOME" ]; then
        exec java -cp "${JAVA_HOME}/jre/lib/ext/*:${MEMBRANE_HOME}/lib/*" \
             com.predic8.membrane.balancer.client.LBNotificationClient $ARGUMENTS
    else
        echo "Please set the JAVA_HOME environment variable."
        exit 1
    fi
}

terminate() {
	echo "Starting of Membrane Router Load Balancer Client failed."
	echo "Please execute this script from the MEMBRANE_HOME/examples/loadbalancer-client-2 directory"
	exit 1
}

find_membrane_directory() {
    candidate=${MEMBRANE_HOME:-$membrane_home}
    if [ -n "$candidate" ] && [ -f "$candidate/starter.jar" ]; then
        echo "$candidate"
        return 0
    fi

    current="${1:-$(pwd)}"
    while [ "$current" != "/" ]; do
        if [ -f "$current/starter.jar" ]; then
            echo "$current"
            return 0
        fi
        current=$(dirname "$current")
    done
    return 1
}

if ! MEMBRANE_HOME=$(find_membrane_directory "$(pwd)"); then
    echo "MEMBRANE_HOME variable is not set and could not be auto-detected."
    terminate
fi

homeSet


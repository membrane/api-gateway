#!/bin/sh

start() {
    membrane_home="$1"
    config_file="$2"
    export CLASSPATH="$membrane_home/conf:$membrane_home/lib/*"
    echo "Starting: $membrane_home CL: $CLASSPATH with config: $config_file"
    java -cp "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI -c "$config_file"
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

script_dir=$(dirname "$0")
default_config="$script_dir/proxies.xml"
specified_config=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        -c)
            shift
            specified_config="$1"
            ;;
    esac
    shift
done

if [ -n "$specified_config" ]; then
    config_file="$specified_config"
else
    config_file="$default_config"
fi

if [ -n "$MEMBRANE_HOME" ]; then
    start "$MEMBRANE_HOME" "$config_file"
else
    membrane_home=$(find_membrane_directory "$(pwd)")
    if [ $? -eq 0 ]; then
        start "$membrane_home" "$config_file"
    else
        echo "Could not start Membrane. Set the MEMBRANE_HOME env variable."
    fi
fi

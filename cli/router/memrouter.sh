#!/bin/bash
abspath() {
    { [[ "$1" =~ ^/ ]] && echo "$1" || echo "$(pwd)/$1"; } | sed -r ':. s#(/|^)\./#\1#g; t .; :: s#[^/]{1,}/\.\./##; t :'
}
MEMBRANE_HOME="$(dirname $(abspath $0))"
CLASSPATH="$MEMBRANE_HOME/conf"
CLASSPATH="$CLASSPATH:$MEMBRANE_HOME/starter.jar"
export CLASSPATH
cd "$MEMBRANE_HOME"
echo Membrane Router running...
java  -classpath "$CLASSPATH" com.predic8.membrane.core.Starter "$@"

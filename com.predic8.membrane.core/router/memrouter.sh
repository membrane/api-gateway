#!/bin/bash
MEMBRANE_HOME="$(dirname $0)"
export MEMBRANE_HOME
CLASSPATH="$MEMBRANE_HOME/conf"
CLASSPATH="$CLASSPATH:$MEMBRANE_HOME/starter.jar"
export CLASSPATH
cd "$MEMBRANE_HOME"
echo Membrane Router running...
java  -classpath "$CLASSPATH" com.predic8.membrane.core.Starter "$@"

#!/bin/bash

## resolve links - $0 may be a link to Membrane's home
PRG="$0"

# need this for relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG="`dirname "$PRG"`/$link"
  fi
done

saveddir=`pwd`

MEMBRANE_HOME=`dirname "$PRG"`

# make it fully qualified
export MEMBRANE_HOME=`cd "$MEMBRANE_HOME" && pwd`

cd "$saveddir"
# echo Using Membrane at $MEMBRANE_HOME

CLASSPATH="$MEMBRANE_HOME/conf"
CLASSPATH="$CLASSPATH:$MEMBRANE_HOME/starter.jar"
export CLASSPATH
cd "$MEMBRANE_HOME"
echo Membrane Router running...
java -Dlogback.configurationFile="$MEMBRANE_HOME/conf/logback.xml" -classpath "$CLASSPATH" com.predic8.membrane.core.Starter "$@"

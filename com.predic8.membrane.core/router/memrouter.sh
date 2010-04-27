MEMROUTER_HOME=/tmp/membrane-router-1.4.0
CLASSPATH=$MEMROUTER_HOME/conf
CLASSPATH=$CLASSPATH:$MEMROUTER_HOME/lib/stax2-api-3.0.1.jar
CLASSPATH=$CLASSPATH:$MEMROUTER_HOME/lib/stax-api-1.0.1.jar
CLASSPATH=$CLASSPATH:$MEMROUTER_HOME/lib/woodstox-core-asl-4.0.5.jar
CLASSPATH=$CLASSPATH:$MEMROUTER_HOME/starter.jar
export CLASSPATH
echo $CLASSPATH
java  -classpath $CLASSPATH com.predic8.membrane.core.Starter $1 $2 $3 $4 $5 $6 $7 $8 $9 $10

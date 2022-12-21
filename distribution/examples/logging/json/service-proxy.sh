#!/bin/bash
homeSet() {
 echo "MEMBRANE_HOME variable is now set"
 CLASSPATH="$MEMBRANE_HOME/conf"
 CLASSPATH="$CLASSPATH:$MEMBRANE_HOME/starter.jar"
 export CLASSPATH
 echo Membrane Router running...
 java -Dlog4j.configurationFile=$(pwd)/log4j2_json.xml  -Dlog4j.debug=true -classpath "$CLASSPATH" com.predic8.membrane.core.Starter -c proxies.xml
 
}

terminate() {
	echo "Starting of Membrane Router failed."
	echo "Please execute this script from the appropriate subfolder of MEMBRANE_HOME/examples/"
	
}

homeNotSet() {
  echo "MEMBRANE_HOME variable is not set"

  if [ -f  "`pwd`/../../starter.jar" ]
    then 
    	export MEMBRANE_HOME="`pwd`/../.."
    	homeSet	
    else
    	terminate    
  fi 
}


if  [ "$MEMBRANE_HOME" ]  
	then homeSet
	else homeNotSet
fi


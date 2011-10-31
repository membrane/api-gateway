#!bin/bash
homeSet() {
 echo "MEMBRANE_HOME variable is now set"
 CLASSPATH="$MEMBRANE_HOME/conf"
 CLASSPATH="$CLASSPATH:$MEMBRANE_HOME/lib/membrane-router.jar:$MEMBRANE_HOME/lib/commons-cli-1.1.jar:$MEMBRANE_HOME/lib/commons-logging.jar:$MEMBRANE_HOME/lib/xmlbeautifier-1.2.1.jar:$MEMBRANE_HOME/lib/commons-codec-1.3.jar"
 export CLASSPATH
 java  -classpath "$CLASSPATH" com.predic8.membrane.balancer.client.LBNotificationClient "$@"
 
}

terminate() {
	echo "Starting of Membrane Router Load Balancer Client failed."
	echo "Please execute this script from the MEMBRANE_HOME/examples/loadbalancer-client-2 directory"
	
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


if  [ "${MEMBRANE_HOME}" ]  
	then homeSet
	else homeNotSet
fi


#!/bin/bash
ARGUMENTS="$@"

homeSet() {
	echo "MEMBRANE_HOME variable is now set"
	if [ "$JAVA_HOME" ] ; then
		exec java -cp "${JAVA_HOME}/jre/lib/ext/*:${MEMBRANE_HOME}/lib/*" com.predic8.membrane.balancer.client.LBNotificationClient $ARGUMENTS
	else
		echo "Please set the JAVA_HOME environment variable."
	fi
}

terminate() {
	echo "Starting of Membrane Router Load Balancer Client failed."
	echo "Please execute this script from the MEMBRANE_HOME/examples/loadbalancer-client-2 directory"
	exit 1
}

homeNotSet() {
	echo "MEMBRANE_HOME variable is not set"

	if [ -f  "`pwd`/../../../starter.jar" ] ; then 
		export MEMBRANE_HOME="`pwd`/../.."
		homeSet
	else
		terminate
	fi
}


if  [ "${MEMBRANE_HOME}" ] ; then
	homeSet
else
	homeNotSet
fi


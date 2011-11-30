@echo off
if not "%MEMBRANE_HOME%" == "" goto homeSet
set "MEMBRANE_HOME=%cd%\..\.."
echo "%MEMBRANE_HOME%"
if exist "%MEMBRANE_HOME%\memrouter.bat" goto homeOk

:homeSet
if exist "%MEMBRANE_HOME%\memrouter.bat" goto homeOk
echo Please set the MEMBRANE_HOME environment variable to point to
echo the directory where you have extracted the Membrane software.
exit

:homeOk
set "CLASSPATH=%MEMBRANE_HOME%"
set "CLASSPATH=%MEMBRANE_HOME%/conf"
set "CLASSPATH=%CLASSPATH%;%MEMBRANE_HOME%/lib/membrane-esb.jar;%MEMBRANE_HOME%/lib/commons-cli-1.1.jar;%MEMBRANE_HOME%/lib/commons-logging.jar;%MEMBRANE_HOME%/lib/xmlbeautifier-1.2.1.jar;%MEMBRANE_HOME%/lib/commons-codec-1.3.jar"
java -classpath "%CLASSPATH%" com.predic8.membrane.balancer.client.LBNotificationClient %1 %2 %3 %4 %5 %6 %7 %8 %9

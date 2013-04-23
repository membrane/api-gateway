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

:javaHomeMissing
echo Please set the JAVA_HOME environment variable.
exit

:homeOk
if not exist "%JAVA_HOME%" goto javaHomeMissing
java "-Djava.ext.dirs=%JAVA_HOME%/jre/lib/ext/;%MEMBRANE_HOME%/lib/" -cp %MEMBRANE_HOME%/conf/  com.predic8.membrane.balancer.client.LBNotificationClient %1 %2 %3 %4 %5 %6 %7 %8 %9

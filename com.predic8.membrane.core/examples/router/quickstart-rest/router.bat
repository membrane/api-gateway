@echo off
if not "%MEMBRANE_HOME%" == "" goto homeSet
set MEMBRANE_HOME=%cd%\..\..
echo %MEMBRANE_HOME%
if exist "%MEMBRANE_HOME%\memrouter.bat" goto homeOk

:homeSet
if exist "%MEMBRANE_HOME%\memrouter.bat" goto homeOk
echo Please set the MEMBRANE_HOME environment variable to point to
echo the directory where you have extracted the Membrane software.
exit

:homeOk
set CLASSPATH=%MEMBRANE_HOME%
set CLASSPATH=%MEMBRANE_HOME%/conf
set CLASSPATH=%CLASSPATH%;%MEMBRANE_HOME%/starter.jar
echo Membrane Router running...
java  -classpath %CLASSPATH% com.predic8.membrane.core.Starter -c quickstart-rest.proxies.xml -b ..\..\conf\monitor-beans.min.xml
@echo off
if not "%MEMBRANE_HOME%" == "" goto homeSet
set "MEMBRANE_HOME=%cd%"
if exist "%MEMBRANE_HOME%\memrouter.bat" goto homeOk

:homeSet
if exist "%MEMBRANE_HOME%\memrouter.bat" goto homeOk
echo Please set the MEMBRANE_HOME environment variable to point to
echo the directory where you have extracted the Membrane software.
goto end

:homeOk
set "CLASSPATH=%MEMBRANE_HOME%"
set "CLASSPATH=%MEMBRANE_HOME%/conf"
set "CLASSPATH=%CLASSPATH%;%MEMBRANE_HOME%/starter.jar"
echo Membrane Router running...
java  -classpath "%CLASSPATH%" com.predic8.membrane.core.Starter %1 %2 %3 %4 %5 %6 %7 %8 %9

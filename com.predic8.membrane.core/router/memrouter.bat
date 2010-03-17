@echo off
if not "%MEMROUTER_HOME%" == "" goto homeSet
set MEMROUTER_HOME=%cd%
if exist "%MEMROUTER_HOME%\memrouter.bat" goto homeOk

:homeSet
if exist "%MEMROUTER_HOME%\memrouter.bat" goto homeOk
echo Please set the MEMROUTER_HOME environment variable to point to
echo the directory where you have extrackted the Membrane software.
goto end

:homeOk
set CLASSPATH=%MEMROUTER_HOME%
set CLASSPATH=%MEMROUTER_HOME%/conf
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/stax2-api-3.0.1.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/stax-api-1.0.1.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/woodstox-core-asl-4.0.5.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/starter.jar
java  -classpath %CLASSPATH% com.predic8.membrane.core.Starter %1 %2 %3 %4 %5  %6 %7 %8 %9 %10
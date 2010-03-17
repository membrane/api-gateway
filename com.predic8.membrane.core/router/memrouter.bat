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
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/commons-discovery.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/commons-logging.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/log4j-1.2.8.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/spring-beans.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/spring-core.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/xmlbeautifier-1.1.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/activation-1.1.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/commons-cli-1.1.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/commons-codec-1.3.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/commons-httpclient-3.1.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/stax2-api-3.0.1.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/stax-api-1.0.1.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/lib/woodstox-core-asl-4.0.5.jar
set CLASSPATH=%CLASSPATH%;%MEMROUTER_HOME%/membrane-router.jar
java  -classpath %CLASSPATH% com.predic8.membrane.core.RouterCLI %1 %2 %3 %4 %5  %6 %7 %8 %9 %10
@echo off
if not "%MEMBRANE_HOME%" == "" goto homeSet
set "MEMBRANE_HOME=%cd%"
if exist "%MEMBRANE_HOME%\service-proxy.bat" goto homeOk

:homeSet
if exist "%MEMBRANE_HOME%\service-proxy.bat" goto homeOk
echo Please set the MEMBRANE_HOME environment variable to point to
echo the directory where you have extracted the Membrane software.
goto end

:homeOk
set "CLASSPATH=%MEMBRANE_HOME%"
set "CLASSPATH=%MEMBRANE_HOME%/conf"
set "CLASSPATH=%CLASSPATH%;%MEMBRANE_HOME%/starter.jar"

for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVAVER=%%g
)
set JAVAVER=%JAVAVER:"=%

for /f "delims=. tokens=2" %%v in ("%JAVAVER%") do (
    set JAVAVER=%%v
)

if %JAVAVER% LSS 8 echo "You are running Java %JAVAVER%. Membrane needs atleast Java 8. Please install a newer Java version from http://www.oracle.com/technetwork/java/javase/downloads/index.html"


echo Membrane Router running...
java  -classpath "%CLASSPATH%" com.predic8.membrane.core.Starter %1 %2 %3 %4 %5 %6 %7 %8 %9

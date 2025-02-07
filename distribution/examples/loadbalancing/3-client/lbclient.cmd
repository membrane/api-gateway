@echo off
REM Set MEMBRANE_HOME to three directories above the current directory if it’s not already defined.

if not defined MEMBRANE_HOME (
  REM Calculate the absolute path three levels up.
  for %%f in ("%cd%\..\..\..") do set "MEMBRANE_HOME=%%~ff"
)
echo MEMBRANE_HOME is set to: %MEMBRANE_HOME%

REM Check if membrane.cmd exists in MEMBRANE_HOME.
if not exist "%MEMBRANE_HOME%\membrane.cmd" (
  echo Please set the MEMBRANE_HOME environment variable to point to
  echo the directory where you have extracted the Membrane software.
  exit /b 1
)

REM Check JAVA_HOME is defined and that the directory exists.
if not defined JAVA_HOME (
  echo Please set the JAVA_HOME environment variable.
  exit /b 1
)

if not exist "%JAVA_HOME%" (
  echo JAVA_HOME is set to "%JAVA_HOME%" but the directory does not exist.
  exit /b 1
)

REM Build the classpath.
set "CLASSPATH=%JAVA_HOME%\jre\lib\ext\*;%MEMBRANE_HOME%\lib\*;%MEMBRANE_HOME%\conf\*"

REM Execute the Java command with all passed-in arguments.
java -cp "%CLASSPATH%" com.predic8.membrane.balancer.client.LBNotificationClient %*
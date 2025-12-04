@echo off
setlocal
if not defined MEMBRANE_HOME (>&2 echo start_router.cmd: MEMBRANE_HOME not set & exit /b 1)
set "CLASSPATH=%MEMBRANE_HOME%\conf;%MEMBRANE_HOME%\lib\*"

java %JAVA_OPTS% -cp "%CLASSPATH%" com.predic8.membrane.core.cli.RouterCLI %*
set "status=%ERRORLEVEL%"
if not "%status%"=="0" (
  >&2 echo Membrane terminated with exit code %status%
  >&2 echo MEMBRANE_HOME: %MEMBRANE_HOME%
  >&2 echo CLASSPATH: %CLASSPATH%
)
exit /b %status%

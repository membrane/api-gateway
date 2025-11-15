@echo off
setlocal

set "required=%MEMBRANE_REQUIRED_JAVA_VERSION%"
if not defined required set "required=21"

where java >nul 2>nul || (
  >&2 echo Java is not installed. Membrane needs at least Java %required%.
  exit /b 1
)

set "SCRIPT_DIR=%~dp0"
java -jar "%SCRIPT_DIR%..\starter.jar" %*
exit /b %ERRORLEVEL%

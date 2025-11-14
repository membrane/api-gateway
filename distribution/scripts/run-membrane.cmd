@echo off
setlocal
set "SCRIPTS_DIR=%~dp0"
if not defined MEMBRANE_HOME set "MEMBRANE_HOME=%SCRIPTS_DIR%\.."
for %%A in ("%MEMBRANE_HOME%") do set "MEMBRANE_HOME=%%~fA"
if not exist "%MEMBRANE_HOME%\LICENSE.txt" (
  set "MEMBRANE_HOME=%SCRIPTS_DIR%\.."
  for %%A in ("%MEMBRANE_HOME%") do set "MEMBRANE_HOME=%%~fA"
)
if not defined MEMBRANE_REQUIRED_JAVA_VERSION set "MEMBRANE_REQUIRED_JAVA_VERSION=21"
call "%SCRIPTS_DIR%\java_check.cmd" || exit /b 1
call "%SCRIPTS_DIR%\start_router.cmd" %*
exit /b %ERRORLEVEL%

@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "dir=%SCRIPT_DIR%"

:search_up
if exist "%dir%\starter.jar" if exist "%dir%\scripts\run-membrane.cmd" goto found
for %%A in ("%dir%\..") do set "next=%%~fA"
if /I "%next%"=="%dir%" goto notfound
set "dir=%next%"
goto search_up

:found
set "MEMBRANE_HOME=%dir%"
set "MEMBRANE_CALLER_DIR=%SCRIPT_DIR%"
call "%MEMBRANE_HOME%\scripts\run-membrane.cmd" %*
exit /b %ERRORLEVEL%

:notfound
>&2 echo Could not locate Membrane root. Ensure directory structure is correct.
exit /b 1

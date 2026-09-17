@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "DIR=%~dp0"
if "%DIR:~-1%"=="\" set "DIR=%DIR:~0,-1%"
set "IMAGE=predic8/membrane:7.6.0"

rem Without an explicit -c the container falls back to its own baked-in
rem conf/apis.yaml and silently ignores the one in this directory.
set "ARGS=%*"
if not defined ARGS set "ARGS=-c conf/apis.yaml"

rem Ports 2000-2010 and 9000 (admin console) cover conf/apis.yaml and most tutorials.
rem For a configuration listening elsewhere, publish it additionally, e.g.:
rem   set "MEMBRANE_DOCKER_OPTS=-p 3128:3128"

rem Bind-mount this directory so config edits on the host are picked up live.
rem Mounted at /opt/membrane/work, not /opt/membrane/conf: the image ships its own
rem console-only conf/log4j2.xml that must not be shadowed.
for /f "delims=" %%i in ('docker create -p 2000-2010:2000-2010 -p 9000:9000 %MEMBRANE_DOCKER_OPTS% -v "%DIR%:/opt/membrane/work" -w /opt/membrane/work --entrypoint /opt/membrane/membrane.sh %IMAGE% %ARGS%') do set "CID=%%i"

set "CLEANUP_CMD=docker rm -f %CID% >nul 2>nul"

docker start -a "%CID%"
set "STATUS=%ERRORLEVEL%"

%CLEANUP_CMD%
endlocal & exit /b %STATUS%

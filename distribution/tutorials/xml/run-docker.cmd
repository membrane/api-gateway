@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "DIR=%~dp0"
if "%DIR:~-1%"=="\" set "DIR=%DIR:~0,-1%"
set "IMAGE=predic8/membrane:7.6.0"

for /f "delims=" %%i in ('docker create -p 2000-2010:2000-2010 -v "%DIR%:/opt/membrane/tutorial" -w /opt/membrane/tutorial --entrypoint /opt/membrane/membrane.sh %IMAGE% %*') do set "CID=%%i"

set "CLEANUP_CMD=docker rm -f %CID% >nul 2>nul"

docker start -a "%CID%"
set "STATUS=%ERRORLEVEL%"

%CLEANUP_CMD%
endlocal & exit /b %STATUS%

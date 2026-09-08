@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "DIR=%~dp0"
set "IMAGE=predic8/membrane:7.5.0"

for /f "delims=" %%i in ('docker create -p 2000-2010:2000-2010 %IMAGE% %*') do set "CID=%%i"

set "CLEANUP_CMD=docker rm -f %CID% >nul 2>nul"

docker cp "%DIR%." "%CID%:/opt/membrane/" >nul
docker start -a "%CID%"
set "STATUS=%ERRORLEVEL%"

%CLEANUP_CMD%
endlocal & exit /b %STATUS%

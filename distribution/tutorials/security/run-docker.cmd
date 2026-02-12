@echo off
setlocal enabledelayedexpansion

set "DIR=%~dp0"
set "IMAGE=predic8/membrane:7.1.0"

for /f "delims=" %%i in ('docker create -p 2000-2010:2000-2010 %IMAGE% %*') do set "CID=%%i"

set "CLEANUP_CMD=docker rm -f %CID% >nul 2>nul"

docker cp "%DIR%." "%CID%:/opt/membrane/" >nul
docker start -a "%CID%"

%CLEANUP_CMD%
endlocal

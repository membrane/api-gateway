@echo off
setlocal
set "required=%MEMBRANE_REQUIRED_JAVA_VERSION%"
if not defined required set "required=21"
where java >nul 2>nul || (>&2 echo Java is not installed. Membrane needs at least Java %required%. & exit /b 1)
set "vline="
for /f "delims=" %%A in ('java -version 2^>^&1') do if not defined vline set "vline=%%A"
set "full="
for /f tokens^=2^ delims^=^" %%B in ("%vline%") do set "full=%%B"
if not defined full for /f "tokens=2" %%C in ("%vline%") do set "full=%%C"
if not defined full (>&2 echo WARNING: Could not determine Java version. Proceeding anyway... & exit /b 0)
set "major="
for /f "tokens=1-3 delims=._-" %%a in ("%full%") do if "%%a"=="1" (set "major=%%b") else (set "major=%%a")
echo %major%| findstr /r "^[0-9][0-9]*$" >nul || (>&2 echo WARNING: Could not parse Java version "%full%". Proceeding anyway... & exit /b 0)
if %major% LSS %required% (>&2 echo Java version mismatch: Required=%required%, Installed=%full% & exit /b 1)
exit /b 0

@echo off
setlocal EnableDelayedExpansion

set "exitcode=0"
set "required_version=21"

where java >nul 2>&1
if errorlevel 1 (
    echo Java is not installed. Membrane needs at least Java %required_version%.
    set "exitcode=1"
    goto finish
)

for /f "usebackq tokens=3 delims= " %%A in (`java -version 2^>^&1 ^| findstr /i "version"`) do (
    set "full_version=%%A"
    goto :versionFound
)

:versionFound
if not defined full_version (
    echo WARNING: Could not determine Java version. Make sure Java version is at least %required_version%. Proceeding anyway...
    goto startMembrane
)
set "full_version=%full_version:"=%"

for /f "delims=." %%B in ("%full_version%") do (
    set "current_version=%%B"
)

for /f "delims=0123456789" %%C in ("!current_version!") do (
    set "nonNumeric=%%C"
)
if defined nonNumeric (
    echo WARNING: Could not parse Java version. Make sure your Java version is at least %required_version%. Proceeding anyway...
    goto startMembrane
)

if %current_version% GEQ %required_version% (
    goto startMembrane
) else (
    echo Java version mismatch: Required=%required_version%, Installed=%full_version%
    set "exitcode=1"
    goto finish
)

:findMembraneDirectory
set "test_dir=%~1"
if exist "%test_dir%\conf" if exist "%test_dir%\lib" (
    set "membrane_home=%test_dir%"
    goto :eof
)

if "%test_dir:~1%"==":\" (
    goto :eof
)

for %%A in ("%test_dir%") do set "parent=%%~dpA"
if not "%parent:~0,3%"=="%parent%" (
    set "parent=%parent:~0,-1%"
)
if /i "%parent%"=="%test_dir%" (
    goto :eof
)
call :findMembraneDirectory "%parent%"
goto :eof

:startMembrane
set "current=%CD%"
set "membrane_home="

call :findMembraneDirectory "%current%"

if defined membrane_home (
    call :startMembraneService "%membrane_home%"
    set "exitcode=0"
    goto finish
) else (
    echo Could not start Membrane. Ensure the directory structure is correct.
    set "exitcode=1"
    goto finish
)

:startMembraneService
set "membrane_home=%~1"
set "MEMBRANE_HOME=%membrane_home%"
set "CLASSPATH=%membrane_home%\conf;%membrane_home%\lib\*"
echo Starting: %membrane_home% CL: %CLASSPATH%
java -cp "%CLASSPATH%" com.predic8.membrane.core.cli.RouterCLI -c proxies.xml
goto :eof

:finish
pause
exit /b %exitcode%
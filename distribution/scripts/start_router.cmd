@echo off
setlocal
if not defined MEMBRANE_HOME (>&2 echo start_router.cmd: MEMBRANE_HOME not set & exit /b 1)
set "CLASSPATH=%MEMBRANE_HOME%\conf;%MEMBRANE_HOME%\lib\*"

set | findstr /B /I /R /C:"JAVA_OPTS=.*-Dlog4j\.configurationFile=" >nul && call :normalize_java_opts

rem === Terminal Color Detection ==============================================

set "DISABLE_COLORS=true"

rem User override via environment variable (only accepts "true" or "false", case-insensitive)
if defined MEMBRANE_DISABLE_TERM_COLORS (
    if /I "%MEMBRANE_DISABLE_TERM_COLORS%"=="true" (
        set "DISABLE_COLORS=true"
        goto :color_detection_done
    )
    if /I "%MEMBRANE_DISABLE_TERM_COLORS%"=="false" (
        set "DISABLE_COLORS=false"
        goto :color_detection_done
    )
    rem Invalid value - show warning and continue with auto-detection
    >&2 echo Warning: MEMBRANE_DISABLE_TERM_COLORS must be 'true' or 'false' ^(case-insensitive^). Ignoring value: %MEMBRANE_DISABLE_TERM_COLORS%
)

rem Auto-detection

if defined CI (
    set "DISABLE_COLORS=true"
    goto :color_detection_done
)

rem Windows Terminal detection
if defined WT_SESSION (
    set "DISABLE_COLORS=false"
    goto :color_detection_done
)

rem Check for TERM_PROGRAM (VSCode, etc.)
if defined TERM_PROGRAM (
    set "DISABLE_COLORS=false"
    goto :color_detection_done
)

set "DISABLE_COLORS=true"

:color_detection_done

set "JAVA_OPTS=%JAVA_OPTS% -Dmembrane.disable.term.colors=%DISABLE_COLORS% -Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0"

java %JAVA_OPTS% -cp "%CLASSPATH%" com.predic8.membrane.core.cli.RouterCLI %*
set "status=%ERRORLEVEL%"
if not "%status%"=="0" (
  >&2 echo Membrane terminated with exit code %status%
  >&2 echo MEMBRANE_HOME: %MEMBRANE_HOME%
  >&2 echo CLASSPATH: %CLASSPATH%
)
exit /b %status%

:normalize_java_opts
setlocal EnableDelayedExpansion
set "REST=!JAVA_OPTS!"
set "NEW="
set Q=^"
:normalize_next
if not defined REST goto :normalize_done
if "!REST:~0,1!"==" " (
    set "REST=!REST:~1!"
    goto :normalize_next
)
rem Split only at spaces outside double quotes, so a quoted path containing spaces stays one option.
set "TOK="
set "INQ="
:normalize_char
if not defined REST goto :normalize_token
set "C=!REST:~0,1!"
if not defined INQ if "!C!"==" " goto :normalize_token
if "!C!"=="!Q!" (if defined INQ (set "INQ=") else set "INQ=1")
set "TOK=!TOK!!C!"
set "REST=!REST:~1!"
goto :normalize_char
:normalize_token
set "BARE=!TOK:"=!"
if "!BARE:~0,26!"=="-Dlog4j.configurationFile=" call :resolve_log4j_path
if defined NEW (set "NEW=!NEW! !TOK!") else set "NEW=!TOK!"
goto :normalize_next
:normalize_done
for /f "eol= delims=" %%V in ("!NEW!") do endlocal & set "JAVA_OPTS=%%V"
exit /b 0

rem Log4j accepts a comma-separated list of locations, so each one is resolved on its own.
:resolve_log4j_path
set "VAL=!BARE:~26!"
if not defined VAL exit /b 0
set "OUT="
set "LOC="
set "CHANGED="
:resolve_next_char
if not defined VAL goto :resolve_done
set "C=!VAL:~0,1!"
set "VAL=!VAL:~1!"
if not "!C!"=="," (
    set "LOC=!LOC!!C!"
    goto :resolve_next_char
)
call :resolve_location
set "OUT=!OUT!,"
goto :resolve_next_char
:resolve_done
call :resolve_location
rem Quoted because MEMBRANE_HOME may contain spaces (e.g. C:\Program Files\membrane).
if defined CHANGED set "TOK="-Dlog4j.configurationFile=!OUT!""
exit /b 0

rem Absolute paths, UNC paths and URIs are left unchanged, like in start_router.sh.
rem Windows file names cannot contain ":", so any colon means a drive path or a URI/resource prefix (file:, classpath:, ...).
:resolve_location
rem Log4j trims each location, so "a.xml, b.xml" is valid.
:resolve_trim_start
if defined LOC if "!LOC:~0,1!"==" " (
    set "LOC=!LOC:~1!"
    goto :resolve_trim_start
)
:resolve_trim_end
if defined LOC if "!LOC:~-1!"==" " (
    set "LOC=!LOC:~0,-1!"
    goto :resolve_trim_end
)
if defined LOC if not "!LOC:~0,1!"=="\" if not "!LOC:~0,1!"=="/" if "!LOC::=!"=="!LOC!" (
    set "LOC=!MEMBRANE_HOME!\!LOC!"
    set "CHANGED=1"
)
set "OUT=!OUT!!LOC!"
set "LOC="
exit /b 0

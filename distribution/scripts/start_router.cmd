@echo off
setlocal
if not defined MEMBRANE_HOME (>&2 echo start_router.cmd: MEMBRANE_HOME not set & exit /b 1)
set "CLASSPATH=%MEMBRANE_HOME%\conf;%MEMBRANE_HOME%\lib\*"

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

set "JAVA_OPTS=%JAVA_OPTS% -Dmembrane.disable.term.colors=%DISABLE_COLORS%"

java %JAVA_OPTS% -cp "%CLASSPATH%" com.predic8.membrane.core.cli.RouterCLI %*
set "status=%ERRORLEVEL%"
if not "%status%"=="0" (
  >&2 echo Membrane terminated with exit code %status%
  >&2 echo MEMBRANE_HOME: %MEMBRANE_HOME%
  >&2 echo CLASSPATH: %CLASSPATH%
)
exit /b %status%

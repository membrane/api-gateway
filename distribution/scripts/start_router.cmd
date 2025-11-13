@echo off
setlocal
if not defined MEMBRANE_HOME (>&2 echo start_router.cmd: MEMBRANE_HOME not set & exit /b 1)
set "CLASSPATH=%MEMBRANE_HOME%\conf;%MEMBRANE_HOME%\lib\*"
if "%~1"=="" (
  rem Prio 1: apis.yaml / apis.yml in caller dir
  if exist "apis.yaml" (
    set "CFG=apis.yaml"
  ) else if exist "apis.yml" (
    set "CFG=apis.yml"

  rem Prio 2: proxies.xml in caller dir
  ) else if exist "proxies.xml" (
    set "CFG=proxies.xml"

  rem Prio 3: apis.yaml / apis.yml in MEMBRANE_HOME
  ) else if exist "%MEMBRANE_HOME%\conf\apis.yaml" (
    set "CFG=%MEMBRANE_HOME%\conf\apis.yaml"
  ) else if exist "%MEMBRANE_HOME%\conf\apis.yml" (
    set "CFG=%MEMBRANE_HOME%\conf\apis.yml"

  rem Prio 4: proxies.xml in MEMBRANE_HOME
  ) else if exist "%MEMBRANE_HOME%\conf\proxies.xml" (
    set "CFG=%MEMBRANE_HOME%\conf\proxies.xml"
  )

  if not defined CFG (
    >&2 echo No configuration file found ^(apis.yaml, apis.yml or proxies.xml^). Provide one of these or use -c ^<file^>.
    exit /b 1
  )

  java %JAVA_OPTS% -cp "%CLASSPATH%" com.predic8.membrane.core.cli.RouterCLI -c "%CFG%"
  set "status=%ERRORLEVEL%"
  if not "%status%"=="0" (
    >&2 echo Membrane terminated with exit code %status%
    >&2 echo MEMBRANE_HOME: %MEMBRANE_HOME%
    >&2 echo CLASSPATH: %CLASSPATH%
  )
  exit /b %status%
)
java %JAVA_OPTS% -cp "%CLASSPATH%" com.predic8.membrane.core.cli.RouterCLI %*
set "status=%ERRORLEVEL%"
if not "%status%"=="0" (
  >&2 echo Membrane terminated with exit code %status%
  >&2 echo MEMBRANE_HOME: %MEMBRANE_HOME%
  >&2 echo CLASSPATH: %CLASSPATH%
)
exit /b %status%
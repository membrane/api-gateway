@echo off
setlocal
set "dir=%CD%"
:loop
if exist "%dir%\LICENSE.txt" if exist "%dir%\scripts\run-membrane.cmd" goto found
for %%A in ("%dir%\..") do set "next=%%~fA"
if /I "%next%"=="%dir%" goto notfound
set "dir=%next%"
if "%dir%"=="%SystemDrive%\" goto notfound
goto loop
:found
set "ROOT=%dir%"
call "%ROOT%\scripts\run-membrane.cmd" %*
exit /b %ERRORLEVEL%
:notfound
>&2 echo Could not locate Membrane root. Ensure directory structure is correct.
exit /b 1
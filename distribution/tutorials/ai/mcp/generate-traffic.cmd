@echo off
setlocal EnableExtensions EnableDelayedExpansion

if "%FRUIT_BASE%"=="" set FRUIT_BASE=http://localhost:3000
if "%APIBIN_BASE%"=="" set APIBIN_BASE=http://localhost:3001
if "%ATTACK_BASE%"=="" set ATTACK_BASE=http://localhost:3002

if "%~1"=="" (
  set ROUNDS=20
) else (
  set ROUNDS=%~1
)

for /L %%i in (1,1,%ROUNDS%) do (
  call :request GET "%FRUIT_BASE%/products/"
  call :request GET "%FRUIT_BASE%/products/4"
  call :request GET "%FRUIT_BASE%/categories/"

  call :request GET "%APIBIN_BASE%/analyze?round=%%i&delay=50"
  call :request GET "%APIBIN_BASE%/faker?profile=order&count=2&locale=de-DE&seed=%%i"
  call :request POST "%APIBIN_BASE%/echo" "hello apibin round %%i"
  call :request GET "%ATTACK_BASE%/wp-login.php"
  call :request GET "%ATTACK_BASE%/xmlrpc.php"
  call :request GET "%ATTACK_BASE%/wp-admin/install.php"
  call :request GET "%ATTACK_BASE%/.env"
  call :request GET "%ATTACK_BASE%/phpinfo.php"
)

exit /b 0

:request
set METHOD=%~1
set URL=%~2
set BODY=%~3

echo %METHOD% %URL%

if "%BODY%"=="" (
  curl -sS -o NUL -w " -^> %%{http_code}\n" -X %METHOD% "%URL%"
) else (
  curl -sS -o NUL -w " -^> %%{http_code}\n" -X %METHOD% "%URL%" -H "Content-Type: text/plain" --data "%BODY%"
)

exit /b 0

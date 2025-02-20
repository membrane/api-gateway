@echo off
REM Überprüfen der Eingabeparameter (username und password)
if "%~1"=="" (
  echo Usage: %0 username password
  exit /b 1
)
if "%~2"=="" (
  echo Usage: %0 username password
  exit /b 1
)

set "username=%~1"
set "password=%~2"

REM Variablen definieren
set "clientId=abc"
set "clientSecret=def"
set "tokenEndpoint=http://localhost:7007/oauth2/token"
set "target=http://localhost:2000"

echo 1.) Requesting Token
echo POST %tokenEndpoint%
echo.

REM POST-Parameter zusammenbauen (Escape des &-Zeichens mit ^)
set "postParams=grant_type=password^&username=%username%^&password=%password%^&client_id=%clientId%^&client_secret=%clientSecret%"
echo %postParams%
echo.

REM Token anfordern und Antwort in token.json speichern
curl -s -X POST -d "%postParams%" "%tokenEndpoint%" -o token.json

REM Überprüfen, ob die Antwort-Datei existiert
if not exist token.json (
    echo Fehler beim Abrufen des Tokens.
    exit /b 1
)

REM JSON parsen: Mit PowerShell wird der Token-Typ und Access-Token extrahiert
for /f "delims=" %%i in ('powershell -NoProfile -Command "Get-Content token.json | ConvertFrom-Json | ForEach-Object { Write-Output ($_.token_type + ' ' + $_.access_token) }"') do set "authToken=%%i"

echo Got Token: %authToken%
echo.

echo 2.) Calling API
echo GET %target%
echo Authorization: %authToken%
echo.

REM API-Aufruf an das Ziel mit dem erhaltenen Token
curl -s -H "Authorization: %authToken%" "%target%"
echo.

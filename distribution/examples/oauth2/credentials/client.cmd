@echo off
setlocal EnableDelayedExpansion

REM Variablen definieren
set "clientId=abc"
set "clientSecret=def"
set "tokenEndpoint=http://localhost:7000/oauth2/token"
set "target=http://localhost:2000"

echo 1.) Requesting Token
REM POST-Parameter für client_credentials zusammenbauen (Escape des &-Zeichens mit ^)
set "postParams=grant_type=client_credentials^&client_id=%clientId%^&client_secret=%clientSecret%"
echo %postParams%
echo.

REM Token anfordern und Antwort in token.json speichern
curl -s -X POST -d "%postParams%" "%tokenEndpoint%" -o token.json

if not exist token.json (
    echo Fehler beim Abrufen des Tokens.
    exit /b 1
)

REM Mit PowerShell den token_type und access_token extrahieren und zu einem Header zusammenfügen
for /f "delims=" %%i in ('powershell -NoProfile -Command "Get-Content token.json | ConvertFrom-Json | ForEach-Object { Write-Output ($_.token_type + ' ' + $_.access_token) }"') do set "authToken=%%i"

echo Got Token: %authToken%
echo.

echo 2.) Calling API
echo GET %target%
echo Authorization: %authToken%
echo.

REM API-Aufruf an das Ziel mit dem Authorization-Header; Statuscode wird ausgegeben
curl -s -o response.txt -w "HTTPSTATUS: %%{http_code}" -H "Authorization: %authToken%" "%target%" > output.txt

REM Aus der Ausgabe den HTTP-Status-Code extrahieren
for /f "tokens=2 delims=:" %%a in ('findstr "HTTPSTATUS:" output.txt') do set "status=%%a"

echo HTTP Status Description: %status%

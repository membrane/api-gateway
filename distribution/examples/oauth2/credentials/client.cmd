@echo off
setlocal

:: Variablen setzen
set clientId=abc
set clientSecret=def
set tokenEndpoint=http://localhost:7000/oauth2/token
set target=http://localhost:2000

:: OAuth2-Token per POST anfordern und in token.json speichern
curl -s -X POST -d "grant_type=client_credentials&client_id=%clientId%&client_secret=%clientSecret%" %tokenEndpoint% -o token.json

:: Falls die Token-Datei nicht erstellt wurde, Skript beenden
if not exist token.json (
    echo Fehler: Token konnte nicht abgerufen werden.
    goto :EOF
)

:: JSON-Ausgabe parsen – token_type und access_token über PowerShell extrahieren
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "(Get-Content token.json | ConvertFrom-Json).token_type"`) do set token_type=%%A
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "(Get-Content token.json | ConvertFrom-Json).access_token"`) do set access_token=%%A

:: Authorization-Header zusammensetzen (z.B. "Bearer xyz")
set authHeader=%token_type% %access_token%

:: Zielanfrage durchführen – Header speichern und HTTP-Antwortstatus ermitteln
curl -s -D headers.txt -o nul -H "Authorization: %authHeader%" %target%

:: Aus der ersten Header-Zeile (z.B. "HTTP/1.1 200 OK") den Statusbeschreibungs-Teil extrahieren
set "statusDesc="
for /f "tokens=1,2,3* delims= " %%a in (headers.txt) do (
    set statusDesc=%%c
    goto :gotStatus
)
:gotStatus

echo %statusDesc%

:: Temporäre Dateien aufräumen
del token.json
del headers.txt

endlocal

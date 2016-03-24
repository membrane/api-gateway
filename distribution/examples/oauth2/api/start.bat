SETLOCAL

SET username=john
SET password=password


powershell -Noninteractive .\api.ps1 -username %username% -password %password%
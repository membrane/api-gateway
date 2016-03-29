SETLOCAL

SET username=john
SET password=password


powershell -Noninteractive .\trusted_client\api.ps1 -username %username% -password %password%
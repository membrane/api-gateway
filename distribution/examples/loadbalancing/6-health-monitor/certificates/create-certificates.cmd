setlocal

set "PASSWORD=changeit"
set "DNAME=CN=localhost, OU=Dev, O=MyOrg, L=MyCity, ST=MyState, C=DE"
set "SAN=SAN=dns:localhost,ip:127.0.0.1"

where keytool >nul 2>&1 || (echo keytool not found. Install JDK and ensure it's in PATH.& exit /b 1)

REM --- backend-1 ---
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -validity 365 ^
  -keystore backend-1.p12 -storetype PKCS12 -storepass "%PASSWORD%" -keypass "%PASSWORD%" ^
  -dname "%DNAME%" -ext "%SAN%"
keytool -exportcert -alias localhost -keystore backend-1.p12 -storetype PKCS12 ^
  -storepass "%PASSWORD%" -rfc -file backend-1.pem

REM --- backend-2 ---
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -validity 365 ^
  -keystore backend-2.p12 -storetype PKCS12 -storepass "%PASSWORD%" -keypass "%PASSWORD%" ^
  -dname "%DNAME%" -ext "%SAN%"
keytool -exportcert -alias localhost -keystore backend-2.p12 -storetype PKCS12 ^
  -storepass "%PASSWORD%" -rfc -file backend-2.pem

REM --- balancer ---
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -validity 365 ^
  -keystore balancer.p12 -storetype PKCS12 -storepass "%PASSWORD%" -keypass "%PASSWORD%" ^
  -dname "%DNAME%" -ext "%SAN%"

REM Import node certs into balancer trust
keytool -importcert -alias backend-1 -file backend-1.pem -keystore balancer.p12 -storetype PKCS12 ^
  -storepass "%PASSWORD%" -noprompt
keytool -importcert -alias backend-2 -file backend-2.pem -keystore balancer.p12 -storetype PKCS12 ^
  -storepass "%PASSWORD%" -noprompt

echo Done. Created backend-1.p12, backend-2.p12, balancer.p12 (+ PEMs).
endlocal

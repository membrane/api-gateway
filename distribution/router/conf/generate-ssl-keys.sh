#!/bin/bash
echo  Script that generates sample keystores for SSL/TLS

echo "---------------------- Client ----------------------------------------------"
keytool -genkey -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -alias client -keypass secret -keystore client.p12 -storetype PKCS12 -storepass secret -dname "cn=client"
keytool -selfcert -alias client -keystore client.p12 -storetype PKCS12 -storepass secret -keypass secret -validity 3650
keytool -export -alias client -file client.cer -keystore client.p12 -storetype PKCS12 -storepass secret

echo "---------------------- Membrane --------------------------------------------"
keytool -genkey -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -alias membrane -keypass secret -keystore membrane.p12 -storetype PKCS12 -storepass secret -dname "cn=membrane"
keytool -selfcert -alias membrane -keystore membrane.p12 -storetype PKCS12 -storepass secret -keypass secret -validity 3650
keytool -export -alias membrane -file membrane.cer -keystore membrane.p12 -storetype PKCS12 -storepass secret

echo "-------------------- Exchange of certificates ------------------------------"
keytool -import -alias membrane -file membrane.cer -keystore client.p12 -storetype PKCS12 -storepass secret
keytool -import -alias client -file client.cer -keystore membrane.p12 -storetype PKCS12 -storepass secret

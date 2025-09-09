#!/bin/bash

# Password for the keystore
PASSWORD=changeit

# Create PKCS12 keystore with self-signed cert for localhost
keytool -genkeypair \
  -alias localhost \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -keystore backend-1.p12 \
  -storetype PKCS12 \
  -storepass $PASSWORD \
  -keypass $PASSWORD \
  -dname "CN=localhost, OU=Dev, O=MyOrg, L=MyCity, ST=MyState, C=DE" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

# Export the certificate (public part) in PEM format
keytool -exportcert \
  -alias localhost \
  -keystore backend-1.p12 \
  -storetype PKCS12 \
  -storepass $PASSWORD \
  -rfc \
  -file backend-1.pem

# Create PKCS12 keystore with self-signed cert for localhost
keytool -genkeypair \
  -alias localhost \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -keystore backend-2.p12 \
  -storetype PKCS12 \
  -storepass $PASSWORD \
  -keypass $PASSWORD \
  -dname "CN=localhost, OU=Dev, O=MyOrg, L=MyCity, ST=MyState, C=DE" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

# Export the certificate (public part) in PEM format
keytool -exportcert \
  -alias localhost \
  -keystore backend-2.p12 \
  -storetype PKCS12 \
  -storepass $PASSWORD \
  -rfc \
  -file backend-2.pem




keytool -genkeypair \
  -alias localhost \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -keystore balancer.p12 \
  -storetype PKCS12 \
  -storepass $PASSWORD \
  -keypass $PASSWORD \
  -dname "CN=localhost, OU=Dev, O=MyOrg, L=MyCity, ST=MyState, C=DE" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

keytool -importcert \
  -alias "backend-1" \
  -file backend-1.pem \
  -keystore "balancer.p12" \
  -storetype PKCS12 \
  -storepass "${PASSWORD}" \
  -noprompt

keytool -importcert \
  -alias "backend-2" \
  -file backend-2.pem \
  -keystore "balancer.p12" \
  -storetype PKCS12 \
  -storepass "${PASSWORD}" \
  -noprompt

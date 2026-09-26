#!/usr/bin/env bash
# Builds the real Membrane distribution zip and ships everything needed for the performance test
# (gateway zip, backend role, client role + its dependencies) to the 3 VMs created by
# provision.sh. Run this once after provisioning; use run-scenario.sh to execute test cases
# afterwards (repeatable, no need to re-run this script between scenarios).
#
# The client's HTTP library (async-http-client, a `test`-scoped dependency of the distribution
# module) is resolved fresh via `mvn dependency:copy-dependencies` into ./client-libs on every
# run -- that directory is gitignored, nothing here is a vendored/committed library.
#
# Also generates the self-signed TLS keystores/truststore used by the rate-limit-basic-auth-tls
# scenario, via `keytool` (bundled with the JDK you already need to build this repo) -- see ./certs,
# also gitignored/regenerated fresh every run.
set -euo pipefail

RG=${RG:-membrane-perftest-rg}
ADMIN_USER=${ADMIN_USER:-azureuser}
PT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$PT/../.." && pwd)"
source "$PT/vm-addresses.sh"

echo "== 1. Discovering VM IPs =="
discover_vm_ips
echo "backend: pub=$BACKEND_PUB priv=$BACKEND_PRIV"
echo "gateway: pub=$GATEWAY_PUB priv=$GATEWAY_PRIV"
echo "client:  pub=$CLIENT_PUB"

echo "== 2. Building distribution zip and installing reactor artifacts =="
# The separate dependency:copy-dependencies invocation below resolves reactor dependencies
# from the local Maven repository, so install the artifacts from this build first.
( cd "$REPO" && mvn -pl distribution -am -DskipTests install )
ZIP=$(ls -t "$REPO"/distribution/target/membrane-api-gateway-*.zip | head -1)
UNZIPPED_NAME=$(basename "$ZIP" .zip)
echo "Zip: $ZIP"

echo "== 3. Resolving client-side dependencies (async-http-client + transitives) =="
rm -rf "$PT/client-libs"
( cd "$REPO" && mvn -pl distribution dependency:copy-dependencies \
    -DincludeScope=test -DoutputDirectory="$PT/client-libs" )

echo "== 4. Resolving gateway configs with the backend's private IP and remote OAS path =="
GHOME="/home/$ADMIN_USER/$UNZIPPED_NAME"
mkdir -p "$PT/conf/resolved"
cp "$PT/conf/loadtest-shortcircuit.xml" "$PT/conf/resolved/loadtest-shortcircuit.xml"
sed "s#__BACKEND_PRIVATE_IP__#$BACKEND_PRIV#" \
    "$PT/conf/loadtest-fullproxy.xml" > "$PT/conf/resolved/loadtest-fullproxy.xml"
sed "s#__BACKEND_PRIVATE_IP__#$BACKEND_PRIV#" \
    "$PT/conf/loadtest-rate-limit-basic-auth.xml" > "$PT/conf/resolved/loadtest-rate-limit-basic-auth.xml"
sed -e "s#__BACKEND_PRIVATE_IP__#$BACKEND_PRIV#" \
    -e "s#__OAS_PATH__#$GHOME/conf_override/fruitshop-v2-2-0.oas.yml#" \
    "$PT/conf/loadtest-openapi-validation.xml" > "$PT/conf/resolved/loadtest-openapi-validation.xml"
sed -e "s#__BACKEND_PRIVATE_IP__#$BACKEND_PRIV#" \
    -e "s#__GATEWAY_KEYSTORE_PATH__#$GHOME/conf_override/gateway.p12#" \
    -e "s#__GATEWAY_TRUSTSTORE_PATH__#$GHOME/conf_override/gateway-truststore.p12#" \
    "$PT/conf/loadtest-rate-limit-basic-auth-tls.xml" > "$PT/conf/resolved/loadtest-rate-limit-basic-auth-tls.xml"
sed -e "s#__BACKEND_PRIVATE_IP__#$BACKEND_PRIV#" \
    -e "s#__WSDL_PATH__#$GHOME/conf_override/person-service.wsdl#" \
    "$PT/conf/loadtest-wsdl2openapi.xml" > "$PT/conf/resolved/loadtest-wsdl2openapi.xml"
sed -e "s#__BACKEND_PRIVATE_IP__#$BACKEND_PRIV#" \
    -e "s#__WSDL_PATH__#$GHOME/conf_override/person-service.wsdl#" \
    "$PT/conf/loadtest-soap-validation.xml" > "$PT/conf/resolved/loadtest-soap-validation.xml"

echo "== 4b. Generating self-signed TLS certificates for the rate-limit-basic-auth-tls scenario =="
# Regenerated fresh on every deploy (gitignored, see .gitignore) -- SANs are baked in against this
# run's discovered private IPs, so they can't be committed/reused across provisions. The gateway's
# own cert doesn't need a SAN matching anything: the client trusts it via LOAD_INSECURE_TLS, not a
# truststore. The backend's does: the gateway's <target><ssl><truststore> validates both the chain
# and (endpointIdentificationAlgorithm defaults to "HTTPS") that the cert's SAN matches the host it
# connects to, i.e. __BACKEND_PRIVATE_IP__.
CERT_PASSWORD=changeit
CERTS="$PT/certs"
rm -rf "$CERTS"
mkdir -p "$CERTS"
keytool -genkeypair -alias gateway -keyalg RSA -keysize 2048 -validity 365 \
  -keystore "$CERTS/gateway.p12" -storetype PKCS12 -storepass "$CERT_PASSWORD" -keypass "$CERT_PASSWORD" \
  -dname "CN=lt-gateway, OU=Membrane Performance Test, O=predic8, C=DE" \
  -ext "SAN=ip:$GATEWAY_PRIV,ip:127.0.0.1"
keytool -genkeypair -alias backend -keyalg RSA -keysize 2048 -validity 365 \
  -keystore "$CERTS/backend.p12" -storetype PKCS12 -storepass "$CERT_PASSWORD" -keypass "$CERT_PASSWORD" \
  -dname "CN=lt-backend, OU=Membrane Performance Test, O=predic8, C=DE" \
  -ext "SAN=ip:$BACKEND_PRIV,ip:127.0.0.1"
keytool -exportcert -alias backend -keystore "$CERTS/backend.p12" -storetype PKCS12 \
  -storepass "$CERT_PASSWORD" -rfc -file "$CERTS/backend-cert.pem"
keytool -importcert -alias backend -file "$CERTS/backend-cert.pem" \
  -keystore "$CERTS/gateway-truststore.p12" -storetype PKCS12 -storepass "$CERT_PASSWORD" -noprompt

SSH="ssh -o StrictHostKeyChecking=accept-new"
SCP="scp -o StrictHostKeyChecking=accept-new"

echo "== 5. Shipping artifacts =="
$SCP "$ZIP" "$ADMIN_USER@$BACKEND_PUB:~/"
$SCP "$PT/java/LoadTesterBackend.java" "$ADMIN_USER@$BACKEND_PUB:~/"
$SCP "$CERTS/backend.p12" "$ADMIN_USER@$BACKEND_PUB:~/"
$SCP "$ZIP" "$ADMIN_USER@$GATEWAY_PUB:~/"
$SCP "$REPO/distribution/router/conf/openapi/fruitshop-v2-2-0.oas.yml" "$ADMIN_USER@$GATEWAY_PUB:~/"
$SCP "$PT/conf/person-service.wsdl" "$ADMIN_USER@$GATEWAY_PUB:~/"
$SCP "$PT"/conf/resolved/*.xml "$ADMIN_USER@$GATEWAY_PUB:~/"
$SCP "$CERTS/gateway.p12" "$CERTS/gateway-truststore.p12" "$ADMIN_USER@$GATEWAY_PUB:~/"
$SCP "$PT/java/LoadTesterClient.java" "$ADMIN_USER@$CLIENT_PUB:~/"
# Upload into a fresh directory, then replace the active directory so removed/upgraded JARs
# cannot survive a redeploy. Keep the previous set in a separate backup directory.
CLIENT_LIBS_STAGE=$($SSH "$ADMIN_USER@$CLIENT_PUB" 'mktemp -d /home/'"$ADMIN_USER"'/client-libs.XXXXXX')
$SCP "$PT"/client-libs/*.jar "$ADMIN_USER@$CLIENT_PUB:$CLIENT_LIBS_STAGE/"
$SSH "$ADMIN_USER@$CLIENT_PUB" "set -e; if [ -d ~/client-libs ]; then backup=\$(mktemp -d ~/client-libs-backup.XXXXXX); mv ~/client-libs \"\$backup/\"; fi; mv '$CLIENT_LIBS_STAGE' ~/client-libs"
for host in "$GATEWAY_PUB" "$BACKEND_PUB" "$CLIENT_PUB"; do
  $SCP "$PT/cpu-sample.sh" "$ADMIN_USER@$host:~/cpu-sample.sh"
done

echo "== 6. Starting backend (shared by all scenarios; idle/unused in the short-circuit case) =="
# Compile in the foreground first so errors surface synchronously. The start step fully
# redirects stdin/stdout/stderr (</dev/null >log 2>&1) and backgrounds with disown -- otherwise
# the ssh session can hang waiting for descriptors held by the whole "cmd &" chain to close.
$SSH "$ADMIN_USER@$BACKEND_PUB" 'pkill -f "com.predic8.membrane.load.[L]oadTesterBackend" || true
for attempt in {1..30}; do
  pgrep -f "com.predic8.membrane.load.[L]oadTesterBackend" >/dev/null || exit 0
  sleep 1
done
echo "Previous backend did not stop" >&2
exit 1'
$SSH "$ADMIN_USER@$BACKEND_PUB" "rm -rf $UNZIPPED_NAME && unzip -o $UNZIPPED_NAME.zip && javac -cp '$UNZIPPED_NAME/lib/*' -d classes LoadTesterBackend.java"
# BACKEND_TLS_KEYSTORE starts a second, TLS-only listener on 2011 alongside the plaintext one on
# 2010 (see LoadTesterBackend.java), so rate-limit-basic-auth-tls can share this same backend
# process instead of needing its own. The wsdl2openapi scenario's SOAP listener on 2012 always runs.
$SSH "$ADMIN_USER@$BACKEND_PUB" "BACKEND_TLS_KEYSTORE=/home/$ADMIN_USER/backend.p12 BACKEND_TLS_KEYSTORE_PASSWORD=$CERT_PASSWORD nohup java -cp '$UNZIPPED_NAME/lib/*:classes' com.predic8.membrane.load.LoadTesterBackend 2010 </dev/null >backend.log 2>&1 & echo \$! > backend.pid"
$SSH "$ADMIN_USER@$BACKEND_PUB" 'pid=$(cat backend.pid)
for attempt in {1..30}; do
  kill -0 "$pid" 2>/dev/null || break
  if curl --silent --fail --max-time 2 http://localhost:2010/ >/dev/null && curl --silent --fail --max-time 2 -k https://localhost:2011/ >/dev/null && curl --silent --fail --max-time 2 http://localhost:2012/ >/dev/null; then
    kill -0 "$pid" 2>/dev/null && { echo "backend OK"; exit 0; }
  fi
  sleep 1
done
echo "backend FAILED, log:" >&2
cat backend.log
exit 1'

echo "== 7. Unpacking gateway distribution and placing configs (not starting it yet) =="
$SSH "$ADMIN_USER@$GATEWAY_PUB" "rm -rf $UNZIPPED_NAME && unzip -o $UNZIPPED_NAME.zip && mkdir -p $UNZIPPED_NAME/conf_override && cp ~/loadtest-*.xml $UNZIPPED_NAME/conf_override/ && cp ~/fruitshop-v2-2-0.oas.yml ~/person-service.wsdl $UNZIPPED_NAME/conf_override/ && cp ~/gateway.p12 ~/gateway-truststore.p12 $UNZIPPED_NAME/conf_override/"
$SSH "$ADMIN_USER@$GATEWAY_PUB" "printf '%s\\n' '$GHOME' > ~/loadtest-gateway-path"

echo "== 8. Compiling client =="
$SSH "$ADMIN_USER@$CLIENT_PUB" "javac -cp 'client-libs/*' -d classes LoadTesterClient.java"

echo ""
echo "Setup complete. Run a scenario with:"
echo "  ./run-scenario.sh shortcircuit"
echo "  ./run-scenario.sh fullproxy"
echo "  ./run-scenario.sh openapi-validation"
echo "  ./run-scenario.sh rate-limit-basic-auth"
echo "  ./run-scenario.sh rate-limit-basic-auth-tls"
echo "  ./run-scenario.sh wsdl2openapi"
echo "  ./run-scenario.sh soap-validation"
echo ""
echo "Each accepts an optional concurrency argument, e.g. ./run-scenario.sh fullproxy 150"
echo ""
echo "When done, run teardown.sh to avoid ongoing Azure cost."

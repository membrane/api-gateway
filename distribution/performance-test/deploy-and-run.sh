#!/usr/bin/env bash
# Builds the real Membrane distribution zip and ships everything needed for the performance test
# (gateway zip, backend role, client role + its dependencies) to the 3 VMs created by
# provision.sh. Run this once after provisioning; use run-scenario.sh to execute test cases
# afterwards (repeatable, no need to re-run this script between scenarios).
#
# The client's HTTP library (async-http-client, a `test`-scoped dependency of the distribution
# module) is resolved fresh via `mvn dependency:copy-dependencies` into ./client-libs on every
# run -- that directory is gitignored, nothing here is a vendored/committed library.
set -euo pipefail

RG=${RG:-membrane-perftest-rg}
ADMIN_USER=${ADMIN_USER:-azureuser}
PT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$PT/../.." && pwd)"

echo "== 1. Building distribution zip and installing reactor artifacts =="
# The separate dependency:copy-dependencies invocation below resolves reactor dependencies
# from the local Maven repository, so install the artifacts from this build first.
( cd "$REPO" && mvn -pl distribution -am -DskipTests install )
ZIP=$(ls -t "$REPO"/distribution/target/membrane-api-gateway-*.zip | head -1)
UNZIPPED_NAME=$(basename "$ZIP" .zip)
echo "Zip: $ZIP"

echo "== 2. Resolving client-side dependencies (async-http-client + transitives) =="
rm -rf "$PT/client-libs"
( cd "$REPO" && mvn -pl distribution dependency:copy-dependencies \
    -DincludeScope=test -DoutputDirectory="$PT/client-libs" )

echo "== 3. Discovering VM IPs =="
BACKEND_PUB=$(az vm list-ip-addresses -g "$RG" -n lt-backend --query "[0].virtualMachine.network.publicIpAddresses[0].ipAddress" -o tsv)
BACKEND_PRIV=$(az vm list-ip-addresses -g "$RG" -n lt-backend --query "[0].virtualMachine.network.privateIpAddresses[0]" -o tsv)
GATEWAY_PUB=$(az vm list-ip-addresses -g "$RG" -n lt-gateway --query "[0].virtualMachine.network.publicIpAddresses[0].ipAddress" -o tsv)
GATEWAY_PRIV=$(az vm list-ip-addresses -g "$RG" -n lt-gateway --query "[0].virtualMachine.network.privateIpAddresses[0]" -o tsv)
CLIENT_PUB=$(az vm list-ip-addresses -g "$RG" -n lt-client --query "[0].virtualMachine.network.publicIpAddresses[0].ipAddress" -o tsv)
echo "backend: pub=$BACKEND_PUB priv=$BACKEND_PRIV"
echo "gateway: pub=$GATEWAY_PUB priv=$GATEWAY_PRIV"
echo "client:  pub=$CLIENT_PUB"

echo "== 4. Resolving gateway configs with the backend's private IP and remote OAS path =="
GHOME="/home/$ADMIN_USER/$UNZIPPED_NAME"
mkdir -p "$PT/conf/resolved"
cp "$PT/conf/loadtest-shortcircuit.xml" "$PT/conf/resolved/loadtest-shortcircuit.xml"
sed "s#__BACKEND_PRIVATE_IP__#$BACKEND_PRIV#" \
    "$PT/conf/loadtest-fullproxy.xml" > "$PT/conf/resolved/loadtest-fullproxy.xml"
sed -e "s#__BACKEND_PRIVATE_IP__#$BACKEND_PRIV#" \
    -e "s#__OAS_PATH__#$GHOME/conf_override/fruitshop-v2-2-0.oas.yml#" \
    "$PT/conf/loadtest-openapi-validation.xml" > "$PT/conf/resolved/loadtest-openapi-validation.xml"

SSH="ssh -o StrictHostKeyChecking=accept-new"
SCP="scp -o StrictHostKeyChecking=accept-new"

echo "== 5. Shipping artifacts =="
$SCP "$ZIP" "$ADMIN_USER@$BACKEND_PUB:~/"
$SCP "$PT/java/LoadTesterBackend.java" "$ADMIN_USER@$BACKEND_PUB:~/"
$SCP "$ZIP" "$ADMIN_USER@$GATEWAY_PUB:~/"
$SCP "$REPO/distribution/router/conf/openapi/fruitshop-v2-2-0.oas.yml" "$ADMIN_USER@$GATEWAY_PUB:~/"
$SCP "$PT"/conf/resolved/*.xml "$ADMIN_USER@$GATEWAY_PUB:~/"
$SCP "$PT/java/LoadTesterClient.java" "$ADMIN_USER@$CLIENT_PUB:~/"
$SCP -r "$PT/client-libs" "$ADMIN_USER@$CLIENT_PUB:~/client-libs"

echo "== 6. Starting backend (shared by all scenarios; idle/unused in the short-circuit case) =="
# Compile in the foreground first so errors surface synchronously. The start step fully
# redirects stdin/stdout/stderr (</dev/null >log 2>&1) and backgrounds with disown -- otherwise
# the ssh session can hang waiting for descriptors held by the whole "cmd &" chain to close.
$SSH "$ADMIN_USER@$BACKEND_PUB" "unzip -o $UNZIPPED_NAME.zip && javac -cp '$UNZIPPED_NAME/lib/*' -d classes LoadTesterBackend.java"
$SSH "$ADMIN_USER@$BACKEND_PUB" "nohup java -cp '$UNZIPPED_NAME/lib/*:classes' com.predic8.membrane.load.LoadTesterBackend 2010 </dev/null >backend.log 2>&1 & disown; echo backend start issued"
sleep 5
$SSH "$ADMIN_USER@$BACKEND_PUB" "pgrep -fa LoadTesterBackend >/dev/null && echo 'backend OK' || { echo 'backend FAILED, log:'; cat backend.log; exit 1; }"

echo "== 7. Unpacking gateway distribution and placing configs (not starting it yet) =="
$SSH "$ADMIN_USER@$GATEWAY_PUB" "unzip -o $UNZIPPED_NAME.zip && mkdir -p $UNZIPPED_NAME/conf_override && cp ~/loadtest-*.xml $UNZIPPED_NAME/conf_override/ && cp ~/fruitshop-v2-2-0.oas.yml $UNZIPPED_NAME/conf_override/"

echo "== 8. Compiling client =="
$SSH "$ADMIN_USER@$CLIENT_PUB" "javac -cp 'client-libs/*' -d classes LoadTesterClient.java"

echo ""
echo "Setup complete. Run a scenario with:"
echo "  ./run-scenario.sh shortcircuit"
echo "  ./run-scenario.sh fullproxy"
echo "  ./run-scenario.sh openapi-validation"
echo ""
echo "Each accepts an optional concurrency argument, e.g. ./run-scenario.sh fullproxy 150"
echo ""
echo "When done, run teardown.sh to avoid ongoing Azure cost."

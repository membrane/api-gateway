#!/usr/bin/env bash
# Restarts the gateway with one of the prepared scenario configs, samples CPU on all three
# VMs while under load, and runs the measured client run against it. Run deploy-and-run.sh once
# first; this script can then be re-run repeatedly (e.g. at different concurrency levels or after
# a code change + redeploy) without needing to redo setup.
#
# Usage: ./run-scenario.sh <shortcircuit|fullproxy|openapi-validation|rate-limit-basic-auth|rate-limit-basic-auth-tls|wsdl2openapi|soap-validation> [concurrency]
set -euo pipefail

SCENARIO="${1:?Usage: $0 <shortcircuit|fullproxy|openapi-validation|rate-limit-basic-auth|rate-limit-basic-auth-tls|wsdl2openapi|soap-validation> [concurrency]}"
# Concurrency: the optional second argument, else the scenario's DEFAULT_CONCURRENCY set in the
# case statement below. 100 (the default) is the throughput peak for fullproxy on the default
# Standard_F16as_v7 gateway (sweep 64/100/150/220/300: ~200k/211k/201k/200k/204k RPS; above 100
# only p99 grows, 3ms -> 23ms). Re-sweep if you change VM sizes.
DEFAULT_CONCURRENCY=100
# Measured requests per run. 10,000,000 gives a longer, steadier measured window; the rate-limit
# configs' requestLimit is sized for that.
LOAD_TOTAL=${LOAD_TOTAL:-1000000}
# Untimed warmup requests before the measured phase. 1,000,000 (several seconds of load) lets the
# gateway's JIT reach steady state; the former 10,000 took well under a second, so short runs
# still measured part of the warmup.
LOAD_WARMUP=${LOAD_WARMUP:-1000000}

RG=${RG:-membrane-perftest-rg}
ADMIN_USER=${ADMIN_USER:-azureuser}
# Fixed, pre-touched heap + ParallelGC: measured to use noticeably less gateway CPU than the
# JVM's adaptive default for the same throughput, with total GC pause time under 100ms across a
# full 1,000,000-request run (see TESTED-CONFIGURATIONS.md, "Heap size and GC tuning"). Override
# or blank out via the JAVA_OPTS env var to compare against the JVM's untuned defaults.
JAVA_OPTS=${JAVA_OPTS-'-Xms32g -Xmx32g -XX:+AlwaysPreTouch -XX:+UseParallelGC -Xlog:gc*,safepoint:file=gc.log:time,uptime,level,tags'}
PT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shortcircuit/openapi-validation use a small fixed POST+JSON body, same as always. fullproxy and
# rate-limit-basic-auth instead use a padded ~1KB JSON body -- a more realistic request size than
# the 33-byte minimal one, and (along with backlog/somaxconn) one of the things that differs
# between fullproxy's config and shortcircuit/openapi-validation. rate-limit-basic-auth is built
# directly on top of fullproxy's topology and uses its body/backlog too, so its RPS can be
# subtracted directly against fullproxy's to isolate the two plugins' combined added cost --
# unlike openapi-validation, which must keep the small body to satisfy the OpenAPI spec it
# validates against. rate-limit-basic-auth-tls is in turn built directly on rate-limit-basic-auth
# (same body/backlog/credentials/rate limit), adding only TLS on both hops, so its RPS subtracts
# directly against rate-limit-basic-auth's to isolate the added TLS cost. See
# TESTED-CONFIGURATIONS.md for the full concurrency/body sweep history behind these defaults.
# (A GET/no-body variant of fullproxy was measured once and found statistically indistinguishable
# in RPS -- the JSON body isn't on the gateway's/backend's critical path in this scenario, since
# neither validates nor inspects it -- so the payload-size question only matters for byte-transfer
# cost, not gateway logic.)
FULLPROXY_BODY_1KB='{"name":"Mangos","price":2.79,"description":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}'

BASIC_AUTH_USER=loadtest
BASIC_AUTH_PASSWORD=loadtest-secret
BASIC_AUTH_HEADER="Basic $(printf '%s:%s' "$BASIC_AUTH_USER" "$BASIC_AUTH_PASSWORD" | base64)"

# Synthetic person for the wsdl2openapi scenario: 10 scalar fields, one array, one nested object,
# matching the createPerson request element of conf/person-service.wsdl (no root key: the JSON is
# the content of that element).
WSDL2OPENAPI_BODY='{"person":{"firstName":"Jane","lastName":"Doe","email":"jane.doe@example.com","phone":"+49 228 5550100","dateOfBirth":"1985-04-12","heightCm":172,"weightKg":64.5,"newsletter":true,"customerNumber":100042,"nationality":"DE","hobby":["cycling","chess","cooking"],"address":{"street":"Example Street","houseNumber":"12a","postalCode":"53111","city":"Bonn","region":"NRW","country":"DE","additionalInfo":"2nd floor"}}}'

# The same person as a SOAP 1.1 request for the soap-validation scenario. person-service.wsdl's
# schema has no elementFormDefault, so only the createPerson element is namespace-qualified.
SOAP_VALIDATION_BODY='<s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/"><s11:Body><p:createPerson xmlns:p="http://example.com/person"><person><firstName>Jane</firstName><lastName>Doe</lastName><email>jane.doe@example.com</email><phone>+49 228 5550100</phone><dateOfBirth>1985-04-12</dateOfBirth><heightCm>172</heightCm><weightKg>64.5</weightKg><newsletter>true</newsletter><customerNumber>100042</customerNumber><nationality>DE</nationality><hobby>cycling</hobby><hobby>chess</hobby><hobby>cooking</hobby><address><street>Example Street</street><houseNumber>12a</houseNumber><postalCode>53111</postalCode><city>Bonn</city><region>NRW</region><country>DE</country><additionalInfo>2nd floor</additionalInfo></address></person></p:createPerson></s11:Body></s11:Envelope>'

LOAD_AUTHORIZATION=""
LOAD_PATH=/shop/v2/products
LOAD_SCHEME=http
LOAD_INSECURE_TLS=""
case "$SCENARIO" in
  shortcircuit)        CONFIG=loadtest-shortcircuit.xml;        LOAD_METHOD=POST; LOAD_BODY='{"name":"Mangos","price":2.79}'; LOAD_CONTENT_TYPE=application/json; DEFAULT_CONCURRENCY=350 ;;
  fullproxy)            CONFIG=loadtest-fullproxy.xml;          LOAD_METHOD=POST; LOAD_BODY="$FULLPROXY_BODY_1KB";          LOAD_CONTENT_TYPE=application/json ;;
  openapi-validation)   CONFIG=loadtest-openapi-validation.xml; LOAD_METHOD=POST; LOAD_BODY='{"name":"Mangos","price":2.79}'; LOAD_CONTENT_TYPE=application/json ;;
  rate-limit-basic-auth) CONFIG=loadtest-rate-limit-basic-auth.xml; LOAD_METHOD=POST; LOAD_BODY="$FULLPROXY_BODY_1KB";      LOAD_CONTENT_TYPE=application/json; LOAD_AUTHORIZATION="$BASIC_AUTH_HEADER" ;;
  rate-limit-basic-auth-tls) CONFIG=loadtest-rate-limit-basic-auth-tls.xml; LOAD_METHOD=POST; LOAD_BODY="$FULLPROXY_BODY_1KB"; LOAD_CONTENT_TYPE=application/json; LOAD_AUTHORIZATION="$BASIC_AUTH_HEADER"; LOAD_SCHEME=https; LOAD_INSECURE_TLS=true ;;
  wsdl2openapi)          CONFIG=loadtest-wsdl2openapi.xml;       LOAD_METHOD=POST; LOAD_BODY="$WSDL2OPENAPI_BODY";          LOAD_CONTENT_TYPE=application/json; LOAD_PATH=/create-person ;;
  soap-validation)       CONFIG=loadtest-soap-validation.xml;    LOAD_METHOD=POST; LOAD_BODY="$SOAP_VALIDATION_BODY";       LOAD_CONTENT_TYPE=text/xml; LOAD_PATH=/person-service ;;
  *) echo "Unknown scenario '$SCENARIO' (expected shortcircuit|fullproxy|openapi-validation|rate-limit-basic-auth|rate-limit-basic-auth-tls|wsdl2openapi|soap-validation)"; exit 1 ;;
esac
CONCURRENCY="${2:-$DEFAULT_CONCURRENCY}"

echo "Discovering VM IPs..."
source "$PT/vm-addresses.sh"
discover_vm_ips
# Keepalives: the client's ssh session prints nothing during the measured run, which lasts
# over a minute with LOAD_TOTAL=10000000, long enough for an idle connection to be dropped
# ("Read from remote host ...: Operation timed out") and the run's output lost.
SSH="ssh -o StrictHostKeyChecking=accept-new -o ServerAliveInterval=15 -o ServerAliveCountMax=4"

GHOME=$($SSH "$ADMIN_USER@$GATEWAY_PUB" 'cat ~/loadtest-gateway-path')
if [[ "$GHOME" != /home/"$ADMIN_USER"/membrane-api-gateway-* || "$GHOME" == *$'\n'* ]]; then
  echo "Invalid deployed gateway path; rerun deploy-and-run.sh" >&2
  exit 1
fi

echo ">>> [$SCENARIO] stopping any running gateway"
# The bracket trick ([R]outerCLI) keeps pkill/pgrep from matching their own ssh-invoked command
# line, which otherwise contains the literal string "RouterCLI" and would self-match/self-kill.
# Wait for it to actually exit (not a fixed sleep) so the new gateway below isn't racing it for
# port 2000.
$SSH "$ADMIN_USER@$GATEWAY_PUB" "pkill -f '[R]outerCLI' || true; for i in \$(seq 1 20); do pgrep -f '[R]outerCLI' >/dev/null || exit 0; sleep 0.5; done; echo 'old gateway did not exit in time' >&2; exit 1"

echo ">>> [$SCENARIO] starting gateway with $CONFIG (JAVA_OPTS='$JAVA_OPTS')"
# Keep setup in the foreground. Backgrounding an entire && chain leaves its shell holding
# the SSH output pipe open, even when the gateway itself has all descriptors redirected.
$SSH "$ADMIN_USER@$GATEWAY_PUB" "set -e; cd '$GHOME'; rm -f gc.log; JAVA_OPTS='$JAVA_OPTS' nohup ./membrane.sh -c '$GHOME/conf_override/$CONFIG' </dev/null >~/gateway.log 2>&1 & echo \$! > ~/gateway.pid; echo issued"
# Poll the PID we just launched specifically, plus the gateway's own port -- a broad pgrep here
# could still match the old process if it lingers, and report OK even though the new one failed
# to bind. AlwaysPreTouch on a 32GB heap takes longer to come up than the JVM's untuned default.
$SSH "$ADMIN_USER@$GATEWAY_PUB" "PID=\$(cat ~/gateway.pid); for i in \$(seq 1 30); do kill -0 \$PID 2>/dev/null || { echo 'gateway FAILED (process exited)'; tail -50 ~/gateway.log; exit 1; }; (exec 3<>/dev/tcp/127.0.0.1/2000) 2>/dev/null && exec 3>&- && { echo 'gateway OK'; exit 0; }; sleep 1; done; echo 'gateway FAILED (not ready after 30s)'; tail -50 ~/gateway.log; exit 1"

echo ">>> [$SCENARIO] starting CPU sampling"
ROLES=(gw be cl)
HOSTS=("$GATEWAY_PUB" "$BACKEND_PUB" "$CLIENT_PUB")
SAMPLE_DIRS=()
CLIENT_OUTPUT=$(mktemp)
cleanup() {
  for ((i=0; i<${#SAMPLE_DIRS[@]}; i++)); do
    $SSH "$ADMIN_USER@${HOSTS[$i]}" "kill \$(cat '${SAMPLE_DIRS[$i]}/pid') 2>/dev/null || true" || true
  done
  rm -f "$CLIENT_OUTPUT"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
for ((i=0; i<${#HOSTS[@]}; i++)); do
  SAMPLE_DIRS[$i]=$($SSH "$ADMIN_USER@${HOSTS[$i]}" 'mktemp -d /tmp/membrane-cpu.XXXXXX')
  $SSH "$ADMIN_USER@${HOSTS[$i]}" "nohup bash ~/cpu-sample.sh </dev/null > '${SAMPLE_DIRS[$i]}/cpu.log' 2>'${SAMPLE_DIRS[$i]}/error.log' & echo \$! > '${SAMPLE_DIRS[$i]}/pid'"
done

echo ">>> [$SCENARIO] running client at concurrency $CONCURRENCY ($LOAD_TOTAL requests + $LOAD_WARMUP warmup, $LOAD_METHOD)"
$SSH "$ADMIN_USER@$CLIENT_PUB" "cd ~ && TARGET_URL=$LOAD_SCHEME://$GATEWAY_PRIV:2000$LOAD_PATH LOAD_METHOD=$LOAD_METHOD LOAD_BODY='$LOAD_BODY' LOAD_CONTENT_TYPE=$LOAD_CONTENT_TYPE LOAD_AUTHORIZATION='$LOAD_AUTHORIZATION' LOAD_INSECURE_TLS=$LOAD_INSECURE_TLS LOAD_TOTAL=$LOAD_TOTAL LOAD_CONCURRENCY=$CONCURRENCY LOAD_WARMUP=$LOAD_WARMUP java -cp 'client-libs/*:classes' com.predic8.membrane.load.LoadTesterClient" | tee "$CLIENT_OUTPUT"

WINDOW=$(awk '/^MEASURED_WINDOW [0-9]+ [0-9]+$/ {print $2, $3}' "$CLIENT_OUTPUT")
if [[ ! "$WINDOW" =~ ^[0-9]+\ [0-9]+$ ]]; then
  echo "Missing measured window; redeploy the updated client" >&2
  exit 1
fi
read -r START END <<< "$WINDOW"

echo ">>> [$SCENARIO] CPU summary (complete intervals inside measured window, including idle intervals)"
for ((i=0; i<${#HOSTS[@]}; i++)); do
  echo "--- ${ROLES[$i]} ---"
  $SSH "$ADMIN_USER@${HOSTS[$i]}" "kill -0 \$(cat '${SAMPLE_DIRS[$i]}/pid') 2>/dev/null || { cat '${SAMPLE_DIRS[$i]}/error.log' >&2; exit 1; }"
  $SSH "$ADMIN_USER@${HOSTS[$i]}" "awk -v start='$START' -v end='$END' -f - '${SAMPLE_DIRS[$i]}/cpu.log'" < "$PT/cpu-summary.awk"
done

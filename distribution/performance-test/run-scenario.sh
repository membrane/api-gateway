#!/usr/bin/env bash
# Restarts the gateway with one of the prepared scenario configs, samples CPU on all three
# VMs while under load, and runs the measured client run against it. Run deploy-and-run.sh once
# first; this script can then be re-run repeatedly (e.g. at different concurrency levels or after
# a code change + redeploy) without needing to redo setup.
#
# Usage: ./run-scenario.sh <shortcircuit|fullproxy|openapi-validation|rate-limit-basic-auth|rate-limit-basic-auth-tls> [concurrency]
set -euo pipefail

SCENARIO="${1:?Usage: $0 <shortcircuit|fullproxy|openapi-validation|rate-limit-basic-auth|rate-limit-basic-auth-tls> [concurrency]}"
# 175 was found empirically to be the throughput plateau's sweet spot for the mixed-hardware
# setup (Standard_FX16mds_v2 gateway / Standard_F16as_v7 backend) -- see TESTED-CONFIGURATIONS.md
# for the concurrency sweep (125/175/220/300) that found it. Re-sweep if you change VM sizes.
CONCURRENCY="${2:-175}"

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

LOAD_AUTHORIZATION=""
LOAD_SCHEME=http
LOAD_INSECURE_TLS=""
case "$SCENARIO" in
  shortcircuit)        CONFIG=loadtest-shortcircuit.xml;        LOAD_METHOD=POST; LOAD_BODY='{"name":"Mangos","price":2.79}'; LOAD_CONTENT_TYPE=application/json ;;
  fullproxy)            CONFIG=loadtest-fullproxy.xml;          LOAD_METHOD=POST; LOAD_BODY="$FULLPROXY_BODY_1KB";          LOAD_CONTENT_TYPE=application/json ;;
  openapi-validation)   CONFIG=loadtest-openapi-validation.xml; LOAD_METHOD=POST; LOAD_BODY='{"name":"Mangos","price":2.79}'; LOAD_CONTENT_TYPE=application/json ;;
  rate-limit-basic-auth) CONFIG=loadtest-rate-limit-basic-auth.xml; LOAD_METHOD=POST; LOAD_BODY="$FULLPROXY_BODY_1KB";      LOAD_CONTENT_TYPE=application/json; LOAD_AUTHORIZATION="$BASIC_AUTH_HEADER" ;;
  rate-limit-basic-auth-tls) CONFIG=loadtest-rate-limit-basic-auth-tls.xml; LOAD_METHOD=POST; LOAD_BODY="$FULLPROXY_BODY_1KB"; LOAD_CONTENT_TYPE=application/json; LOAD_AUTHORIZATION="$BASIC_AUTH_HEADER"; LOAD_SCHEME=https; LOAD_INSECURE_TLS=true ;;
  *) echo "Unknown scenario '$SCENARIO' (expected shortcircuit|fullproxy|openapi-validation|rate-limit-basic-auth|rate-limit-basic-auth-tls)"; exit 1 ;;
esac

echo "Discovering VM IPs..."
source "$PT/vm-addresses.sh"
discover_vm_ips
SSH="ssh -o StrictHostKeyChecking=accept-new"

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

echo ">>> [$SCENARIO] running client at concurrency $CONCURRENCY (1M requests + 10k warmup, $LOAD_METHOD)"
$SSH "$ADMIN_USER@$CLIENT_PUB" "cd ~ && TARGET_URL=$LOAD_SCHEME://$GATEWAY_PRIV:2000/shop/v2/products LOAD_METHOD=$LOAD_METHOD LOAD_BODY='$LOAD_BODY' LOAD_CONTENT_TYPE=$LOAD_CONTENT_TYPE LOAD_AUTHORIZATION='$LOAD_AUTHORIZATION' LOAD_INSECURE_TLS=$LOAD_INSECURE_TLS LOAD_TOTAL=1000000 LOAD_CONCURRENCY=$CONCURRENCY LOAD_WARMUP=10000 java -cp 'client-libs/*:classes' com.predic8.membrane.load.LoadTesterClient" | tee "$CLIENT_OUTPUT"

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

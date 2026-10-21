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
# Fixed request rate (requests per second) for warmup and measured phase, e.g. LOAD_RATE=1000 to
# look at a gateway under typical rather than maximum load. Unset (the default) sends as fast as
# the concurrency allows. With a rate, the concurrency only caps the client's open connections,
# and its default rises to 1000 so a gateway stall queues requests instead of failing them.
LOAD_RATE=${LOAD_RATE:-}
if [[ -n "$LOAD_RATE" && ! "$LOAD_RATE" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "LOAD_RATE must be a positive number" >&2
  exit 1
fi

RG=${RG:-membrane-perftest-rg}
ADMIN_USER=${ADMIN_USER:-azureuser}
# Collector and heap flags. Fixed, pre-touched heap + ParallelGC: measured to use noticeably less
# gateway CPU than the JVM's adaptive default for the same throughput. Override via the GC_OPTS
# env var to compare other settings; GC_OPTS='' (set but empty) runs the JVM's untuned defaults.
# GC logging is added separately (GC_LOG_OPTS below), so overriding GC_OPTS keeps it.
GC_OPTS=${GC_OPTS-'-Xms32g -Xmx32g -XX:+AlwaysPreTouch -XX:+UseParallelGC'}
# Names this run's results directory, e.g. tuned, g1-2g, soak-g1-2g.
RUN_LABEL=${RUN_LABEL:-tuned}
if [[ ! "$RUN_LABEL" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "RUN_LABEL may only contain letters, digits, '.', '_' and '-'" >&2
  exit 1
fi
# GATEWAY_RESTART=false keeps the running gateway JVM (and its gc.log) instead of restarting it
# with this scenario's config, so back-to-back runs form a soak test against one JVM. The running
# gateway must have been started by this script for the same scenario; GC_OPTS has no effect then.
GATEWAY_RESTART=${GATEWAY_RESTART:-true}
# JDK the gateway runs on, e.g. /usr/lib/jvm/temurin-25-jdk-amd64 to compare against another
# version. Empty (the default) uses the gateway VM's `java` on the PATH (Temurin 21, see provision.sh).
GATEWAY_JAVA_HOME=${GATEWAY_JAVA_HOME:-}
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
# directly against rate-limit-basic-auth's to isolate the added TLS cost. The concurrency/body
# sweeps behind these defaults were documented in TESTED-CONFIGURATIONS.md (removed in 1bd2951f5).
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
[[ -n "$LOAD_RATE" ]] && DEFAULT_CONCURRENCY=1000
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

# Epoch-ms timestamps (timemillis) let gc-summary.awk match pauses against the client's
# MEASURED_WINDOW. gc* includes gc+cpu (User/Sys/Real per collection), which shows the concurrent
# work of G1 that never appears as pause time. filecount=0 disables rotation, so a soak run keeps
# the start of the log. PrintCommandLineFlags records the effective JVM flags in gateway.log.
GC_LOG_OPTS="-Xlog:gc*,safepoint:file=$GHOME/gc.log:timemillis,uptime,level,tags:filecount=0 -XX:+PrintCommandLineFlags"
JAVA_OPTS="$GC_OPTS $GC_LOG_OPTS"
# membrane.sh starts whichever `java` comes first on the PATH.
GATEWAY_PATH_PREFIX=""
if [[ -n "$GATEWAY_JAVA_HOME" ]]; then
  $SSH "$ADMIN_USER@$GATEWAY_PUB" "test -x '$GATEWAY_JAVA_HOME/bin/java'" || { echo "No JDK at $GATEWAY_JAVA_HOME on the gateway" >&2; exit 1; }
  GATEWAY_PATH_PREFIX="PATH='$GATEWAY_JAVA_HOME/bin':\$PATH "
fi

RESULTS="$PT/results/$(date +%Y%m%d-%H%M%S)-$SCENARIO-$RUN_LABEL"
mkdir -p "$RESULTS"

if [[ "$GATEWAY_RESTART" == false ]]; then
  echo ">>> [$SCENARIO] keeping the running gateway (GATEWAY_RESTART=false)"
  $SSH "$ADMIN_USER@$GATEWAY_PUB" "PID=\$(cat ~/gateway.pid); kill -0 \$PID 2>/dev/null && (exec 3<>/dev/tcp/127.0.0.1/2000) 2>/dev/null || { echo 'no running gateway; run once without GATEWAY_RESTART=false first' >&2; exit 1; }"
  # The backend answers every scenario's requests, so a gateway left running with another config
  # would not show up as errors. Check what the last restart started.
  RUNNING=$($SSH "$ADMIN_USER@$GATEWAY_PUB" 'cat ~/gateway-run.txt 2>/dev/null' || true)
  RUNNING_CONFIG=$(sed -n 's/^config=//p' <<< "$RUNNING")
  if [[ "$RUNNING_CONFIG" != "$CONFIG" ]]; then
    echo "The running gateway serves '${RUNNING_CONFIG:-unknown}', not $CONFIG; start it once without GATEWAY_RESTART=false" >&2
    exit 1
  fi
  GC_OPTS=$(sed -n 's/^gcOpts=//p' <<< "$RUNNING")
  GATEWAY_JAVA_HOME=$(sed -n 's/^javaHome=//p' <<< "$RUNNING")
else
  echo ">>> [$SCENARIO] stopping any running gateway"
  # The bracket trick ([R]outerCLI) keeps pkill/pgrep from matching their own ssh-invoked command
  # line, which otherwise contains the literal string "RouterCLI" and would self-match/self-kill.
  # Wait for it to actually exit (not a fixed sleep) so the new gateway below isn't racing it for
  # port 2000.
  $SSH "$ADMIN_USER@$GATEWAY_PUB" "pkill -f '[R]outerCLI' || true; for i in \$(seq 1 20); do pgrep -f '[R]outerCLI' >/dev/null || exit 0; sleep 0.5; done; echo 'old gateway did not exit in time' >&2; exit 1"

  echo ">>> [$SCENARIO] starting gateway with $CONFIG (JAVA_OPTS='$JAVA_OPTS', JDK '${GATEWAY_JAVA_HOME:-default}')"
  # Keep setup in the foreground. Backgrounding an entire && chain leaves its shell holding
  # the SSH output pipe open, even when the gateway itself has all descriptors redirected.
  $SSH "$ADMIN_USER@$GATEWAY_PUB" "set -e; cd '$GHOME'; rm -f gc.log; ${GATEWAY_PATH_PREFIX}JAVA_OPTS='$JAVA_OPTS' nohup ./membrane.sh -c '$GHOME/conf_override/$CONFIG' </dev/null >~/gateway.log 2>&1 & echo \$! > ~/gateway.pid; echo issued"
  # Poll the PID we just launched specifically, plus the gateway's own port -- a broad pgrep here
  # could still match the old process if it lingers, and report OK even though the new one failed
  # to bind. AlwaysPreTouch on a 32GB heap takes longer to come up than the JVM's untuned default.
  $SSH "$ADMIN_USER@$GATEWAY_PUB" "PID=\$(cat ~/gateway.pid); for i in \$(seq 1 30); do kill -0 \$PID 2>/dev/null || { echo 'gateway FAILED (process exited)'; tail -50 ~/gateway.log; exit 1; }; (exec 3<>/dev/tcp/127.0.0.1/2000) 2>/dev/null && exec 3>&- && { echo 'gateway OK'; exit 0; }; sleep 1; done; echo 'gateway FAILED (not ready after 30s)'; tail -50 ~/gateway.log; exit 1"
  printf 'config=%s\ngcOpts=%s\njavaHome=%s\n' "$CONFIG" "$GC_OPTS" "$GATEWAY_JAVA_HOME" | $SSH "$ADMIN_USER@$GATEWAY_PUB" 'cat > ~/gateway-run.txt'
fi
# start_router.sh runs java as a child process, so gateway.pid holds the shell, not the JVM.
GATEWAY_JVM_PID=$($SSH "$ADMIN_USER@$GATEWAY_PUB" "pgrep -n -f 'java.*[R]outerCLI'")
GATEWAY_JAVA_VERSION=$($SSH "$ADMIN_USER@$GATEWAY_PUB" "readlink -f /proc/$GATEWAY_JVM_PID/exe; /proc/$GATEWAY_JVM_PID/exe -version 2>&1 | head -2" | paste -sd ' ' -)
printf 'scenario=%s\nrunLabel=%s\nconcurrency=%s\nloadTotal=%s\nloadWarmup=%s\nloadRate=%s\ngatewayRestart=%s\ngcOpts=%s\ngatewayHome=%s\ngatewayJava=%s\n' \
  "$SCENARIO" "$RUN_LABEL" "$CONCURRENCY" "$LOAD_TOTAL" "$LOAD_WARMUP" "$LOAD_RATE" "$GATEWAY_RESTART" "$GC_OPTS" "$GHOME" "$GATEWAY_JAVA_VERSION" > "$RESULTS/run.txt"

echo ">>> [$SCENARIO] starting CPU sampling"
ROLES=(gw be cl)
HOSTS=("$GATEWAY_PUB" "$BACKEND_PUB" "$CLIENT_PUB")
SAMPLE_DIRS=()
CLIENT_OUTPUT="$RESULTS/client.out"
cleanup() {
  for ((i=0; i<${#SAMPLE_DIRS[@]}; i++)); do
    $SSH "$ADMIN_USER@${HOSTS[$i]}" "kill \$(cat '${SAMPLE_DIRS[$i]}/pid') 2>/dev/null || true" || true
  done
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
for ((i=0; i<${#HOSTS[@]}; i++)); do
  SAMPLE_DIRS[$i]=$($SSH "$ADMIN_USER@${HOSTS[$i]}" 'mktemp -d /tmp/membrane-cpu.XXXXXX')
  $SSH "$ADMIN_USER@${HOSTS[$i]}" "nohup bash ~/cpu-sample.sh </dev/null > '${SAMPLE_DIRS[$i]}/cpu.log' 2>'${SAMPLE_DIRS[$i]}/error.log' & echo \$! > '${SAMPLE_DIRS[$i]}/pid'"
done
# The gateway JVM's own CPU time: at a fixed, low rate the whole VM is mostly idle, so its CPU
# share says little about what the gateway (and its GC) spends. Sent with each run, so no redeploy
# is needed; it stops by itself when the JVM exits.
$SSH "$ADMIN_USER@$GATEWAY_PUB" 'cat > ~/proc-cpu-sample.sh' < "$PT/proc-cpu-sample.sh"
$SSH "$ADMIN_USER@$GATEWAY_PUB" "nohup bash ~/proc-cpu-sample.sh $GATEWAY_JVM_PID </dev/null > '${SAMPLE_DIRS[0]}/proc-cpu.log' 2>/dev/null &"

echo ">>> [$SCENARIO] running client at concurrency $CONCURRENCY ($LOAD_TOTAL requests + $LOAD_WARMUP warmup, $LOAD_METHOD${LOAD_RATE:+, $LOAD_RATE req/s})"
$SSH "$ADMIN_USER@$CLIENT_PUB" "cd ~ && rm -f client-gc.log slow-requests.txt && TARGET_URL=$LOAD_SCHEME://$GATEWAY_PRIV:2000$LOAD_PATH LOAD_METHOD=$LOAD_METHOD LOAD_BODY='$LOAD_BODY' LOAD_CONTENT_TYPE=$LOAD_CONTENT_TYPE LOAD_AUTHORIZATION='$LOAD_AUTHORIZATION' LOAD_INSECURE_TLS=$LOAD_INSECURE_TLS LOAD_TOTAL=$LOAD_TOTAL LOAD_CONCURRENCY=$CONCURRENCY LOAD_WARMUP=$LOAD_WARMUP LOAD_RATE=$LOAD_RATE LOAD_SLOW_LOG=\$HOME/slow-requests.txt java -Xlog:gc*,safepoint:file=client-gc.log:timemillis,uptime,level,tags:filecount=0 -cp 'client-libs/*:classes' com.predic8.membrane.load.LoadTesterClient" | tee "$CLIENT_OUTPUT"
# Slow requests (due time and latency) and the client's own GC log tell gateway stalls apart from
# client ones when matched against the gateway's gc.log.
$SSH "$ADMIN_USER@$CLIENT_PUB" 'cat ~/slow-requests.txt 2>/dev/null' > "$RESULTS/slow-requests.txt" || true
$SSH "$ADMIN_USER@$CLIENT_PUB" 'cat ~/client-gc.log' > "$RESULTS/client-gc.log" || true

WINDOW=$(awk '/^MEASURED_WINDOW [0-9]+ [0-9]+$/ {print $2, $3}' "$CLIENT_OUTPUT")
if [[ ! "$WINDOW" =~ ^[0-9]+\ [0-9]+$ ]]; then
  echo "Missing measured window; redeploy the updated client" >&2
  exit 1
fi
read -r START END <<< "$WINDOW"
# A client without the fixed-rate mode ignores LOAD_RATE and runs flat out instead.
if [[ -n "$LOAD_RATE" ]] && ! grep -q '^SEND_RATE ' "$CLIENT_OUTPUT"; then
  echo "The client ignored LOAD_RATE; redeploy the updated client" >&2
  exit 1
fi

echo ">>> [$SCENARIO] CPU summary (complete intervals inside measured window, including idle intervals)"
for ((i=0; i<${#HOSTS[@]}; i++)); do
  echo "--- ${ROLES[$i]} ---"
  $SSH "$ADMIN_USER@${HOSTS[$i]}" "kill -0 \$(cat '${SAMPLE_DIRS[$i]}/pid') 2>/dev/null || { cat '${SAMPLE_DIRS[$i]}/error.log' >&2; exit 1; }"
  $SSH "$ADMIN_USER@${HOSTS[$i]}" "awk -v start='$START' -v end='$END' -f - '${SAMPLE_DIRS[$i]}/cpu.log'" < "$PT/cpu-summary.awk"
done | tee "$RESULTS/cpu-summary.txt"

# Fetch with ssh+cat: scp to the gateway once silently copied nothing.
$SSH "$ADMIN_USER@$GATEWAY_PUB" "cat '$GHOME/gc.log'" > "$RESULTS/gc.log"
$SSH "$ADMIN_USER@$GATEWAY_PUB" 'cat ~/gateway.log' > "$RESULTS/gateway.log"
# PrintCommandLineFlags writes the effective flags as the first line starting with -XX:.
grep -m1 -- '^-XX:' "$RESULTS/gateway.log" > "$RESULTS/jvm-flags.txt" || echo "No JVM flags line in gateway.log" >&2

echo ">>> [$SCENARIO] GC summary (measured window; GC pauses, then all safepoints incl. time to reach them)"
LC_ALL=C awk -v start="$START" -v end="$END" -f "$PT/gc-summary.awk" "$RESULTS/gc.log" | tee "$RESULTS/gc-summary.txt"

$SSH "$ADMIN_USER@$GATEWAY_PUB" "cat '${SAMPLE_DIRS[0]}/proc-cpu.log'" > "$RESULTS/proc-cpu.log"
TICKS=$($SSH "$ADMIN_USER@$GATEWAY_PUB" 'getconf CLK_TCK')
# CPU seconds of the JVM and of its GC threads between the first and the last sample inside the
# measured window, scaled to the window.
echo ">>> [$SCENARIO] gateway JVM CPU (measured window)"
LC_ALL=C awk -v start="$START" -v end="$END" -v ticks="$TICKS" '
  $1 >= start && $1 <= end { if (!first) { first = $1; p0 = $2; g0 = $3 } last = $1; p1 = $2; g1 = $3 }
  END {
    if (last <= first) { print "jvmCpu=n/a gcThreadCpu=n/a"; exit }
    f = (end - start) / (last - first) / ticks
    printf "jvmCpu=%.2fs gcThreadCpu=%.2fs window=%.1fs\n", (p1 - p0) * f, (g1 - g0) * f, (end - start) / 1000
  }' "$RESULTS/proc-cpu.log" | tee "$RESULTS/proc-cpu-summary.txt"
MAX_HEAP=$(grep -o -- '-XX:MaxHeapSize=[0-9]*' "$RESULTS/jvm-flags.txt" | LC_ALL=C awk -F= '{printf "%.0fMB", $2 / 1048576}')

# One line per run, for scanning a series of runs: client errors and latency (lat), GC collections
# and their mean interval, pauses (gc.*), concurrent GC time, and the JVM's CPU with the GC threads'
# share of it.
LC_ALL=C awk -v label="$RUN_LABEL" -v heap="${MAX_HEAP:-?}" '
  function val(field) { return substr(field, index(field, "=") + 1) }
  /^OK=/ { err = val($2) }
  /^LATENCY_MS/ { lat = sprintf("lat.p50=%sms lat.p99=%sms lat.p99.9=%sms lat.p99.99=%sms lat.max=%sms", val($2), val($4), val($6), val($7), val($5)) }
  /^SEND_RATE/ { lag = val($4) }
  /^GC pauses=/ { gc = sprintf("gc.pauses=%s gc.max=%s gc.total=%s full=%s degenerated=%s", val($2), val($6), val($5), val($3), val($4)) }
  /^GC collections=/ { n = val($2); conc = val($3) }
  /^ZGC allocation stalls/ { stalls = sprintf(" stalls=%s stall.max=%s", val($4), val($6)) }
  /^jvmCpu=/ { jvm = val($1) + 0; gcCpu = val($2) + 0; window = val($3) + 0 }
  END {
    every = n > 0 ? sprintf("%.1fs", window / n) : "-"
    share = jvm > 0 ? sprintf("%.1f%%", 100 * gcCpu / jvm) : "n/a"
    printf "SUMMARY %s heap=%s err=%s %s sendLag=%sms | collections=%s every=%s %s concurrent=%s%s | jvmCpu=%.2fs gcCpu=%.2fs (%s)\n", \
      label, heap, err, lat, lag, n, every, gc, conc, stalls, jvm, gcCpu, share
  }' "$CLIENT_OUTPUT" "$RESULTS/gc-summary.txt" "$RESULTS/proc-cpu-summary.txt" | tee "$RESULTS/summary.txt"
echo ">>> [$SCENARIO] results in $RESULTS"

#!/usr/bin/env bash
# Runs one scenario at a fixed request rate once per JDK x collector x heap size and prints one
# SUMMARY line per run (details in each run's results/ directory, the full output in a log file).
# Answers "how often and how long does GC pause under typical load" rather than at full throughput.
#
# Usage: ./run-gc-matrix.sh [scenario]   (default fullproxy)
# Env: RATE (req/s, default 1000), MEASURE_SECONDS (default 60), WARMUP_SECONDS (default 10),
#      JDKS (default "21 25"), GCS (default "g1 zgc shen"), HEAPS (default "default 100m 250m 4g").
set -euo pipefail

SCENARIO=${1:-fullproxy}
RATE=${RATE:-1000}
MEASURE_SECONDS=${MEASURE_SECONDS:-60}
WARMUP_SECONDS=${WARMUP_SECONDS:-10}
JDKS=${JDKS:-"21 25"}
GCS=${GCS:-"g1 zgc shen"}
HEAPS=${HEAPS:-"default 100m 250m 4g"}
RG=${RG:-membrane-perftest-rg}
ADMIN_USER=${ADMIN_USER:-azureuser}
PT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$PT/vm-addresses.sh"
discover_vm_ips
SSH="ssh -o StrictHostKeyChecking=accept-new -o ServerAliveInterval=15 -o ServerAliveCountMax=4"

# provision.sh installs Temurin 21 only. Other versions come from the same Adoptium apt repo; the
# install would make the newest one the default `java`, so the default is set back to 21.
for jdk in $JDKS; do
  $SSH "$ADMIN_USER@$GATEWAY_PUB" "set -e; test -x /usr/lib/jvm/temurin-$jdk-jdk-amd64/bin/java && exit 0
    echo 'installing temurin-$jdk-jdk on the gateway'
    sudo apt-get install -y -q -o DPkg::Lock::Timeout=600 temurin-$jdk-jdk >/dev/null
    sudo update-alternatives --set java /usr/lib/jvm/temurin-21-jdk-amd64/bin/java"
done

gc_opts() { # jdk collector heap
  local opts
  case "$2" in
    g1) opts="" ;;
    # Generational ZGC is opt-in on 21 and the only mode from 24 on.
    zgc) opts="-XX:+UseZGC"; [[ "$1" == 21 ]] && opts="$opts -XX:+ZGenerational" ;;
    # Generational Shenandoah is production-ready from 25 on; 21 only has the single-generation mode.
    shen) opts="-XX:+UseShenandoahGC"; [[ "$1" != 21 ]] && opts="$opts -XX:ShenandoahGCMode=generational" ;;
    *) echo "Unknown collector $2" >&2; return 1 ;;
  esac
  [[ "$3" != default ]] && opts="$opts -Xmx$3"
  echo "${opts# }"
}

LOG="$PT/results/gc-matrix-$(date +%Y%m%d-%H%M%S)-$SCENARIO.log"
mkdir -p "$PT/results"
echo "Rate $RATE req/s, warmup ${WARMUP_SECONDS}s, measured ${MEASURE_SECONDS}s; full output in $LOG"
for jdk in $JDKS; do
  for gc in $GCS; do
    for heap in $HEAPS; do
      label="$jdk-$gc-$heap"
      opts=$(gc_opts "$jdk" "$gc" "$heap")
      echo "=== $(date +%H:%M:%S) $label: $opts" | tee -a "$LOG"
      if GC_OPTS="$opts" GATEWAY_JAVA_HOME="/usr/lib/jvm/temurin-$jdk-jdk-amd64" RUN_LABEL="$label" \
          LOAD_RATE="$RATE" LOAD_TOTAL=$((RATE * MEASURE_SECONDS)) LOAD_WARMUP=$((RATE * WARMUP_SECONDS)) \
          "$PT/run-scenario.sh" "$SCENARIO" >> "$LOG" 2>&1; then
        grep '^SUMMARY ' "$LOG" | tail -1
      else
        echo "FAILED $label (see $LOG)"
      fi
    done
  done
done

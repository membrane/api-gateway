#!/usr/bin/env bash
# CPU time of one process and of its garbage collector threads, in clock ticks, paired with UTC
# epoch milliseconds: "<epoch ms> <process ticks> <GC thread ticks> <GC control thread ms>".
# Usage: proc-cpu-sample.sh <pid>. Stops when the process exits; errors go to ~/proc-cpu-sample.err.
# No `set -e`: threads come and go between listing and reading them, which must not end sampling.
set -u
exec 2>>~/proc-cpu-sample.err
PID=$1
TICKS=$(getconf CLK_TCK)
# Thread names as Linux shows them (cut to 15 characters): G1 "GC Thread#0", "G1 Conc#0",
# "G1 Main Marker", "G1 Refine#0", "G1 Service"; ZGC "ZWorkerYoung#0", "ZWorkerOld#0", "ZDirector",
# "ZDriverMajor", ...; Shenandoah "Shenandoah GC T", "Shenandoah Cont", "Shenandoah Regu". The VM
# Thread, which also runs non-GC safepoint operations, is not counted. CPU of GC threads that have
# exited is lost, which is negligible because the JVM keeps them.
GC_THREAD='^(GC Thread|G1 |Shenandoah|Z[A-Z])'
# The subset that only decides when to collect and coordinates the cycle, rather than collecting.
# Shenandoah's control and regulator threads poll continuously, which costs CPU even when nothing is
# collected; reported separately so that fixed overhead is not mistaken for collection work.
GC_CONTROL_THREAD='^(G1 Service|G1 Main Marker|ZDirector|ZDriver|ZStat|ZUncommitter|ZUnmapper|Shenandoah Cont|Shenandoah Regu)'

while [[ -r /proc/$PID/stat ]]; do
  timestamp=$(date +%s%3N)
  # Process (all threads, including exited ones): utime and stime are fields 14 and 15 of the stat
  # line, i.e. the 12th and 13th after the closing parenthesis of the command name.
  stat=$(< "/proc/$PID/stat") || break
  read -r -a fields <<< "${stat##*) }"
  process=$(( fields[11] + fields[12] ))
  # GC threads: their tick counts are 0 at low load, because the kernel charges a whole tick only
  # to the thread running when it fires. schedstat's first field is the exact time on CPU in ns.
  gcNanos=0
  controlNanos=0
  for task in /proc/$PID/task/*; do
    comm=$(< "$task/comm") || continue
    [[ "$comm" =~ $GC_THREAD ]] || continue
    read -r onCpu _ < "$task/schedstat" || continue
    gcNanos=$(( gcNanos + onCpu ))
    [[ "$comm" =~ $GC_CONTROL_THREAD ]] && controlNanos=$(( controlNanos + onCpu ))
  done
  # Fourth column: the control threads' part of the third, in ms for precision.
  printf '%s %s %s %s\n' "$timestamp" "$process" $(( gcNanos * TICKS / 1000000000 )) $(( controlNanos / 1000000 ))
  sleep 1
done
echo "$(date -Is) stopped sampling pid $PID" >&2

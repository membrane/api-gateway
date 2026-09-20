#!/usr/bin/env bash
# Linux aggregate CPU counters, paired with UTC epoch milliseconds. Guest time is already
# included in user/nice, so the summary must not add the final guest fields again.
set -euo pipefail
while true; do
  timestamp=$(date +%s%3N)
  read -r cpu user nice system idle iowait irq softirq steal rest < /proc/stat
  printf '%s %s %s %s %s %s %s %s %s\n' \
    "$timestamp" "$user" "$nice" "$system" "$idle" "$iowait" "$irq" "$softirq" "$steal"
  sleep 1
done

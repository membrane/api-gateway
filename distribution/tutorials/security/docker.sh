#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 -c /path/to/config.yaml"
  exit 1
}

CFG=""
while getopts ":c:" opt; do
  case "$opt" in
    c) CFG="$OPTARG" ;;
    *) usage ;;
  esac
done

[[ -n "$CFG" ]] || usage
[[ -f "$CFG" ]] || { echo "ERROR: File not found: $CFG"; exit 2; }

if command -v realpath >/dev/null 2>&1; then
  CFG_ABS="$(realpath "$CFG")"
else
  CFG_ABS="$(cd "$(dirname "$CFG")" && pwd)/$(basename "$CFG")"
fi

PORT_ARGS=()
for p in {2000..2005}; do
  PORT_ARGS+=(-p "${p}:${p}")
done

exec sudo docker run --rm -it \
  "${PORT_ARGS[@]}" \
  -v "${CFG_ABS}:/opt/membrane/conf/apis.yaml:ro" \
  predic8/membrane

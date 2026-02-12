#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

cid="$(docker create -it -p 2000-2010:2000-2010 predic8/membrane:7.1.0 "$@")"

cleanup() {
  docker rm -f "$cid" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker cp "${DIR}/." "${cid}:/opt/membrane/"
docker start -a "$cid"

#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# Bind-mount the tutorial directory so config edits on the host (e.g. the Hot-Reload
# tutorial) are picked up live, instead of a one-time `docker cp` snapshot.
cid="$(docker create -it -p 2000-2010:2000-2010 -v "${DIR}:/opt/membrane/tutorial" -w /opt/membrane/tutorial --entrypoint /opt/membrane/membrane.sh predic8/membrane:7.6.0 "$@")"

cleanup() {
  docker rm -f "$cid" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker start -a "$cid"

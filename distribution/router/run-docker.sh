#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# Without an explicit -c the container falls back to its own baked-in
# conf/apis.yaml and silently ignores the one in this directory.
if [ "$#" -eq 0 ]; then
  set -- -c conf/apis.yaml
fi

# Ports 2000-2010 and 9000 (admin console) cover conf/apis.yaml and most tutorials.
# For a configuration listening elsewhere, publish it additionally, e.g.:
#   MEMBRANE_DOCKER_OPTS="-p 3128:3128" ./run-docker.sh -c tutorials/forward-proxy/10-Forward-Proxy.yaml
# Bind-mount this directory so config edits on the host are picked up live.
# Mounted at /opt/membrane/work, not /opt/membrane/conf: the image ships its own
# console-only conf/log4j2.xml that must not be shadowed.
cid="$(docker create -it -p 2000-2010:2000-2010 -p 9000:9000 ${MEMBRANE_DOCKER_OPTS:-} -v "${DIR}:/opt/membrane/work" -w /opt/membrane/work --entrypoint /opt/membrane/membrane.sh predic8/membrane:7.6.0 "$@")"

cleanup() {
  docker rm -f "$cid" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker start -a "$cid"

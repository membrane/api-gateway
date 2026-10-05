#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# Without an explicit -c the container falls back to its own baked-in
# conf/apis.yaml and silently ignores the one in this directory.
if [ "$#" -eq 0 ] && [ -f "${DIR}/conf/apis.yaml" ]; then
  set -- -c conf/apis.yaml
fi

# Ports 2000-2010, 8443 (TLS) and 9000 (admin console) are published.
# For a configuration listening elsewhere, publish it additionally, e.g.:
#   MEMBRANE_DOCKER_OPTS="-p 3128:3128" ./run-docker.sh -c 10-Forward-Proxy.yaml
# JAVA_OPTS from the host environment is passed on to the JVM in the container.
# Paths in it must be valid inside the container, relative ones resolve against /opt/membrane.
# Bind-mount this directory so config edits on the host are picked up live.
# Mounted at /opt/membrane/work, not /opt/membrane/conf: the image ships its own
# console-only conf/log4j2.xml that must not be shadowed.
cid="$(docker create -it -p 2000-2010:2000-2010 -p 8443:8443 -p 9000:9000 -e JAVA_OPTS ${MEMBRANE_DOCKER_OPTS:-} -v "${DIR}:/opt/membrane/work" -w /opt/membrane/work --entrypoint /opt/membrane/membrane.sh predic8/membrane:7.7.0 "$@")"

cleanup() {
  docker rm -f "$cid" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker start -a "$cid"

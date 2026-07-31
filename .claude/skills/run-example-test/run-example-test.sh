#!/usr/bin/env bash
#
# Run distribution example/tutorial integration tests.
#
# Usage:
#   run-example-test.sh <TestClass>       # run just that one IT class (simple name, FQN, or path), fast
#   run-example-test.sh -b <TestClass>    # force a distribution rebuild first, then run that test
#   run-example-test.sh                    # run the WHOLE example IT suite (always rebuilds first)
#
# IMPORTANT: the example/tutorial ITs (DistributionExtractingTestcase) unzip
# distribution/target/membrane-api-gateway-*.zip and start membrane from the
# UNZIPPED distribution -- not from the source tree. So the tests only see what
# was last built. You must build the distribution at the repo root with
#   mvn clean install -DskipTests
# before running them. This script does that for you (always for the full suite;
# for a single test only if the zip is missing or you pass -b).
#
# -DskipTests (not -Dmaven.test.skip) still compiles tests but skips running
# them, which also dodges the German-locale annot test failure on this machine.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"   # .claude/skills/run-example-test -> repo root
DIST="$ROOT/distribution"
TESTSRC="$DIST/src/test/java"

# Force the English locale for EVERY JVM involved: the test JVM, the failsafe fork,
# and the child membrane process the tests start via membrane.sh. On macOS the JVM
# ignores LANG/LC_ALL, so we must use -Duser.language/-Duser.country.
#   - LOCALE_OPTS is passed directly to the JVMs this script launches (java launcher,
#     and failsafe via -DargLine).
#   - JAVA_OPTS reaches the child membrane process: start_router.sh forwards $JAVA_OPTS
#     to its java call, and Process2 copies our environment into the child.
LOCALE_OPTS=(-Duser.language=en -Duser.country=US)
export JAVA_OPTS="${JAVA_OPTS:-} ${LOCALE_OPTS[*]}"

log() { printf '\033[1;34m[run-example-test]\033[0m %s\n' "$*" >&2; }

build_distribution() {
  log "Building distribution at repo root: mvn clean install -DskipTests"
  log "(the example/tutorial ITs unzip this .zip and start membrane from it)"
  ( cd "$ROOT" && mvn clean install -DskipTests )
}

# --- parse args: optional -b/--build flag + optional test class ---
FORCE_BUILD=0
TEST_ARG=""
for a in "$@"; do
  case "$a" in
    -b|--build) FORCE_BUILD=1 ;;
    -*) log "ERROR: unknown flag '$a'"; exit 2 ;;
    *)  if [[ -n "$TEST_ARG" ]]; then
          log "ERROR: more than one test class given ('$TEST_ARG' and '$a'); pass exactly one"; exit 2
        fi
        TEST_ARG="$a" ;;
  esac
done

# --- no test class given -> run the whole suite, ALWAYS rebuilding first ---
if [[ -z "$TEST_ARG" ]]; then
  build_distribution
  log "Running the full example IT suite via failsafe (slow; some tests need internet)."
  exec env -C "$ROOT" mvn -pl distribution failsafe:integration-test -DargLine="${LOCALE_OPTS[*]}"
fi

# --- single test: ensure the distribution exists / is fresh ---
if [[ "$FORCE_BUILD" == 1 ]]; then
  build_distribution
elif ! ls "$DIST"/target/membrane-api-gateway-*.zip >/dev/null 2>&1; then
  log "No distribution zip found."
  build_distribution
else
  log "Distribution zip present. Pass -b to rebuild it first --"
  log "needed if you edited the example's config/YAML/scripts or any upstream (e.g. core) module."
fi

# --- resolve a fully-qualified class name from a simple name, FQN, or path ---
simple="${TEST_ARG##*/}"; simple="${simple%.java}"; simple="${simple##*.}"
hits=()
while IFS= read -r h; do hits+=("$h"); done < <(find "$TESTSRC" -name "${simple}.java")
if [[ "${#hits[@]}" -eq 0 ]]; then
  log "ERROR: no test class matching '$TEST_ARG' under $TESTSRC"
  exit 2
fi
# If the simple name is ambiguous, try to narrow using the path/FQN the user gave.
if [[ "${#hits[@]}" -gt 1 ]]; then
  wanted="${TEST_ARG%.java}"; wanted="${wanted//.//}"   # FQN dots -> path; plain names unaffected
  narrowed=()
  for h in "${hits[@]}"; do
    [[ "$h" == *"/$wanted.java" ]] && narrowed+=("$h")
  done
  if [[ "${#narrowed[@]}" -eq 1 ]]; then
    hits=("${narrowed[@]}")
  else
    log "ERROR: '$simple' is ambiguous; ${#hits[@]} test classes match:"
    for h in "${hits[@]}"; do log "  ${h#"$TESTSRC"/}"; done
    log "Pass a more specific path or fully-qualified class name to disambiguate."
    exit 2
  fi
fi
rel="${hits[0]#"$TESTSRC"/}"; rel="${rel%.java}"
FQN="${rel//\//.}"
log "Resolved test class: $FQN"

# --- refresh compiled test classes (incremental, offline) so test edits are picked up ---
log "Compiling test classes (mvn -pl distribution test-compile -q -o)..."
( cd "$ROOT" && mvn -pl distribution test-compile -q -o )

# --- ensure the test classpath file exists (regenerate if missing) ---
CP_FILE="$DIST/target/cp.txt"
if [[ ! -f "$CP_FILE" ]]; then
  log "Generating test classpath (dependency:build-classpath)..."
  ( cd "$ROOT" && mvn -q -pl distribution dependency:build-classpath \
      -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test )
fi

CP="$DIST/target/classes:$DIST/target/test-classes:$(cat "$CP_FILE")"

# --- compile the launcher if missing or out of date ---
RUN_DIR="$DIST/target/run-one"
mkdir -p "$RUN_DIR"
if [[ ! -f "$RUN_DIR/RunOne.class" || "$SCRIPT_DIR/RunOne.java" -nt "$RUN_DIR/RunOne.class" ]]; then
  log "Compiling launcher..."
  javac -cp "$CP" -d "$RUN_DIR" "$SCRIPT_DIR/RunOne.java"
fi

# --- run the single test. CWD MUST be distribution/ -- the tests resolve ./target ---
log "Running $FQN ..."
exec env -C "$DIST" java "${LOCALE_OPTS[@]}" -cp "$RUN_DIR:$CP" RunOne "$FQN"

#!/usr/bin/env bash
# Runs a single core test class or package, bypassing the UnitTests isolation trap.
#
# In the core module, `mvn test -Dtest=SomeTest` does NOT isolate one class:
# Surefire is bound to UnitTests.java, a JUnit Platform @Suite with
# @SelectPackages("com.predic8"). The suite engine re-discovers and runs the
# whole package regardless of Surefire's -Dtest filter. This script instead
# drives the JUnit Platform Launcher directly against one class or package,
# via com.predic8.membrane.devtools.SingleTestRunner (core/src/test/java).
#
# Requires the reactor to already be built once (e.g. `mvn install -DskipTests`
# at the repo root) so core's dependencies resolve from the local repo.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <fully.qualified.TestClassName|package.name>" >&2
  echo "Example: $0 com.predic8.membrane.core.openapi.OpenAPIValidatorTest" >&2
  echo "Example: $0 com.predic8.membrane.core.openapi" >&2
  exit 1
fi

TARGET="$1"
LAST_SEGMENT="${TARGET##*.}"
if [[ "$LAST_SEGMENT" =~ ^[A-Z] ]]; then
  SELECTOR_KIND="class"
else
  SELECTOR_KIND="package"
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CORE_DIR="$REPO_ROOT/core"

CP_FILE="$(mktemp)"
trap 'rm -f "$CP_FILE"' EXIT

# One mvn invocation (one JVM/Maven bootstrap) compiles SingleTestRunner along
# with the rest of the test sources and writes out the test-scope classpath.
mvn -q -pl core test-compile dependency:build-classpath \
    -Dmdep.outputFile="$CP_FILE" -Dmdep.includeScope=test

# test-classes must come first: TestUtil.getPathFromResource walks up from the
# first classpath entry, so any other order breaks resource-loading tests.
RUN_CP="$CORE_DIR/target/test-classes:$CORE_DIR/target/classes:$(cat "$CP_FILE")"

# Some tests resolve resource paths relative to the process working directory
# (not just the classpath), the way Surefire runs with basedir=core as CWD.
cd "$CORE_DIR"
# Matches core/pom.xml's Surefire argLine: some large generated XML configs
# (e.g. Spring proxies.xml in tests) exceed JAXP's default entity-size limits.
java -Dfile.encoding=UTF-8 -Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 \
     -Duser.language=en -Duser.country=US -cp "$RUN_CP" \
     com.predic8.membrane.devtools.SingleTestRunner "$TARGET" "$SELECTOR_KIND"

#!/usr/bin/env bash
# Builds the app if needed (or with --rebuild), starts it on localhost and opens the browser.
# Extra JVM options can be passed via JAVA_OPTS, e.g. JAVA_OPTS=-Drmm.configDir=/tmp/rmm ./run.sh
set -euo pipefail
cd "$(dirname "$0")"
JAR=server/build/libs/rimworld-mod-manager.jar
[[ -f "$JAR" && "${1:-}" != "--rebuild" ]] || ./gradlew :server:shadowJar -q
exec java ${JAVA_OPTS:-} -Drmm.openBrowser=true -jar "$JAR"

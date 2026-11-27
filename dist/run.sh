#!/usr/bin/env sh
# Starts RimWorld Mod Manager on localhost and opens it in the browser (Linux, macOS). Needs Java 21 or newer.
cd "$(dirname "$0")" || exit 1
if ! command -v java >/dev/null 2>&1; then
  echo "Java not found. Install Java 21 or newer (e.g. from https://adoptium.net) and run this again." >&2
  exit 1
fi
exec java ${JAVA_OPTS:-} -Drmm.openBrowser=true -jar rimworld-mod-manager.jar "$@"

#!/bin/sh
# Dev mode: run the app with spring-boot:run (devtools enabled) and recompile
# whenever a file under src/ changes. Devtools sees the new classes in
# target/classes and restarts the app in a couple of seconds.
set -e

snapshot() {
  find src -type f -exec stat -c '%Y %s %n' {} + | sort | md5sum
}

(
  last=$(snapshot)
  while sleep 2; do
    now=$(snapshot)
    if [ "$now" != "$last" ]; then
      last=$now
      echo "[dev] change detected in src/, recompiling..."
      mvn -q -o -B compile && echo "[dev] compiled" || echo "[dev] compile FAILED"
    fi
  done
) &

exec mvn -B spring-boot:run

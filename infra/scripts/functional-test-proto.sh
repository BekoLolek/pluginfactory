#!/usr/bin/env bash
# Prototype driver for the in-server bot functional-test harness.
#
# Proves end-to-end that a network-isolated Paper server + an in-container
# Mineflayer bot can run a scenario against a real plugin and emit a
# machine-readable pass/fail. Phase B replaces this shell driver with
# FunctionalTestService (Java) + an AI-generated scenario.
#
# Prereqs on the host: docker, images `pluginfactory-build:latest` and
# `pluginfactory-test:latest` (build the latter from containers/Dockerfile.test).
#
# Usage:  infra/scripts/functional-test-proto.sh [path-to-repo-root]
set -euo pipefail

ROOT="${1:-/opt/pluginfactory}"
FIX="$ROOT/containers/test-fixtures/pingplugin"
WORK="$(mktemp -d)"
CT="pf-ftest-$$"

cleanup() { docker rm -f "$CT" >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT

echo "== 1. Build the known-good test plugin =="
cp -r "$FIX" "$WORK/pingplugin"
docker run --rm -u root -v "$WORK/pingplugin:/ws" -w /ws \
  --entrypoint mvn pluginfactory-build:latest -q -DskipTests package
JAR="$(ls "$WORK"/pingplugin/target/*.jar | head -1)"
echo "   built: $JAR"

echo "== 2. Boot isolated Paper + run the bot scenario =="
docker run -d --name "$CT" --network none --memory 4g \
  pluginfactory-test:latest tail -f /dev/null >/dev/null
docker cp "$JAR" "$CT:/server/plugins/plugin.jar"
docker cp "$FIX/scenario.js" "$CT:/work/scenario.js"

docker exec -e RCON_PASSWORD=pf-rcon-pass -e MC_VERSION=1.21.4 "$CT" sh -c '
  cd /server
  java -Xmx1536m -XX:+UseSerialGC -jar paper.jar --nogui > run.log 2>&1 &
  for i in $(seq 1 90); do grep -q "Done (" run.log && break; sleep 1; done
  grep -q "Done (" run.log || { echo SERVER_FAILED; tail -20 run.log; exit 1; }
  node /harness/run-test.js /work/scenario.js | sed -n "/PF_RESULT_BEGIN/,/PF_RESULT_END/p"
  pkill -f paper.jar 2>/dev/null || true
'
echo "== done =="

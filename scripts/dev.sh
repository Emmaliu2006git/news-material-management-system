#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
BACKEND_PID=""

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v java >/dev/null 2>&1 || fail "Java 21 or newer is required."
command -v curl >/dev/null 2>&1 || fail "curl is required to wait for the backend health endpoint."
[[ -x "${PROJECT_ROOT}/mvnw" ]] || fail "Maven wrapper is missing or not executable."

JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1)"
[[ -n "${JAVA_MAJOR}" && "${JAVA_MAJOR}" -ge 21 ]] || fail "Java 21 or newer is required."

if [[ -x "${PROJECT_ROOT}/.tools/node-v22.20.0-linux-x64/bin/node" ]]; then
  NODE_BIN="${PROJECT_ROOT}/.tools/node-v22.20.0-linux-x64/bin/node"
  NPM_MODE="cli"
  NPM_BIN="${PROJECT_ROOT}/.tools/node-v22.20.0-linux-x64/lib/node_modules/npm/bin/npm-cli.js"
elif command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1 \
    && [[ "$(command -v node)" != /mnt/* ]] && [[ "$(command -v npm)" != /mnt/* ]]; then
  NODE_BIN="$(command -v node)"
  NPM_MODE="command"
  NPM_BIN="$(command -v npm)"
else
  fail "Node.js 22+ and npm are required. Install Node 22 LTS or unpack it under .tools/node-v22.20.0-linux-x64."
fi

NODE_MAJOR="$(${NODE_BIN} -p 'process.versions.node.split(".")[0]')"
[[ "${NODE_MAJOR}" -ge 22 ]] || fail "Node.js 22 or newer is required."

run_npm() {
  if [[ "${NPM_MODE}" == "cli" ]]; then
    PATH="$(dirname "${NODE_BIN}"):${PATH}" "${NODE_BIN}" "${NPM_BIN}" "$@"
  else
    "${NPM_BIN}" "$@"
  fi
}

cleanup() {
  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
    kill "${BACKEND_PID}" 2>/dev/null || true
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

mkdir -p "${PROJECT_ROOT}/.local-data/storage"
run_npm --prefix "${PROJECT_ROOT}/frontend" ci

"${PROJECT_ROOT}/mvnw" -f "${PROJECT_ROOT}/pom.xml" -Dskip.frontend=true -DskipTests \
  spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--app.storage.local-root=${PROJECT_ROOT}/.local-data/storage --app.analysis.scheduling-enabled=true" &
BACKEND_PID="$!"

for attempt in $(seq 1 60); do
  if curl --silent --fail http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "Backend ready at http://localhost:8080"
    break
  fi
  kill -0 "${BACKEND_PID}" 2>/dev/null || fail "Backend exited before becoming ready."
  [[ "${attempt}" -lt 60 ]] || fail "Backend did not become ready within 60 seconds."
  sleep 1
done

echo "Responsive web app: http://localhost:5173"
run_npm --prefix "${PROJECT_ROOT}/frontend" run dev -- --host 0.0.0.0

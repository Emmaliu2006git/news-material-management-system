#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v java >/dev/null 2>&1 || fail "Java 21 or newer is required."
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

echo "[1/7] Installing locked frontend dependencies"
run_npm --prefix "${PROJECT_ROOT}/frontend" ci
echo "[2/7] Type-checking frontend"
run_npm --prefix "${PROJECT_ROOT}/frontend" run typecheck
echo "[3/7] Linting frontend"
run_npm --prefix "${PROJECT_ROOT}/frontend" run lint
echo "[4/7] Running frontend unit tests"
run_npm --prefix "${PROJECT_ROOT}/frontend" test
echo "[5/7] Building frontend"
run_npm --prefix "${PROJECT_ROOT}/frontend" run build
echo "[6/7] Testing and packaging backend with the built SPA"
"${PROJECT_ROOT}/mvnw" -f "${PROJECT_ROOT}/pom.xml" -Dskip.frontend=true verify
echo "[7/7] Installing Chromium and running packaged-app E2E"
export PLAYWRIGHT_BROWSERS_PATH="${PROJECT_ROOT}/.playwright-browsers"
run_npm --prefix "${PROJECT_ROOT}/frontend" exec -- playwright install chromium
run_npm --prefix "${PROJECT_ROOT}/frontend" run e2e

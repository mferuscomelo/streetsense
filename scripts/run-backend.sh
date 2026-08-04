#!/usr/bin/env bash
# Runs the Spring Boot backend (Java 26, --enable-preview already wired into
# the bootRun task). Dashboard: http://localhost:8080
#
# Usage:
#   scripts/run-backend.sh          # plain run
#   scripts/run-backend.sh --seed   # also seed synthetic crowd contributors

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/backend"

args=(bootRun)
if [[ "${1:-}" == "--seed" ]]; then
  echo "+ ./gradlew bootRun -Pseed=true"
  args+=("-Pseed=true")
else
  echo "+ ./gradlew bootRun"
fi

exec ./gradlew "${args[@]}"

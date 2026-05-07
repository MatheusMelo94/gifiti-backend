#!/usr/bin/env bash
# run-local.sh — start gifiti-backend locally with the `local` Spring profile.
# Cite: devops-conventions.md § Project Setup
#
# Usage:   ./run-local.sh
# Assumes: a populated .env file in the repo root (copy from .env.example).
# Default profile: local. Override by exporting SPRING_PROFILES_ACTIVE before
# running this script.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${REPO_ROOT}"

if [[ ! -f .env ]]; then
  echo "ERROR: .env not found at ${REPO_ROOT}/.env" >&2
  echo "Copy .env.example to .env and fill in real values." >&2
  exit 1
fi

# Source .env safely: ignore comments and blank lines, export the rest.
# `set -a` auto-exports all variable assignments until `set +a`.
set -a
# shellcheck disable=SC1091
source .env
set +a

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

echo "Starting gifiti-backend (profile=${SPRING_PROFILES_ACTIVE}, port=${PORT:-8080})..."
exec ./mvnw spring-boot:run

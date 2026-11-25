#!/usr/bin/env bash
# Build-Skript für Kreisel-Kämpfe – wird lokal UND von der CI-Pipeline benutzt.
#
#   ./scripts/build.sh            baut Backend und Frontend
#   ./scripts/build.sh backend    nur Backend  (Maven: kompilieren, testen, JAR paketieren)
#   ./scripts/build.sh frontend   nur Frontend (npm: Lint, Type-Check, Tests, Vite-Build)
#
# Ergebnisse:
#   src/backend/target/BeybladeBackend-*.jar   lauffähiges Spring-Boot-JAR
#   src/frontend/dist/                          statische Web-App
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-all}"

build_backend() {
  echo "==> Backend bauen (Maven)"
  cd "$ROOT/src/backend"
  mvn -B --no-transfer-progress verify
  ls -1 target/*.jar
}

build_frontend() {
  echo "==> Frontend bauen (npm)"
  cd "$ROOT/src/frontend"
  npm ci --no-audit --no-fund
  npm run ci
  du -sh dist
}

case "$TARGET" in
  backend)  build_backend ;;
  frontend) build_frontend ;;
  all)      build_backend; build_frontend ;;
  *) echo "Unbekanntes Ziel: $TARGET (erlaubt: backend | frontend | all)" >&2; exit 2 ;;
esac

echo "==> Build '$TARGET' erfolgreich"

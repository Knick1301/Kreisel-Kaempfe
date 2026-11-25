#!/usr/bin/env bash
# Smoke-Test für die Testumgebung (docker compose).
# Prüft, ob Frontend, Backend und die Verbindung dazwischen funktionieren.
#
#   ./scripts/smoke-test.sh [BASIS-URL]     Standard: http://localhost:8081
set -euo pipefail

BASE="${1:-http://localhost:8081}"
TIMEOUT="${SMOKE_TIMEOUT:-180}"

fail() { echo "FEHLER: $*" >&2; exit 1; }
ok()   { echo "  ok  $*"; }

echo "==> Warte bis $BASE/api erreichbar ist (max. ${TIMEOUT}s)"
start=$(date +%s)
# Ohne Token antwortet das Backend mit 401 – das heißt: nginx UND Spring laufen.
until [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/auth/me")" = "401" ]; do
  if [ $(( $(date +%s) - start )) -ge "$TIMEOUT" ]; then
    fail "Backend antwortet nach ${TIMEOUT}s noch nicht"
  fi
  sleep 3
done
ok "Backend über nginx erreichbar ($(( $(date +%s) - start ))s)"

echo "==> Frontend"
html=$(curl -fsS "$BASE/") || fail "Startseite nicht erreichbar"
grep -q '<div id="app">' <<<"$html" || fail "Startseite enthält kein Vue-Mount-Element"
ok "Startseite wird ausgeliefert"
curl -fsS -o /dev/null "$BASE/rangliste" || fail "SPA-Route /rangliste liefert Fehler"
ok "SPA-Routing (/rangliste -> index.html)"

echo "==> Backend-API"
user="ci_$(date +%s | tail -c 9)"
resp=$(curl -fsS -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$user\",\"password\":\"Smoke1234\"}") || fail "Registrierung fehlgeschlagen"
token=$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' <<<"$resp")
[ -n "$token" ] || fail "Kein Token in der Antwort: $resp"
ok "Registrierung von '$user'"

me=$(curl -fsS "$BASE/api/auth/me" -H "Authorization: Bearer $token") || fail "/api/auth/me fehlgeschlagen"
grep -q "\"$user\"" <<<"$me" || fail "/api/auth/me liefert falschen Benutzer: $me"
ok "Anmeldung per Token"

for kind in fusion-wheels spin-tracks tips; do
  parts=$(curl -fsS "$BASE/api/parts/$kind" -H "Authorization: Bearer $token") || fail "/api/parts/$kind fehlgeschlagen"
  grep -q '"id"' <<<"$parts" || fail "/api/parts/$kind ist leer (Seeder nicht gelaufen?)"
  ok "Kreiselteile '$kind' aus der Datenbank geladen"
done

echo "==> Testumgebung läuft korrekt"

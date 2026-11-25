# Kreisel-Kämpfe

[![CI/CD](https://github.com/Knick1301/Kreisel-Kaempfe/actions/workflows/ci.yml/badge.svg)](https://github.com/Knick1301/Kreisel-Kaempfe/actions/workflows/ci.yml)

Ein Online-Multiplayer-Spiel, in dem Spieler ihre eigenen Kampfkreisel zusammenstellen und in einer Arena gegeneinander antreten.

Projekt im Rahmen der Vorlesung **Software Engineering** (3. und 4. Semester) an der DHBW Karlsruhe.

## Projektidee

- Spieler stellen ihren Kreisel aus Einzelteilen zusammen (z. B. Ring, Disc, Spitze), die unterschiedliche Werte haben
- Kämpfe finden online in Echtzeit gegen andere Spieler statt
- Ergebnisse, Ranglisten und Spielerprofile werden in einer Datenbank gespeichert

## Team & Rollen

| Name | Scrum-Rolle | GitHub |
|------|-------------|--------|
| Hannes Bunse | Product Owner | [@hannesarbeit8-hash](https://github.com/hannesarbeit8-hash) | |
| Joni Strecker | Scrum Master | [@js1js1js1](https://github.com/js1js1js1) | |
| Yannick Weber | Development Team | [@Knick1301](https://github.com/Knick1301) | |
| Maxi Bühner | Development Team | [@maxiii1232](https://github.com/maxiii1232) | |
| Tim Reichert | Development Team | [@TiRe0110](https://github.com/TiRe0110) | |
| Kerem Prato | Documentation | [@prkeMeiko](https://github.com/prkeMeiko) | |

## Tech-Stack

Vue 3 + Vite (Frontend), Java 21 / Spring Boot (Backend), H2-Datenbank, Vitest + JUnit (Tests), GitHub Actions + Docker (CI/CD)

## CI/CD

Bei jedem Push und bei jedem Pull Request nach `main` läuft die Pipeline [`.github/workflows/ci.yml`](.github/workflows/ci.yml):

| Job | Was passiert |
|-----|--------------|
| **backend** | `./scripts/build.sh backend` – Maven kompiliert, testet und paketiert das Spring-Boot-JAR |
| **frontend** | `./scripts/build.sh frontend` – npm: Lint, Type-Check, Vitest, Vite-Build |
| **deploy-test** | baut beide Docker-Images, startet die Testumgebung mit `docker compose` und prüft sie mit `./scripts/smoke-test.sh` |
| **publish** | nur auf `main`: Images nach `ghcr.io` (GitHub Container Registry) |

JAR und `dist/` werden als Build-Artefakte am Workflow-Lauf archiviert.

**Lokal bauen:** `./scripts/build.sh` (oder `backend` / `frontend`)

**Testumgebung lokal starten:**

```bash
docker compose up -d --build      # Frontend: http://localhost:8081, Backend: http://localhost:8080
./scripts/smoke-test.sh           # prüft Frontend, API und Datenbank
docker compose down -v            # stoppen und Daten löschen
```

**Pre-commit-Hook** (prüft den Frontend-Linter vor jedem Commit), einmalig aktivieren: `git config core.hooksPath scripts/hooks`

## Projektorganisation

| Was | Wo |
|-----|----|
| Quellcode & Versionsverwaltung | dieses Repository |
| Scrum Board (Backlog, Sprints) | GitHub Projects |
| User Stories & Bugs | GitHub Issues |
| Wöchentlicher Blog, Dailys, Retros | GitHub Discussions |
| Projektdokumentation | [`docs/`](docs/) |
| Stundenaufzeichnung | [`docs/stunden/`](docs/stunden/) |

Wie wir arbeiten (Branches, Commits, Definition of Done): siehe [CONTRIBUTING.md](CONTRIBUTING.md).

## Projektstruktur

```
.
├── docs/                 Projektdokumentation
│   ├── anforderungen/    SRS, Use Cases, User Stories, Mockups
│   ├── architektur/      UML-Diagramme, Architekturentscheidungen
│   ├── tests/            Teststrategie, Testberichte
│   ├── risiken/          Risikomanagement
│   └── stunden/          Stundenaufzeichnung
├── src/
│   ├── backend/          Spring-Boot-Backend (Maven, Dockerfile)
│   └── frontend/         Vue-Frontend (npm, Dockerfile, nginx.conf)
├── scripts/              Build-Skript, Smoke-Test, Git-Hooks
├── docker-compose.yml    Testumgebung
└── .github/              Issue- und PR-Templates, CI/CD-Workflows
```

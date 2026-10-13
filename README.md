# Kreisel-Kämpfe

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

Vue + Tailwind.css, Java-Backend, H2-Database, Test-Framework und CI/CD stehen noch aus_

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
├── src/                  Quellcode (Struktur folgt mit dem Tech-Stack)
└── .github/              Issue- und PR-Templates, CI/CD-Workflows
```

# ADR-2: Lobbyzustand zentral und konsistent verändern

## Kontext

Kreisel-Kämpfe erlaubt das Erstellen und Suchen/Betreten von Lobbys. Beim Mehrspielerbetrieb können zwei Spieler gleichzeitig denselben letzten freien Platz belegen wollen. Ohne koordinierte Zustandsänderung könnte eine Lobby überfüllt oder uneinheitlich angezeigt werden.

## Entscheidung

1. Der **`LobbyService`** ist die zentrale fachliche Instanz für die verbindlichen Zustandsänderungen einer Lobby.
2. Der Service prüft die erlaubte Teilnehmerzahl und nimmt den Spieler **atomar** in die Lobby auf, d. h. Prüfung und Aufnahme bilden einen unteilbaren Vorgang.
3. Wird der letzte Platz zwischenzeitlich belegt, erhält die zweite Anfrage eine klare Ablehnung statt eines widersprüchlichen Zustands.
4. Der konkrete Mechanismus richtet sich nach dem später festgelegten Speicherort: Transaktion bei gemeinsam genutzter Datenbank oder geeignete Synchronisierung bei einem einzelnen Serverprozess. Ein nur lokaler Clientzustand gilt nicht als verbindliche Quelle.

## Alternativen

- **Lobbyplätze nur im jeweiligen Client prüfen:** Einfach, aber gleichzeitig anfragende Clients können unterschiedliche Zustände sehen. **Verworfen.**
- **Zentrale Prüfung ohne atomare Aktualisierung:** Spart Implementierungsaufwand, verhindert aber den eigentlichen Parallelitätsfehler nicht. **Verworfen.**

## Konsequenzen

**Positiv:** Keine Überbelegung und nachvollziehbare Rückmeldungen; ein einziger Ort für die Lobby-Regeln.  
**Negativ:** Gleichzeitige Änderungen an derselben Lobby müssen koordiniert werden; bei späterem Betrieb auf mehreren Serverinstanzen ist ein gemeinsamer konsistenter Speicher erforderlich.

## Prüfung / Akzeptanzkriterium

In 100 Tests mit zwei nahezu gleichzeitigen Beitritten auf einen letzten freien Platz wird **genau einer** angenommen. Die Lobby überschreitet ihre maximale Teilnehmerzahl nie.

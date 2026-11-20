# Architekturstrategien und Entscheidungen – Kreisel-Kämpfe

**Stand:** 20.11.2026  
**Bezug:** [Qualitätsbaum](qualitaetsbaum.md)

## Schritt 1: Anforderungen und Qualitätsmerkmale

Wir haben die Qualität von Kreisel-Kämpfe anhand von Leistungsfähigkeit, Zuverlässigkeit, Änderbarkeit, Sicherheit und Benutzbarkeit betrachtet. Daraus sind zehn überprüfbare Qualitätsszenarien entstanden (QS-01 bis QS-10). Die tabellarische Bewertung mit geschäftlichem Nutzen (GNut) und technischem Risiko (TRis) steht im Qualitätsbaum.

## Schritt 2: Strategien für die zwei wichtigsten Szenarien

### QS-02: Gleichzeitigem Lobby-Beitritt widerspruchsfrei begegnen

**Problem:** Zwei Spieler können fast gleichzeitig den letzten Platz einer Lobby anfordern. Wenn jeder unabhängig prüft, ob ein Platz frei ist, könnte die maximale Teilnehmerzahl überschritten werden.

**Strategie:**
1. **Eine zentrale Stelle für den Lobbyzustand:** Änderungen an der Teilnehmerliste erfolgen über den `LobbyService`; die `LobbyView` darf den verbindlichen Zustand nicht selbst ändern.
2. **Prüfen und Eintragen als unteilbarer Schritt:** Die Aktion „Platz frei? → Spieler hinzufügen“ wird atomar ausgeführt. Bei gemeinsamer Datenbank eignet sich eine Transaktion; bei nur einem Prozess kann zunächst eine geeignete Synchronisierung pro Lobby genutzt werden.
3. **Klare Rückmeldungen:** Der `LobbyController` gibt entweder „Beitritt erfolgreich“ oder „Lobby voll / nicht mehr verfügbar“ zurück.
4. **Paralleltest:** Zwei Anfragen auf den letzten freien Platz gleichzeitig ausführen; 100 Wiederholungen müssen ohne Überbelegung bestehen.

**Geeignete Muster:** Service Layer; zentrale Zustandsverwaltung; Repository Pattern **falls** Lobbyzustände in einem Repository persistiert werden.  
**Abwägung:** Weniger parallele Schreibzugriffe auf eine Lobby, dafür korrekter Zustand. Für das Studienprojekt ist Konsistenz wichtiger als maximaler Durchsatz.

**Entscheidung:** [ADR-0002](adr/0002-konsistenter-lobbybeitritt.md)

### QS-03: Kampfergebnisse exakt einmal berücksichtigen

**Problem:** Wird nach einem Netzwerkfehler dasselbe Kampfergebnis erneut übermittelt, kann die Rangliste ohne Schutz doppelt verändert werden.

**Strategie:**
1. **Eindeutige Kampf-ID:** Jeder abgeschlossene Kampf hat eine eindeutige ID.
2. **Ergebnisse zentral prüfen:** Die Ergebnisverarbeitung erfolgt in einer dafür zuständigen Service-Komponente, nicht durch frei manipulierbare Änderungen in der Ranglistenansicht.
3. **Idempotente Speicherung:** Zu einer Kampf-ID wird höchstens ein endgültiges Ergebnis gespeichert. Eine zweite Übermittlung derselben ID erzeugt keinen zweiten Eintrag.
4. **Rangliste aus gültigen Ergebnissen ableiten:** Die Rangliste wird aus den bestätigten Ergebnissen berechnet oder nur im selben kontrollierten Verarbeitungsschritt aktualisiert.
5. **Duplikattests:** Dasselbe Ergebnis mehrfach einsenden und prüfen, ob es exakt einmal zählt.

**Geeignete Muster:** Service Layer; Repository Pattern; Idempotenz durch eindeutigen Schlüssel; kontrollierte Ergebnisverarbeitung.  
**Abwägung:** Zusätzliche Prüfungen und eindeutige Kennungen sind notwendig, dafür bleiben Rangliste und Turnierstände nachvollziehbar.

**Entscheidung:** [ADR-0003](adr/0003-idempotente-kampfergebnisse.md)

## Schritt 3: Architekturentscheidungen und Entwurfsmuster

| Entscheidung / Muster | Umsetzung im Projekt | Begründung | Dokumentation |
|---|---|---|---|
| **Schichtenartige Trennung: View → Controller → Service → Repository** | Bereits in den Sequenzdiagrammen z. B. mit `BaukastenView`, `PartController`, `PartService`, `FusionWheelRepository` erkennbar. Bei Lobbys: `LobbyView`, `LobbyController`, `LobbyService`. | Oberfläche, Geschäftsregeln und Datenzugriff bleiben klar getrennt; erleichtert Wartung und Tests. | [ADR-0001](adr/0001-schichtenarchitektur.md) |
| **Lobbyzustand zentral verwalten** | `LobbyService` als einzige fachliche Stelle für Erstellen, Beitreten, Verlassen und Startfreigabe. | Verhindert widersprüchliche Belegungszustände. | [ADR-0002](adr/0002-konsistenter-lobbybeitritt.md) |
| **Kampfergebnisse idempotent speichern** | Ergebnis pro eindeutiger Kampf-ID höchstens einmal übernehmen; Rangliste ausschließlich aus gültigen Ergebnissen. | Vermeidet doppelte Punkte und fehlerhafte Platzierungen. | [ADR-0003](adr/0003-idempotente-kampfergebnisse.md) |

### Bezug zur vorhandenen Softwarearchitektur

Die neue Planung **ergänzt** die bisherigen UML-Diagramme und soll sie nicht widersprechen: Der Baukasten behält seine Aufteilung in View, Controller, Service und Teil-Repository. Die Lobby verwendet ihre bisherigen Klassen `LobbyView`, `LobbyController` und `LobbyService`. Für die Rangliste existiert bereits ein betrachteter Anwendungsfall mit einem Zustand „Noch keine Kämpfe“. Für die konkrete Ergebnisablage kann ein passendes Repository **neu vorgesehen werden**, sofern es noch nicht existiert.

Eine endgültige Entscheidung über konkrete Servertechnik, Datenbank, Netzwerkprotokoll oder Entwicklungsframework ist aus den vorliegenden Screenshots **nicht sicher ableitbar** und wird deshalb hier bewusst nicht erfunden.

## Überprüfung im Projekt

| Qualitätsszenario | Praktischer Test | Erfolgskriterium |
|---|---|---|
| QS-02 | 100-mal zwei parallele Beitrittsanfragen auf einen letzten freien Platz | Genau ein Beitritt erfolgreich; nie überbelegt |
| QS-03 | 100-mal eine Kampf-ID doppelt als fertiges Ergebnis verarbeiten | Je Kampf-ID ein Datensatz; eine Ranglistenwirkung |
| QS-05 | Ein neues Fusion Wheel mit Werten anlegen | Max. 2 h; keine Änderungen an Lobby/Rangliste |
| QS-08 | 5 neuen Testpersonen den Baukasten geben | Mind. 4 erstellen Kreisel ohne Hilfe in 3 min |

**Offene Teamabstimmung:** Zielwerte, Speicherort des Lobbyzustands und die konkrete Stelle der Ergebnisverwaltung vor der endgültigen ADR-Freigabe bestätigen. Die ADRs 0002 und 0003 sind deshalb als **vorgeschlagen** gekennzeichnet.

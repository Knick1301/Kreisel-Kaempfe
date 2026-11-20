# Architekturentscheidungen

## Priorisierte Qualitätsszenarien

**QS-02 – Gleichzeitiger Lobbybeitritt:** Zwei Spieler wollen den letzten freien Platz belegen. Der `LobbyService` prüft und vergibt Plätze in einem unteilbaren Vorgang. **Ziel:** Nur ein Spieler wird aufgenommen.

**QS-03 – Doppelte Kampfergebnisse:** Ein Kampfergebnis wird mehrfach übertragen. Jedes Ergebnis wird anhand einer eindeutigen Kampf-ID nur einmal gewertet. **Ziel:** Keine doppelten Ranglistenpunkte.

## Architekturentscheidungen

| Entscheidung | Begründung | ADR |
|---|---|
| **View → Controller → Service → Repository** | Klare Trennung von Oberfläche, Logik und Daten; erleichtert Änderungen und Tests. |
| **Lobbyverwaltung im `LobbyService`** | Verhindert Überbelegung bei gleichzeitigen Beitritten. |
| **Ergebnisse über eindeutige Kampf-IDs speichern** | Verhindert doppelte Wertungen in der Rangliste. |

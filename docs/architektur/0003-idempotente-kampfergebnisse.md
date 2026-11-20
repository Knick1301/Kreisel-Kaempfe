# ADR-0003: Kampfergebnisse nur einmal speichern und werten

- **Datum:** 20.11.2026
- **Status:** Vorgeschlagen – vom Team zu bestätigen
- **Qualitätsszenario:** QS-03 (hoher geschäftlicher Nutzen, hohes technisches Risiko)

## Kontext

Kreisel-Kämpfe umfasst Kämpfe, eine Rangliste und einen Turnierbereich. Ein Ergebnis könnte durch einen Wiederholungsversuch nach einer unterbrochenen Übertragung mehrfach eintreffen. Würde jeder Eingang direkt Punkte auslösen, wären Rangliste und Turnierstand falsch.

## Entscheidung

1. Jeder Kampf bekommt eine **eindeutige Kampf-ID**.
2. Die fachliche Ergebnisverarbeitung erfolgt in einem zuständigen **Service**. Ein Client darf nicht selbst beliebige Ranglistenpunkte festlegen.
3. Zu jeder Kampf-ID wird **höchstens ein** endgültiges Ergebnis im Ergebnisspeicher übernommen. Eine erneute Verarbeitung derselben ID ist ohne zusätzliche Punktewirkung möglich (**idempotent**).
4. Die Rangliste verwendet nur akzeptierte Ergebnisse; falls Punkte zusätzlich gespeichert werden, muss ihre Aktualisierung konsistent mit der Speicherung des Ergebnisses erfolgen.
5. Die Speicherung soll die Eindeutigkeit technisch absichern, beispielsweise mit einem eindeutigen Schlüssel auf der Kampf-ID.

## Alternativen

- **Bei jedem Ergebnisempfang Punkte erhöhen:** Einfach, kann bei doppelten Übertragungen falsche Ergebnisse erzeugen. **Verworfen.**
- **Dubletten nur in der Oberfläche verbergen:** Die Anzeige wirkt eventuell korrekt, die gespeicherten Punkte sind trotzdem falsch. **Verworfen.**

## Konsequenzen

**Positiv:** Verlässliche Rangliste; wiederholtes Senden bei Netzwerkproblemen ist ungefährlich; Ergebnisse sind nachvollziehbar.  
**Negativ:** Zusätzliche eindeutige Kennung, Prüfung und gegebenenfalls transaktionale Speicherung nötig.

## Prüfung / Akzeptanzkriterium

100 wiederholte Einsendungen eines bereits registrierten Kampfergebnisses verändern die Anzahl der gespeicherten Ergebnisse und den Ranglistenstand nicht erneut. Für jeden abgeschlossenen Kampf zählt genau ein Ergebnis.

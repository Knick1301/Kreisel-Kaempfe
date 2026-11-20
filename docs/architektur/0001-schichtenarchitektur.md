# ADR-1: Oberfläche, Logik und Datenzugriff trennen

## Kontext

Kreisel-Kämpfe bietet unterschiedliche Funktionen: Kreisel zusammenstellen, Lobby verwalten, Kämpfe durchführen, Rangliste und Turnier/Freunde-Funktionen anzeigen. Werden Oberfläche, Logik und Speicherung vermischt, werden Änderungen und Tests unnötig schwierig. In unseren bisherigen Sequenzdiagrammen kommt bereits eine Trennung vor, zum Beispiel `BaukastenView → PartController → PartService → FusionWheelRepository`.

## Entscheidung

Wir verwenden eine **schichtenartige Architektur** mit folgenden Zuständigkeiten:

- **View:** Stellt Daten dar und nimmt Benutzereingaben entgegen.
- **Controller:** Nimmt Aktionen entgegen und ruft die passenden Services auf.
- **Service:** Enthält die fachlichen Regeln (z. B. Kreisel prüfen, Lobbyplätze vergeben, Ergebnisse verarbeiten).
- **Repository:** Kapselt den Zugriff auf dauerhaft gespeicherte Daten, falls die Funktion solche Daten benötigt.

Die View greift nicht direkt auf Repositories zu. Fachliche Regeln liegen nicht in der Oberfläche.

## Verworfene Alternative

**Alle Aufgaben direkt in der View umsetzen.** Das wäre anfangs kürzer, verursacht aber starke Abhängigkeiten und erschwert das Testen.

## Konsequenzen

**Positiv:** Klarer Aufbau; Änderungen am Baukasten müssen die Lobby möglichst nicht beeinflussen; Geschäftsregeln sind unabhängig von der Oberfläche testbar.  
**Negativ:** Mehr Klassen und Schnittstellen als bei einer sehr kleinen Ein-Datei-Anwendung.

## Nachweis

UML-Sequenzdiagramme und Klassendiagramm entsprechen dieser Aufteilung. Fachliche Services können in Tests ohne die View aufgerufen werden.

# Storage

`Storage` ist ein Minecraft-Plugin fuer Paper, das ein persoenliches, erweiterbares Lager-System auf Basis spezieller Shulker-Boxen bereitstellt. Statt viele Kisten, Sortieranlagen oder Enderchest-Workarounds zu bauen, bekommt jeder Spieler ein globales Lager, das ueber einen `Lager Shulker` erreichbar ist. Ein einzelner Shulker dient dabei als Bedienoberflaeche fuer Schnellzugriff, Vollansicht, automatische Einlagerung, Einsaug-Funktionen, Nachfuellen und Freigaben fuer andere Spieler.

Items koennen manuell eingelagert, beim Abbauen direkt ins Lager verschoben oder ueber einen konfigurierbaren Einsaug-Bereich automatisch aufgenommen werden. Gleichzeitig bleibt das System spielnah: Slots und Kapazitaet sind upgradbar, der Lager-Shulker muss gecraftet werden, und viele Funktionen werden direkt ueber Inventar-GUIs im Spiel bedient.

## Kurzfassung

`Storage` ist ein globales, GUI-gesteuertes Shulker-Lagersystem fuer Paper-Server. Es kombiniert Schnellzugriff, Sortierung, Suche, automatische Einlagerung, Auto-Nachfuellen, Einsaugen, EXP-Speicherung, Freigaben und Upgrades in einem einzigen Ingame-Workflow.

## Voraussetzungen

- Paper 1.21.1 oder kompatible Version
- Java 17
- Berechtigung `storage.use` fuer die Nutzung des Systems

## Installation

1. Plugin bauen oder die fertige JAR verwenden.
2. Die Plugin-JAR in den `plugins/`-Ordner des Servers legen.
3. Server starten oder neu laden.
4. Optional die Datei `plugins/Storage/config.yml` anpassen.

## Speicherung der Daten

Standardmaessig speichert das Plugin alle Daten lokal in einer eingebetteten Nitrite-Datenbank. Optional kann MySQL aktiviert werden.

### Nitrite-Standard

- Lagerdaten einzelner Spieler werden lokal in `plugins/Storage/storage.db` gespeichert.
- Einstellungen einzelner Lager-Shulker werden ebenfalls dort gespeichert.
- Keine weitere Konfiguration noetig.

### MySQL optional

In der `config.yml` kann MySQL aktiviert werden:

```yml
storage:
  mysql:
    enabled: true
    host: "127.0.0.1"
    port: 3306
    database: "storage"
    username: "root"
    password: "change_me"
```

Wenn MySQL nicht erreichbar ist oder deaktiviert bleibt, nutzt das Plugin automatisch Nitrite. Vorhandene JSON-Dateien werden beim ersten Zugriff weiterhin als Legacy-Fallback gelesen und nach Nitrite uebernommen.

## Wie man den Lager-Shulker bekommt

Der zentrale Gegenstand des Plugins ist der `Lager Shulker`.

- Rezept: `Shulker Box` + `Ender Chest`
- Das Rezept wird Spielern automatisch bekannt gemacht.
- Jeder gecraftete Lager-Shulker erhaelt eine eigene interne ID.

## Grundprinzip der Nutzung

Die Bedienung ist bewusst an Minecraft-Interaktionen angepasst:

- Einen Lager-Shulker platzierst du nur, wenn du dabei schleichst.
- Einen platzierten Lager-Shulker oeffnest du ebenfalls im Schleichen per Rechtsklick.
- Das geoeffnete Inventar ist nicht einfach ein normaler Shulker, sondern die Steuerzentrale fuer dein globales Lager.
- Wird ein Lager-Shulker abgebaut, versucht das Plugin den Inhalt des platzierten Shulkers direkt ins globale Lager zu verschieben.

## Erste Schritte

1. Craft einen `Lager Shulker`.
2. Schleiche und platziere ihn.
3. Schleiche und rechtsklicke auf den platzierten Shulker.
4. Im Hauptmenue kannst du Kategorien, Schnellzugriffe und weitere Funktionen einrichten.
5. Lege ueber den Hopper-Button Items aus dem Cursor oder dem Inventar ins Lager.
6. Oeffne die Lageransicht, um Inhalte zu durchsuchen, zu sortieren und wieder zu entnehmen.

## Funktionsuebersicht

### 1. Globales Spielerlager

Jeder Spieler besitzt ein globales Lager, das nicht an einen einzelnen Blockinhalt gebunden ist. Mehrere Lager-Shulker eines Besitzers greifen auf denselben Lagerbestand zu.

- Startwert: `27` freigeschaltete Slots
- Startkapazitaet: `3456` Items insgesamt
- Gleiche Itemtypen werden zusammengefasst
- Eigene Item-Metadaten bleiben erhalten, weil intern ganze ItemStacks gespeichert werden

Wichtig:

- Slots bestimmen, wie viele verschiedene Itemtypen gespeichert werden koennen
- Kapazitaet bestimmt, wie viele Items insgesamt eingelagert werden koennen

### 2. Schnellzugriff im Hauptmenue

Das Hauptmenue (`QuickSlotsView`) ist der direkte Zugriff auf dein Lager.

- Die oberen 9 Slots stehen fuer Kategorien
- Die Slots darunter koennen mit konkreten Materialien belegt werden
- Linksklick auf einen belegten Schnellslot: 1 Item entnehmen
- Rechtsklick: 1 Stack entnehmen
- `Q` bzw. Drop-Klick: Inventar mit diesem Material auffuellen, soweit Platz vorhanden ist
- Shift-Rechtsklick auf einen Schnellslot: Belegung loeschen
- Mittelklick oder Klick mit Cursor-Item auf einen leeren Slot: Material zuweisen

Das ist besonders praktisch fuer Baumaterial, Werkstoffe oder Farmdrops, die man staendig braucht.

### 3. Kategorien fuer Ordnung im Lager

Das Plugin verwaltet 9 Kategorien. Diese koennen fuer eine bessere Organisation individuell angepasst werden.

- Jede Kategorie hat einen Namen
- Jede Kategorie hat ein eigenes Icon
- Materialien koennen Kategorien fest zugewiesen werden
- In der Lageransicht kann nach Kategorien gefiltert werden

Bedienung:

- Linksklick auf eine Kategorie im Hauptmenue: Lageransicht mit diesem Filter oeffnen
- Rechtsklick auf eine Kategorie: Namen per Amboss-GUI umbenennen
- Mittelklick mit einem Item am Cursor: Kategorie-Icon auf dieses Item setzen

In der Lageransicht:

- Zahlentasten `1` bis `9`: Material direkt einer Kategorie zuweisen
- Taste fuer Offhand-Swap (`F`): Material aus Kategorien entfernen
- Shift-Links / Shift-Rechts: Kategorie zyklisch vor oder zurueckschalten

### 4. Vollstaendige Lageransicht

Die Lageransicht (`LagerView`) ist fuer groessere Lagerbestaende gedacht.

Funktionen:

- Seitenweise Anzeige des Lagerinhalts
- Sortierung nach Name A-Z
- Sortierung nach Name Z-A
- Sortierung nach Menge
- Sortierung nach Kategorie
- Suchfunktion ueber Amboss-Eingabe
- Kategorien-Filter
- Direkte Entnahme per Klick

Bedienung:

- Linksklick auf ein Item: 1 Exemplar entnehmen
- Rechtsklick: 1 Stack entnehmen
- `Q` bzw. Drop-Klick: Inventar mit diesem Item auffuellen
- Sortier-Button: naechste Sortierung
- Rechtsklick auf Sortierung: auf Standard zuruecksetzen
- Such-Button: Suchbegriff eingeben
- Rechtsklick auf Suche: Suche loeschen
- Kategorien-Button: naechste Kategorie
- Rechtsklick auf Kategorien: alle anzeigen

Die Suche prueft Materialnamen und vorhandene Anzeigenamen. Dadurch lassen sich Inhalte auch in groesseren Lagern schnell finden.

### 5. Manuelles Einlagern

Items lassen sich auf mehreren Wegen ins Lager verschieben:

- Im Hauptmenue ueber den Hopper-Button
- In der Lageransicht ueber den Hopper-Button
- Mit Item am Cursor: nur dieses Item einlagern
- Ohne Cursor-Item: moeglichst viele passende Items aus dem Hauptinventar einlagern

Zusatzfunktion:

- Shift-Rechtsklick auf ein Item im Spielerinventar lagert es direkt in einen verfuegbaren Lager-Shulker ein
- Bevorzugt wird ein eigener Lager-Shulker, sonst ein freigegebener

Hinweis:

- Das direkte Inventar-Einlagern ohne Cursor ignoriert Items mit ItemMeta
- So werden Spezialitems, benannte Items oder andere besondere Gegenstaende nicht versehentlich verschoben

### 6. Automatische Einlagerung beim Abbauen

Der Button `Autom. Einlagerung` aktiviert Auto-Store fuer einen Lager-Shulker.

Wenn aktiv:

- Blockdrops werden beim Abbauen direkt ins Lager geschoben
- Auch Inhalte normaler Container koennen beim Abbau mit eingelagert werden
- Falls nicht alles hineinpasst, bleibt der Rest als normaler Drop liegen
- Eigene Lager-Shulker werden bevorzugt, freigegebene Lager koennen ebenfalls genutzt werden

Das ist besonders hilfreich fuer Minen, Farmen und grosse Bauprojekte.

### 7. Shulker automatisch nachfuellen und leeren

Die Refill-Funktion verbindet einen platzierten Lager-Shulker mit deinem globalen Lager.

Wenn aktiviert:

- Inhalte aus dem platzierten Shulker werden automatisch ins globale Lager verschoben
- Ein ausgewaehltes Material kann automatisch wieder in den Shulker nachgelegt werden
- Die letzten 5 Slots des platzierten Shulkers bleiben fuer andere Inhalte reserviert

Konfigurierbar:

- Nachfuell-Item
- Mindestbestand, der im globalen Lager verbleiben muss
- Entnahmemenge pro Nachfuell-Vorgang

Typischer Anwendungsfall:

- Du legst z. B. Cobblestone, Rockets oder Logs als Nachfuell-Item fest
- Der Shulker zieht dieses Material automatisch aus dem globalen Lager nach
- Gleichzeitig werden andere Items aus dem Shulker wieder eingelagert

### 8. Einsaug-Funktion

Die Einsaug-Funktion sammelt gedroppte Items automatisch ein und ueberfuehrt sie ins Lager.

Es gibt zwei Einsatzarten:

- Getragener Lager-Shulker: saugt in kleinem Radius um den Spieler ein
- Platzierter Lager-Shulker: saugt in einem konfigurierbaren Bereich ein

Wichtige Eigenschaften:

- Das Einsaugen verbraucht Ladung
- Pro eingelagertem Item wird 1 Punkt Ladung genutzt
- Brennstoff ist frei waehlbar
- 1 verbrauchter Brennstoff ergibt `64` Ladung

Konfigurierbar:

- Einsaugen an/aus
- Brennstoff fuer die globale Ladung
- Filtermodus: alle Items oder nur definierte Filter-Slots
- Bereichsmodus:
  - `1x1 Chunk`
  - `3x3 Chunks`
  - `Einzelbereich (X/Y/Z)`
- Partikelanzeige fuer den aktiven Bereich
- Relative X/Y/Z-Grenzen im Box-Modus

Filter-Bedienung:

- Die ersten 9 Slots im Einsaug-Menue sind Filter-Slots
- Mit Cursor-Item belegen
- Shift-Rechtsklick entfernt einen Filter

Bereichs-Bedienung:

- Linksklick: Wert erhoehen
- Rechtsklick: Wert verringern
- Shift-Links: groessere Schritte nach oben
- Shift-Rechts: groessere Schritte nach unten

Grenzen:

- X gesamt: maximal 48 Bloecke
- Z gesamt: maximal 48 Bloecke
- Y gesamt: maximal 320 Bloecke

### 9. EXP-Speicher

Das Plugin kann Spieler-Erfahrung direkt im Lager speichern.

Im Hauptmenue:

- Linksklick auf EXP-Speicher: gesamte aktuelle Spieler-XP einlagern
- Rechtsklick: `100 XP` als Orbs ausgeben
- Shift-Rechtsklick: den kompletten XP-Vorrat ausgeben

Damit lassen sich XP sichern oder spaeter gezielt wieder abrufen.

### 10. Freigaben fuer andere Spieler

Lager koennen mit anderen Spielern geteilt werden.

Funktionen:

- Spieler ueber Amboss-Eingabe hinzufuegen
- Spieler aus der Liste entfernen
- Freigaben gelten auf Lager-Ebene des Besitzers
- Freigegebene Spieler duerfen den Lager-Shulker oeffnen und Funktionen nutzen

Wichtig:

- Der Spieler muss mindestens einmal auf dem Server gewesen sein
- Eigene Lager werden bei der Auswahl fuer Schnell-Einlagerung bevorzugt

### 11. Farbe des Lager-Shulkers

Die Farbe des Lager-Shulkers kann ueber das Farbmenue angepasst werden.

- Die gewaehlte Farbe wird gespeichert
- Bereits platzierte zugehoerige Lager-Shulker werden entsprechend aktualisiert
- Die Farbe beeinflusst auch die Darstellung der Einsaug-Partikel

### 12. Upgrades

Das Lager kann mit Ingame-Ressourcen erweitert werden.

#### Slot-Upgrade

- Kosten: `1x Endertruhe`
- Effekt: `+27` globale Slots

#### Kapazitaets-Upgrade

- Kosten: `1x Truhe`
- Effekt: `+1728` maximale Item-Kapazitaet

Upgrades gelten global fuer das Lager des Besitzers, nicht nur fuer einen einzelnen Shulker.

### 13. Pick-Block-Unterstuetzung

Wenn ein Spieler einen Lager-Shulker in der Hand haelt und die Pick-Block-Funktion nutzt, versucht das Plugin das anvisierte Material direkt aus dem Lager zu holen.

Das ist vor allem im Kreativ-/Bau-Workflow angenehm:

- Material anvisieren
- Pick-Block ausloesen
- Das Plugin gibt bis zu einen Stack dieses Materials aus dem Lager

## Wichtige Interaktionen im Spiel

### Lager-Shulker platzieren

- Nur im Schleichen moeglich
- Ohne Schleichen wird das Platzieren abgebrochen

### Lager-Shulker oeffnen

- Nur im Schleichen per Rechtsklick auf den platzierten Lager-Shulker

### Lager-Shulker abbauen

- Der Shulker droppt wieder als Lager-Shulker mit seiner ID
- Der aktuelle Inhalt des platzierten Shulkers wird zuerst ins globale Lager uebertragen
- Was nicht hineinpasst, faellt normal heraus

## Bedienempfehlung fuer Spieler

Ein sinnvoller Start sieht so aus:

1. Einen Lager-Shulker craften und platzieren.
2. Hauptmaterialien als Schnellslots belegen.
3. Kategorien passend zum eigenen Spielstil umbenennen.
4. Auto-Store fuer Farmen oder Mining aktivieren.
5. Refill fuer haeufig benoetigte Bloecke einrichten.
6. Einsaugen mit Brennstoff und Filter konfigurieren.
7. Kapazitaet und Slots nach Bedarf upgraden.

## Konfiguration

Aktuell enthaelt die `config.yml` hauptsaechlich die Storage-Backend-Konfiguration fuer MySQL. Wenn MySQL deaktiviert ist, arbeitet das Plugin standardmaessig mit Nitrite. Die Spiellogik selbst wird ueber die Menues im Spiel eingestellt, nicht ueber viele Textoptionen.

## Berechtigung

```text
storage.use
```

- Standard: `true`
- Erlaubt die Nutzung des Lager-Systems

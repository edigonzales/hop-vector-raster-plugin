# Prüfung des Icon-Entwurfs

Stand: 8. September 2026. Diese Prüfung betrifft die zehn Design-SVGs und die lokale Galerie. Es wurden keine Plugin-Ressourcen integriert.

## Reproduzieren

Voraussetzungen: Python 3, JDK 17 und die lokal vorhandenen Maven-Artefakte für Hop Core 2.17.0 / Batik 1.17. Die Prüfung lädt keine Bibliotheken aus dem Netz nach und verändert keine Maven-Konfiguration.

Vom Repository-Stamm aus:

```sh
python3 docs/icon-design/validation/verify.py --java "$JAVA_HOME/bin/java"
```

Ohne `--java` verwendet das Skript `JAVA_HOME` oder `java` aus dem Suchpfad. Mit `--maven-repo` lässt sich ein abweichender Maven-Cache angeben; mit `--classpath` ein expliziter Hop-2.17-Classpath. Die benötigten Koordinaten sind in `verify.py` festgelegt. Fehlende JARs werden mit ihren Pfaden gemeldet.

Die Ergebnisse landen unter `target/icon-design-qa/`:

- 40 transparente PNGs: zehn Icons × 16/24/32/64 Pixel.
- `contact-sheet.png`: dieselben Renderings auf weiss, dunkel (`#182832`) und in Graustufen.

`--output` erlaubt einen anderen Zielordner. Die PNGs sind abgeleitete Prüfartefakte; die SVGs bleiben die editierbaren Quellen.

## Automatische Prüfungen

| Prüfung | Erwartung |
| --- | --- |
| Vollständigkeit | Genau die zehn vereinbarten SVG-Muster |
| XML | Gültige SVG-Namensräume, `32 × 32`, `viewBox="0 0 32 32"`, Titel und Beschreibung |
| Eigenständigkeit | Nur einfache Vektorelemente, Palette eingehalten; keine Schrift-/Bitmap-/Script-/CSS-/externen Ressourcenabhängigkeiten |
| Backend-Paar | GDAL und GeoTools Raster Clip unterscheiden sich visuell nur durch die Herkunftsmarke |
| Galerie | Alle referenzierten SVG-, CSS-, JavaScript- und Dokumentdateien vorhanden |
| Hop-Renderer | `SvgSupport.loadSvgImage` und `SwingUniversalImageSvg` aus dem echten `hop-core-2.17.0.jar`, mit dessen Batik-1.17-Abhängigkeiten |
| Rasterbilder | Erwartete Pixelmasse, sichtbarer Inhalt, transparente Aussenkante, kein deckender Hintergrund |

Der Java-Prüflauf nutzt Hops Swing/Batik-Renderer headless. Er öffnet keine Hop-Sitzung und verändert keine Benutzerkonfiguration. Es handelt sich nicht um einen Test der nativen SWT-Menüintegration.

## Visuelle und Browser-Prüfung

- Kontaktbogen bei 16, 24, 32 und 64 Pixeln auf hell/dunkel sowie in Graustufen beurteilen.
- Reader/Writer: Pfeilrichtung muss klar verschieden sein.
- GDAL/GeoTools: identisches Clip-Motiv; Kreis/Quadrat sind bei grösseren Ansichten klar erkennbar. Bei 16 Pixeln sind sie nachrangige Hinweise; der Transform-Name bleibt relevant.
- Inspector/Calculator: Lupe und Rechner müssen auch im kleinen Format unterscheidbar sein. Das Menübeispiel zeigt den Inspector zusätzlich bei 16 Pixeln.
- Overlay/Predicate/Coverage: Ergebnisfläche, Prüfzeichen und unregelmässige gemeinsame Flächengrenzen vergleichen. Predicate/Coverage sind beschriftete Inline-Studien; sie gehören nicht zu den zehn mit Hop gerenderten Assets.
- In der Galerie Grösse, Hintergrund und Graustufen umschalten. Die Grössentabelle muss ihre vier festen Grössen behalten.
- Suche mit `Raster` ergibt zwei Muster, ein unbekannter Begriff zeigt den Leerzustand. Leeren der Suche stellt zehn Muster wieder her.
- Alle Bilder müssen vollständig geladen sein. Layout bei schmaler Ansicht (390 Pixel) und normaler Desktopbreite prüfen; die breite Grössentabelle darf in ihrem eigenen Bereich horizontal scrollen.
- SVG-Downloadlinks müssen auf die eigenständigen Originaldateien zeigen. Die HTML-Datei benötigt keinen Server; ein optionaler lokaler Server kann mit `python3 -m http.server --bind 127.0.0.1 --directory docs/icon-design` gestartet werden.

## Ergebnis und Grenzen

Die zehn SVGs wurden mit Hop 2.17 / Batik 1.17 in allen vier Grössen erfolgreich gerendert. XML-, Ressourcen- und Backend-Paar-Prüfung sind Teil desselben reproduzierbaren Durchlaufs. Kontaktbogen und Browseransicht wurden visuell kontrolliert; die Ansichtsregler, Suchtreffer, Leerzustand und Rückkehr zur vollständigen Auswahl wurden im Browser geprüft.

Nicht Bestandteil dieser Lieferung sind Paketierungsänderungen, eine gestartete native Hop-GUI, automatische Theme-Kontrastumschreibung oder Pipeline-Statusüberlagerungen. Diese Punkte werden bei der späteren Übernahme der SVGs in die Plugin-Ressourcen geprüft. Ein vollständiger Maven-Build der Geoverarbeitungsfunktionen ist für die ausschliesslich unter `docs/icon-design/` angelegten Designartefakte nicht erforderlich und wurde nicht ausgeführt.

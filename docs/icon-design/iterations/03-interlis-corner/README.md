# Schritt 03 · INTERLIS-Ecke

9. September 2026 · Zwei Entwürfe nach der bestätigten Handskizze.

[Gemeinsame Vorschau](index.html) · [ili2db SVG](ili2db.svg) · [ilivalidator SVG](ilivalidator.svg)

## Gemeinsame Markierung

- Transparenter Canvas: `32 × 32`, `viewBox="0 0 32 32"`, ohne Aussenrahmen.
- Mittelpunkt der beiden Viertelkreise: `(2, 2)`. Der Quadrant öffnet sich nach rechts unten.
- Aussenradius 12 in INTERLIS-Blau `#004489`; Innenradius 6 in INTERLIS-Rot `#DA2520`.
- Die beiden Pfade in `g#interlis-corner` sind in beiden Dateien identisch. Die Flächen haben keine Umrandung.
- Die Farbwerte wurden bereits im [vorherigen Schritt](../02-interlis/README.md#farben-aus-dem-screenshot) aus dem bereitgestellten Screenshot unter Berücksichtigung seines ICC-Profils nach sRGB übertragen.

Die neue Viertelkreis-Komposition ersetzt den vorherigen Dreiecksversuch. Position und Grösse der Markierung bleiben auch bei künftigen INTERLIS-Motiven konstant.

## Hauptmotive

Die Hauptmotive stehen räumlich getrennt rechts unterhalb der Eckmarkierung. Konturen sind dunkelgrau `#3D4B58`, Flächen hellgrau `#E9EDF1`; die Datenbankoberseite ist `#F8FAFB`.

| Entwurf | Ausarbeitung |
| --- | --- |
| ili2db | Zylinder zwischen x=11 und x=27; elliptische Oberseite, zwei horizontale Unterteilungen. Hauptkontur 2 Einheiten. |
| ilivalidator | Quadratisches Prüffeld zwischen x=11 und x=24, y=15 und y=28. Häkchen von `(14,20)` über `(19,25)` nach `(28,10)`, Strichstärke 2,5 mit runden Enden. |

Das Häkchen bezeichnet die Tätigkeit des ilivalidator-Plugins und keinen aktuellen Erfolgsstatus. Die weissen Karten und ihre Rahmen auf der Vorschauseite gehören nicht zu den SVGs.

## Lieferung und Prüfung

- Zwei eigenständige SVGs als editierbare Quellen.
- Zwei transparente PNG-Vorschauen mit 256 × 256 Pixeln, direkt mit Hops SVG-Renderer erzeugt.
- Vorschauseite ohne externe Ressourcen, mit grossen Motiven und festem Grössenvergleich bei 16, 24, 32 und 64 Pixeln.
- Renderprüfung über `SvgSupport.loadSvgImage` und `SwingUniversalImageSvg` aus Hop 2.17.0 mit Batik 1.17. Dazu wird der vorhandene Prüfer `../../validation/RenderIcons.java` mit diesem Ordner als SVG-Eingabe verwendet.
- Prüfartefakte unter `target/icon-design-interlis-corner/`: acht kleine PNGs und ein Kontaktbogen auf hellem/dunklem Hintergrund sowie in Graustufen. Die Prüfung kontrolliert Dimensionen, sichtbaren Inhalt und den transparenten Rand von zwei SVG-Einheiten.

Bei 16 Pixeln bleiben Viertelkreis-Markierung, Datenbank und Häkchen erkennbar; die Datenbank-Unterteilungen sind dort stark verdichtet. Die unveränderten Markenfarben wirken auf dunklem Hintergrund zurückhaltender. Eine native Hop-GUI-Integration und Theme-Anpassung erfolgen erst nach der Designauswahl.

Die bisherigen Plugin-Ressourcen, IDs und APIs wurden nicht geändert. Die frühere Zehner-Galerie bleibt als historischer Entwurf erhalten.

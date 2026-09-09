# Schritt 02 · INTERLIS-Kennzeichnung

9. September 2026 · Ein einzelnes ili2db-Muster zur gemeinsamen Weiterentwicklung.

[Vorschau](index.html) · [Eigenständiges SVG](ili2db.svg)

## Neue Richtung

Alle INTERLIS-bezogenen Icons sollen oben links dieselbe kleine zweifarbige Dreiecksmarke tragen. Das Hauptsymbol beschreibt die konkrete Aufgabe. Für ili2db ist das ein neutraler Datenbankzylinder im Zentrum.

Das Dreieck ersetzt für die INTERLIS-Familie die zuvor vorgeschlagene violette Familienkennzeichnung. In diesem Schritt ist ausschliesslich das ili2db-Muster ausgearbeitet. Die alte Galerie bleibt als früherer Entwurf bestehen; sie bildet diese neue Richtung noch nicht ab.

## Aufbau dieses Musters

- Zeichenfläche: `32 × 32`, transparenter Hintergrund.
- Dreieck: oben links, von `(2, 2)` bis `(12, 10)`, Spitze nach oben. Die Spitze-nach-oben-Ausrichtung ist die Annahme für dieses Muster.
- Waagrechte Teilung bei `y = 6`: obere Hälfte der Höhe rot, untere Hälfte blau. Wegen der Dreiecksform sind die Farbflächen unterschiedlich gross.
- Datenbank: neutraler grauer Zylinder, Kontur `#3D4B58`, Flächen `#E9EDF1` und `#F8FAFB`. Das Zentrum des Zylinders liegt leicht rechts/unten, damit die Marke freisteht.
- Keine weiteren Pfeile, Modellkästchen, Suite-Marken oder Unterscheidungen zwischen Action und Transform in diesem ersten Versuch.

## Farben aus dem Screenshot

Quelle: vom Benutzer bereitgestellter Screenshot vom 9. September 2026, 06:41:17, Originalgrösse `3424 × 2248` Pixel.

| Farbe | SVG-Farbwert in sRGB | RGB |
| --- | --- | --- |
| INTERLIS-Rot | `#DA2520` | `218, 37, 32` |
| INTERLIS-Blau | `#004489` | `0, 68, 137` |

Die Original-PNG enthält das Monitorprofil `DELL S3221QS`. Deshalb wurden die Pixel zuerst mit Pillow/ImageCms vom eingebetteten ICC-Profil nach sRGB konvertiert. Die Werte wurden sowohl an grossen einfarbigen Dekorflächen als auch im INTERLIS-Logo bestätigt. Die rohen Gerätewerte (`199,53,35` und `19,57,123`) sind keine sRGB-Farbwerte und wurden nicht direkt ins SVG übernommen. Der Screenshot selbst wurde nicht geändert oder ins Repository kopiert.

## Prüfung

Das SVG wird mit dem vorhandenen Hop-2.17-/Batik-Renderer bei 16, 24, 32 und 64 Pixeln auf gültige Darstellung und transparente Ränder geprüft. Der Kontaktbogen im Build-Verzeichnis zeigt zusätzlich helle und dunkle Hintergründe sowie Graustufen. Die farbige Marke bleibt auf dunklem Grund deutlich zurückhaltender; die aktuelle Vorschau konzentriert sich auf die Grundkomposition auf hellem Hintergrund.

Die frühere `validation/verify.py` prüft weiterhin nur die zehn ursprünglichen Muster und deren damalige Palette. Dieses einzelne Iterationsmuster kann mit `validation/RenderIcons.java` separat gerendert werden, indem dieser Ordner als Eingabeverzeichnis verwendet wird.

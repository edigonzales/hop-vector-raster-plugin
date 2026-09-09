# Hop Geo · Icon-System

**Aktueller Schritt:** [INTERLIS-Ecke: ili2db und ilivalidator](iterations/03-interlis-corner/index.html), 9. September 2026. Die neue gemeinsame Kennzeichnung ist ein roter Viertelkreis mit blauem Viertelkreisring oben links, nach der bestätigten Handskizze. Sie ersetzt die violette Familienkennzeichnung und den zwischenzeitlichen Dreiecksversuch. Die folgende Dokumentation und die Zehner-Galerie zeigen noch den ersten Entwurf.

Entwurf 01 · 8. September 2026

**Datenart = Grundmotiv und Farbe. Aufgabe = Zusatzsymbol. Plugin-Suite = kleine Herkunftsmarke.**

[Interaktive Galerie öffnen](index.html) · [Zehn SVG-Muster](icons/) · [Renderprüfung](validation/README.md)

Die Galerie funktioniert direkt als lokale HTML-Datei, ohne Installation, externe Schriften, CDN oder Netzwerkzugriff. Die Regler verändern die dargestellte Grösse, den Hintergrund und die Graustufenansicht. Alle Muster lassen sich einzeln als SVG herunterladen. Die Grössentabelle zeigt unabhängig vom Grössenregler immer 16, 24, 32 und 64 Pixel.

## Umfang und Ausgangslage

Die Designsprache gilt für acht Repositories:

| Repository | Aufgaben und bisherige Gestaltung |
| --- | --- |
| `hop-geotools-plugin` | Fünf Transforms. Reader und Writer teilen ihr SVG mit OGR Input/Output. Raster Clip, Zonal Statistics und ArcInfo Generate Writer verwenden ebenfalls ein identisches Motiv. |
| `hop-gdal-plugin` | Vektor-I/O und Rasterverarbeitung. Clip, Resize, Reproject und das im Quellbestand enthaltene Warp verwenden dasselbe SVG. |
| `hop-interlis-plugin` | Typisierte Klassen, Transferdaten, Strukturen, Rollen, Validierung und Enumerationen. Bereits gemeinsame blaue Palette und 42er-Zeichenfläche. |
| `hop-ili2db-plugin` | Pipeline-Transform und Workflow-Action. Bisher dieselbe Form in Blau bzw. Grün. |
| `hop-geometry-type-plugin` | Gemeinsamer Geometry-Datentyp. Kein eigenes Transform-Icon erforderlich. |
| `hop-geometry-inspector-plugin` | GUI-Kontextaktion `Inspect geometries...`, bislang mit Hops `ui/images/preview.svg`; separate kleine Toolbar-Icons. |
| `hop-geometry-calculator-plugin` | Ein Transform für skalare Messwerte, Koordinaten, Eigenschaften und Prüfresultate. |
| `hop-geoprocessing-plugin` | Fünf Transforms bündeln 56 Operationen; bestehende SVGs auf 64er- und 128er-Zeichenflächen. |

Die ersten fünf Repositories wurden lokal untersucht. Die drei ergänzten Repositories wurden auf GitHub gelesen: [Inspector](https://github.com/edigonzales/hop-geometry-inspector-plugin/tree/60860671a25a29c9d783d2b13efe5dcaf92e66d1), [Calculator](https://github.com/edigonzales/hop-geometry-calculator-plugin/tree/36c670d353755d1d89b4ac1891acdcf47c13498e), [Geoprocessing](https://github.com/edigonzales/hop-geoprocessing-plugin/tree/1dc072adac61d3d6ffd442c27846fe4639e77e07). Die Commit-Links fixieren den analysierten Stand.

Diese Lieferung enthält zehn eigenständige Designmuster und Regeln für die übrige Familie. Bestehende Laufzeitressourcen, Plugin-IDs, APIs und Pipeline-Dateien wurden nicht geändert. Auch die Toolbar des Inspectors bleibt unverändert.

## Farbe und Form

| Familie | Akzent | Helle Fläche | Grundmotiv |
| --- | --- | --- | --- |
| Vektor / Geometrie | Petrol `#168C87` | `#D9EEEB` | Unregelmässiges Polygon, bei Einzelgeometrien mit drei sichtbaren Stützpunkten |
| Raster | Ocker `#C58A20` | `#F7EBCF` | 3×3-Raster mit wenigen akzentuierten Zellen |
| INTERLIS / Modell | Violett `#8061B2` | `#ECE5F5` | Verbundene Klassen- oder Strukturkästchen |
| Datenbank / ili2db | Blau `#397DB8` | `#DFEBF7` | Datenbankzylinder mit kleinem Modellmotiv |
| Gemeinsame Kontur | Dunkelblau `#0E3A5A` | — | Silhouette, Operationszeichen und Herkunftsmarken |
| Trennkontur | Hell `#F3F7FA` | — | Schmale, gleichmässige Kontur hinter Hauptformen und Operationszeichen |

Die Datenfamilie bleibt auch bei einer anderen Ausgabeart erhalten: Ein Geometry Calculator bleibt petrolfarben, obwohl er Zahlen, Texte oder Wahrheitswerte liefert. Eine Raster-Zonenstatistik bleibt ockerfarben. Beim Wechsel der Datenart, etwa Rasterize Vector, dominiert das Zielmotiv; das Ausgangsmotiv wird klein ergänzt.

Die Akzentfarben codieren keine Erfolgs-, Warnungs- oder Fehlerzustände. Ein Häkchen bedeutet eine Prüftätigkeit und sagt nichts über das Ergebnis eines konkreten Laufs aus. Die Form muss die Aufgabe auch in Graustufen tragen.

## Konstruktion

- `width="32"`, `height="32"`, `viewBox="0 0 32 32"` für jedes Asset.
- Die gemalte Silhouette bleibt innerhalb von zwei Einheiten Abstand zur Zeichenflächenkante.
- Hauptkonturen: 2 Einheiten. Rasterlinien: 1,5 Einheiten. Kleine Flächen ersetzen unnötige Innenlinien.
- Abgerundete Linienenden und Linienverbindungen; kleine Kästchen erhalten Radien von 1–2 Einheiten.
- Trennkontur: derselbe Pfad mit 4 Einheiten Strichstärke in `#F3F7FA` hinter der 2 Einheiten starken Hauptkontur. Die sichtbare helle Trennung beträgt damit eine Einheit pro Seite. Sie folgt der Geometrie ohne Versatz und ist kein Schatten.
- Das grössere Hauptmotiv liegt oben/links; das Aufgabenzeichen bevorzugt rechts/unten. Die Herkunftsmarke hat links unten ihren festen Platz.
- Keine Hintergrundkachel, Verläufe, Filter, Schatten, Schriftkürzel, eingebetteten Bitmaps oder externen Ressourcen. Eine partielle Füllung beim Raster ist zulässig; der Canvas bleibt transparent.
- Alle SVGs sind eigenständige Plain-SVG-Dateien mit expliziten Farbwerten und lesbaren `title`-/`desc`-Elementen. Die SVG-Dateien sind die editierbare Quelle; es gibt keinen erforderlichen Generator oder Icon-Font.
- Bei 16 Pixeln hat das Hauptmotiv Vorrang. Die kleinen Suite-Marken ergänzen den sichtbaren Transform-Namen und sind kein Ersatz dafür.

## Herkunft und Richtung

**GeoTools: Kreis. GDAL: Quadrat.** Beide Marken sind dunkelblau, hell abgesetzt und links unten positioniert. Gleiche Aufgaben verwenden dasselbe Hauptmotiv und dieselbe Datenfarbe. Die beiden Raster-Clip-Muster unterscheiden sich ausschliesslich durch Titel/Beschreibung und Herkunftsmarke.

Diese Markierungen gelten für die genannten Plugin-Suites. Der Inspector erhält beispielsweise keine GeoTools-Marke, obwohl er GeoTools intern verwendet. Für weitere Bibliotheken werden keine zusätzlichen Marken eingeführt.

Lesen und Schreiben werden aus Sicht des Datenstroms dargestellt:

- Reader/Input: Pfeil aus der Datenquelle heraus, hier nach rechts.
- Writer/Output: Pfeil in das Datenziel hinein, hier nach links.

Bei ili2db bleibt das Icon für alle konfigurierten Befehle stabil. Die kurze Linie unter dem Zylinder steht für den Pipeline-Datenstrom. Die spätere Workflow-Action erhält an dieser Stelle ein kleines Zahnrad; die Datenbank- und Modellmotive bleiben gleich.

## Zehn SVG-Muster

| SVG | Plugin-Einstieg | Bedeutung |
| --- | --- | --- |
| [geotools-vector-reader.svg](icons/geotools-vector-reader.svg) | `GEOTOOLS_VECTOR_READER` | Polygon mit ausgehendem Pfeil und Kreismarke |
| [geotools-vector-writer.svg](icons/geotools-vector-writer.svg) | `GEOTOOLS_VECTOR_WRITER` | Polygon mit eingehendem Pfeil und Kreismarke |
| [gdal-raster-clip.svg](icons/gdal-raster-clip.svg) | `GDAL_RASTER_CLIP_TRANSFORM` | Raster mit Zuschnittwinkeln und Quadratmarke |
| [geotools-raster-clip.svg](icons/geotools-raster-clip.svg) | `GEOTOOLS_RASTER_CLIP` | Gleiches Raster-Clip-Motiv mit Kreismarke |
| [interlis-input.svg](icons/interlis-input.svg) | `INTERLIS_INPUT` | Klassenstruktur mit ausgehendem Pfeil |
| [interlis-structure-explode.svg](icons/interlis-structure-explode.svg) | `INTERLIS_STRUCTURE_EXPLODE` | Ein Elternobjekt verzweigt in drei Kindobjekte |
| [ili2db-transform.svg](icons/ili2db-transform.svg) | `INTERLIS_ILI2DB_TRANSFORM` | Datenbank und Modellstruktur mit Datenstromlinie |
| [geometry-inspector.svg](icons/geometry-inspector.svg) | GUI-Kontextaktion `Inspect geometries...` | Geometrie mit Lupe |
| [geometry-calculator.svg](icons/geometry-calculator.svg) | `GEOMETRY_CALCULATOR_TRANSFORM` | Geometrie mit Rechner, Display und vier Tasten |
| [layer-overlay.svg](icons/layer-overlay.svg) | `LAYER_OVERLAY_TRANSFORM` | Überlagerte Polygone mit betonter Schnittfläche |

## Fortsetzung der Familie

Pro registriertem Transform bleibt das Icon stabil. Insbesondere Geometry Operation, Geometry Calculator und Coverage Operation erhalten kein wechselndes Icon für einzelne ausgewählte Berechnungen. Operationsspezifische Zeichen können später in einer eigenen Auswahlansicht verwendet werden, gehören aber nicht zur ersten Integration.

### Vektor- und Rasterwerkzeuge

| Funktion | Regel für die spätere Ausarbeitung |
| --- | --- |
| OGR Input / Output | Vector-Reader/-Writer-Motiv mit GDAL-Quadrat |
| ArcInfo Generate Writer | Polygon mit kleinem Blatt und eingehendem Schreibpfeil; keine GeoTools-Marke, da eigenständiger Writer |
| Raster Info | Raster mit Informationskreis; der Punkt und der senkrechte Strich werden als Geometrie gezeichnet |
| Raster Convert | Raster auf einem Blatt mit geknickter Ecke und zwei Austauschpfeilen |
| Raster Reproject | Raster mit gebogenen Meridianen und Richtungspfeil |
| Raster Resize | Raster mit diagonalem Doppelpfeil |
| Raster Mosaic / BuildVRT | Drei versetzte Rasterkacheln in einer gemeinsamen Umfassung |
| Rasterize Vector | Kleines Polygon geht in ein dominantes Raster über |
| Raster Zonal Statistics | Raster, markierte Zone und drei unterschiedlich hohe Balken; gleiche Aufgabe bei GDAL und GeoTools, unterschiedliche Suite-Marke |
| Raster Warp, falls weiterhin angeboten | Gebogenes Raster mit Bearbeitungszeichen für die kombinierte Operation; von Reproject und Resize unterscheiden |

### INTERLIS

| Funktion | Regel für die spätere Ausarbeitung |
| --- | --- |
| Output | Gleiches Klassenmotiv wie Input, Pfeil hinein ins Ziel |
| Transfer Input / Output | Umfassung mehrerer Klassenkästchen für den vollständigen Transfer; ausgehender/eingehender Pfeil |
| Object to Row / Row to Object | Strukturkästchen und horizontale Tabellenzeile, verbunden durch gerichteten Pfeil |
| Structure Collect | Gegenrichtung von Explode: drei Kindobjekte laufen in ein Elternobjekt zusammen |
| Role Join | Zwei Klassenkästchen mit deutlich sichtbarer Verbindung und kleinem angefügtem Feld |
| Validate | Transfer-/Modellmotiv mit Prüf-Häkchen |
| Enumerations | Verzweigte Liste mit drei Endpunkten und unterschiedlich langen Eintragslinien |
| Test-Helfer | Eigene Entwicklungskennzeichnung; nicht Teil der produktiven Musterauswahl |

### Geoprocessing

| Transform | Grundmotiv | Wichtige Abgrenzung |
| --- | --- | --- |
| Geometry Operation | Polygon mit Bearbeitungsstift an einem Stützpunkt | Breite Bearbeitungsfamilie; kein ausschliessliches Buffer-Symbol |
| Spatial Predicate | Zwei erhaltene Geometriekonturen mit kleinem Häkchen | Beziehung wird geprüft; keine hervorgehobene Ergebnisfläche |
| Layer Overlay | Versetzte Polygone mit hervorgehobener gemeinsamer Fläche | Ergebnisgeometrie steht im Vordergrund |
| Layer Aggregate | Drei kleine Polygone laufen in eine gemeinsame Umfassung zusammen | Gruppierung und Zusammenführung, keine Überlagerung |
| Coverage Operation | Drei unregelmässige, lückenlos benachbarte Flächen | Gemeinsame Kanten statt regelmässiger Rasterzellen |

Die Galerie stellt Predicate und Coverage als beschriftete Inline-Motivstudien neben das ausgearbeitete Overlay-Asset. Diese Skizzen sind keine zusätzlichen ausgelieferten Plugin-SVGs.

## Prüfung und spätere Integration

Die zehn Muster werden mit `SvgSupport.loadSvgImage` und `SwingUniversalImageSvg` aus Hop 2.17 gerendert. Das ist Hops eigener SVG-/Batik-Pfad. Prüfungen umfassen transparente Ränder, sichtbaren Inhalt, XML-Struktur, Ressourcenverweise sowie einen Kontaktbogen auf hellen, dunklen und grauen Ansichten. Befehle und Grenzen stehen im [Prüfprotokoll](validation/README.md).

Die HTML-Vorschau ist eine Designprüfung und bildet keine automatische Theme-Umschreibung durch eine bestimmte Hop-GUI-Version nach. Native SWT-Menüs, Theme-Kontrastanpassungen und Pipeline-Statusüberlagerungen sind bei der späteren Integration nochmals in Hop GUI zu prüfen. Die Menüzeile in der Vorschau ist eine gekennzeichnete Gestaltungssimulation.

Bei der späteren Integration die ausgewählten SVGs an die bestehenden Ressourcenpfade kopieren, sodass IDs und `image`-Referenzen erhalten bleiben. Für den Inspector eine eigene Plugin-Ressource anlegen und ausschliesslich dessen bisherige `preview.svg`-Referenz ersetzen. Paketierung und native Anzeige anschliessend mit der jeweiligen Zielversion verifizieren.

## Quellen und Urheberschaft

Alle hier gelieferten SVG-Muster und Motivstudien wurden für dieses System neu aus geometrischen Grundformen gezeichnet. Es wurden keine SVG-Pfade, Logos oder Marken anderer Bibliotheken übernommen.

- [Apache Hop: SVG Files](https://hop.apache.org/dev-manual/latest/svg-files.html): Plain SVG und Einbindungshinweise.
- [Apache Hop: Branding Guide](https://hop.apache.org/tech-manual/latest/_attachments/BrandGuideline_Hop.pdf): Herkunft der gemeinsamen dunkelblauen Konturfarbe.
- [Lucide: Crop](https://lucide.dev/icons/crop): Referenz für ein vertrautes Zuschnittzeichen, ohne Pfadübernahme.
- [Geoprocessing Reference Matrix](https://github.com/edigonzales/hop-geoprocessing-plugin/blob/1dc072adac61d3d6ffd442c27846fe4639e77e07/docs/reference-matrix.md): Abgrenzung der fünf Transform-Familien.

Die ergänzende fachliche Palette, Kompositionen und Herkunftsmarken sind Vorschläge für diese Plugin-Familie und keine offiziellen Apache-Hop-Markenrichtlinien.

# Benutzerhandbuch mit Thoth Biblios

Die deutschen AsciiDoc-Kapitel in `user/` sind die zentrale Benutzerdokumentation.
`master.adoc` bindet sie zu einer Seite ein; `biblios.yml` konfiguriert Navigation
und Suche. Die Links „Edit this page“ und „View source“ am Dokumentanfang
sowie PDF und DOCX sind deaktiviert.

## Lokal bauen und ansehen

Benötigt: **Java 21**, Python **3.9+**, Git und beim ersten Build Internetzugang.
Der Plugin-/Maven-Build verwendet Java 21 und startet Biblios nie.

```sh
python3 docs/biblios/build.py
python3 docs/biblios/build.py --serve --port 8080
```

Der Helfer übernimmt auch uncommittete und neue Handbuchdateien sowie die HPLs aus
`examples/`. Er erstellt einen temporären Git-Snapshot auf `main`, ohne den Branch,
Index oder Dateien des Arbeitsrepositories zu verändern. Die Vorschau liegt unter
http://127.0.0.1:8080/; nach Änderungen erneut bauen und die Seite neu laden.
HTML: `docs/biblios/build/docs-site/`.

Standardmässig wird der aktuelle `all.jar`-Snapshot von
`https://jars.interlis.guru/snapshots/guru/interlis/thoth-biblios/0.0.1-SNAPSHOT/`
aufgelöst und heruntergeladen. Zeitstempelversion und SHA-256 stehen im Log und in
`build-info.json`. Für reproduzierbare Wiederholungen kann `--jar /path/to/all.jar`
oder `BIBLIOS_JAR` einen bereits gespeicherten Snapshot auswählen.

```sh
python3 docs/biblios/build.py --revision <commit-sha>
python3 -m unittest discover -s docs/biblios/tools -p 'test_*.py'
```

Mit `--revision` stammen Handbuchquellen, Beispiele, Biblios-Konfiguration und CSS aus diesem
Commit, auch bei PR-Merge-Commits oder detached HEAD. Buildhelfer und abschliessender Site-Checker
stammen aus dem aufrufenden Checkout. Der Biblios-Snapshot wird separat aufgelöst oder mit `--jar` fixiert.
Die Suche wird mit Enter gestartet. `site.css` ergänzt das Standardtheme für schmale Tabellenansichten.
ASCII-Dialoge verwenden `[.gui-mockup]` vor einem geschlossenen `----`-Listing
gemäss dem [zentralen Repository-Vertrag](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/plugin-repository-contract.md#gui-documentation).
Die GUI-Regeln (`font-size: 0.75em`, `line-height: 1.25`) werden nach dem Theme
geladen; gewöhnliche Codeblöcke behalten ihre Schriftgrösse.
Der Build prüft lokale Links, Includes/Referenzen, Anker, HPL-Downloads und Suchindex.
Downloads, Cache, generierte Konfiguration und HTML sind ignoriert.

## Automatische Veröffentlichung

`.github/workflows/biblios-docs.yml` läuft automatisch **nur bei Änderungen unter
`docs/biblios/**`**: PRs bauen und prüfen, Pushes auf `main` veröffentlichen danach.
Änderungen ausserhalb dieses Verzeichnisses, etwa an der Root-README, Java-Code, Beispielen,
Buildskripten unter `scripts/` oder Workflows, lösen allein keinen Biblios-Lauf aus.
Änderungen an `docs/biblios/build.py` lösen ihn dagegen aus. Bei geänderten Beispielen das zugehörige Handbuchkapitel ebenfalls
aktualisieren, damit Downloads neu veröffentlicht werden.

`workflow_dispatch` erlaubt einen ausdrücklich manuellen Build. Auch dann wird nur
von `main` veröffentlicht. PRs erhalten keine Pages-Schreibberechtigung.
GitHub muss unter Settings → Pages → Build and deployment → Source auf **GitHub
Actions** stehen; der Workflow verwendet das Environment `github-pages`.

Ziel: https://edigonzales.github.io/hop-vector-raster-plugin/

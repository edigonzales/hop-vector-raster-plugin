# Repository instructions

## CI and tests

Before changing pipelines or test setup, read the
[shared CI contract](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/ci-contract.md).
Use the CI interfaces at the shared workflow pin.

Run the commands below from this repository root in Bash, using Python 3, Maven
and JDK 21 (`JAVA_HOME` and `PATH` pointing to that JDK). Compatibility jobs also
use JDK 25. For headless Linux SWT tests, run Maven normally and execute the
native dialog smoke check with `sh scripts/run-headless-vector-dialogs.sh`. The
wrapper starts Xvfb directly and does not require `xauth`.
Set `HOP_CI_DIR` to an absolute checkout of `hop-plugin-ci` at the helper revision
used by this repo's workflow, then prepare the same Maven repositories as CI:

```bash
CI_TEST_TMP="$(mktemp -d)"
export MAVEN_SETTINGS="$CI_TEST_TMP/maven-settings.xml"
python3 "$HOP_CI_DIR/scripts/write_maven_settings.py" --output "$MAVEN_SETTINGS"
```

### Build and distribution

See [.github/workflows/verify.yml](.github/workflows/verify.yml).

```bash
mvn -s "$MAVEN_SETTINGS" -U -B -ntp -Dhop.geometry.type.version=0.2.0-SNAPSHOT clean verify
python3 scripts/check-distribution.py
python3 scripts/test-raster-migration.py
```

Compatibility uses the same Maven settings and Geometry property with `test`
instead of `clean verify`.

### Installed Hop E2E

Set `HOP_HOME` to a clean, disposable Apache Hop 2.19.0 installation. Use absolute
paths for all inputs. Set `PLUGIN_ZIP` to the built Vector/Raster ZIP,
`GEOMETRY_ZIP` to the Geometry 0.2.0-SNAPSHOT ZIP, and `RASTER_TYPE_ZIP` to
the matching Raster Type ZIP. The test requires Java, `javac` and Python. In a
virtual environment, install the pinned offline schema validator with
`python3 -m pip install -r scripts/requirements-test.txt`. Installed E2E runs on
Java 21/25 across Linux, macOS and Windows with the same three plugin ZIPs.

```bash
python3 scripts/run-installed-e2e.py --hop-home "$HOP_HOME" --plugin-zip "$PLUGIN_ZIP" --geometry-zip "$GEOMETRY_ZIP" --raster-type-zip "$RASTER_TYPE_ZIP"
```

For local tests, the plugin ZIP comes from
`assemblies/assemblies-hop-vector-raster/target`. In CI it comes from the exact
canonical artifact, downloaded into `.ci/verified`. The script installs all three
ZIPs and executes the example pipelines and runtime checks in Hop. Snapshot and
release jobs depend on the verify workflow including this installed E2E job.

Raster V1 additionally requires the matching Raster Type ZIP and Maven artifacts from
`hop-raster-type-plugin`. Build/install that repository first during coordinated local development.
The native raster model is supplied only by its separate type ZIP.

## GeoJSON and JSON-FG

Geometry Type owns the shared GeoJSON geometry/CRS codec and Jackson 3 runtime.
The Vector/Raster ZIP must not bundle those libraries. Feature JSON payloads use
String fields to preserve nested structures and number precision. Never use the
homogeneous CloudGeometry path for GeoJSON or infer fields by scanning a file.
The published Geometry ZIP is downloaded once before the matrix. Builds use a
fresh `.ci/maven-repository`. Canonical validation compares the bytes of the
published Maven Geometry JAR with the one in that same runtime ZIP. Installed
E2E uses the identical ZIP.
`.mvn/maven.config` selects Wagon for builds and the existing pinned publisher.
Keep the existing workflow/helper pins when extending these checks.

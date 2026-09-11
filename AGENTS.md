# Repository instructions

## CI and tests

Before changing pipelines or test setup, read the
[shared CI contract](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/ci-contract.md).
The documentation follows `main`; use the interfaces at this repo's actual
workflow/helper revisions and preserve existing pins and `ci-ref` values.

Run the commands below from this repository root in Bash, using Python 3, Maven
and JDK 21 (`JAVA_HOME` and `PATH` pointing to that JDK). Compatibility jobs also
use JDK 25. For headless Linux SWT tests, run Maven under `xvfb-run -a`.
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
python3 scripts/check-cloud-output.py
```

Compatibility uses the same Maven settings and Geometry property with `test`
instead of `clean verify`. Cloud-output validation needs the built classes/JARs.

### Installed Hop E2E

Prepare a clean, disposable Apache Hop 2.19.0 installation; it must not already
contain Geometry or Vector/Raster plugin directories. Set absolute paths:
`HOP_HOME` to that installation, `PLUGIN_ZIP` to the built Vector/Raster ZIP,
and `GEOMETRY_ZIP` to the Geometry 0.2.0-SNAPSHOT ZIP. The workflow downloads the
latter as `ch.so.agi:hop-geometry-type-plugin:0.2.0-SNAPSHOT`, extension `zip`,
using the shared Maven downloader. The test requires Java and `javac` plus Bash.

```bash
python3 scripts/run-installed-e2e.py --hop-home "$HOP_HOME" --plugin-zip "$PLUGIN_ZIP" --geometry-zip "$GEOMETRY_ZIP"
```

For local tests, the plugin ZIP comes from
`assemblies/assemblies-hop-vector-raster/target`. In CI it comes from the exact
canonical artifact, downloaded into `.ci/verified`. The script installs both
ZIPs and executes raster/vector smoke tests in Hop. Snapshot and release jobs
depend on the verify workflow including this installed E2E job.

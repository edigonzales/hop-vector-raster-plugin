#!/usr/bin/env python3
"""Headless SWT smoke check for the six current raster dialogs."""
import os
from pathlib import Path
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
reports = root / "raster/raster-values/target/surefire-reports"
report = next(reports.glob("TEST-*.xml"), None)
if report is None:
    raise SystemExit("Run mvn verify first")
classpath = next(
    p.attrib["value"]
    for p in ET.parse(report).findall(".//property")
    if p.attrib["name"] == "java.class.path"
)
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
version = ET.parse(root / "pom.xml").find("m:properties/m:hop.version", ns).text
work = root / "raster/raster-values/target/ui-smoke"
work.mkdir(exist_ok=True)
rcp = work / f"hop-ui-rcp-{version}.jar"
if not rcp.exists():
    urllib.request.urlretrieve(
        f"https://repo.maven.apache.org/maven2/org/apache/hop/hop-ui-rcp/{version}/hop-ui-rcp-{version}.jar",
        rcp,
    )
java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java"
args = [java]
if sys.platform == "darwin":
    args.append("-XstartOnFirstThread")
args += ["-cp", str(rcp) + os.pathsep + classpath, "ch.so.agi.hop.raster.values.RasterDialogSmoke"]
subprocess.run(args, cwd=work, check=True, timeout=45)

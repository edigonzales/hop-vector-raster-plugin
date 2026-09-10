#!/usr/bin/env python3
"""Execute handbook HPLs using installed ZIP classes, Hop's actual RowGenerator and local data."""
import os
from pathlib import Path
import subprocess
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
report = next((root / 'raster/raster-zonal-stats/target/surefire-reports').glob('TEST-*.xml'), None)
if report is None:
    raise SystemExit('Run mvn clean verify first (Java 17)')
cp = next(p.attrib['value'] for p in ET.parse(report).findall('.//property')
          if p.attrib['name'] == 'java.class.path')
host = [p for p in cp.split(os.pathsep) if p.endswith('.jar') and '/target/' not in p and '\\target\\' not in p]
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
version = ET.parse(root / 'pom.xml').find('m:properties/m:hop.version', ns).text
work = root / 'target/doc-examples'; work.mkdir(parents=True, exist_ok=True)
generator = work / f'hop-transform-rowgenerator-{version}.jar'
if not generator.exists():
    urllib.request.urlretrieve(
        f'https://repo.maven.apache.org/maven2/org/apache/hop/hop-transform-rowgenerator/{version}/{generator.name}', generator)
archive = next((root / 'assemblies/assemblies-hop-vector-raster/target').glob('*.zip'))
java_home = Path(os.environ['JAVA_HOME']) / 'bin' if 'JAVA_HOME' in os.environ else None
java = str(java_home / 'java') if java_home else 'java'
javac = str(java_home / 'javac') if java_home else 'javac'
with tempfile.TemporaryDirectory(prefix='doc-examples-') as temp:
    base = Path(temp)
    with zipfile.ZipFile(archive) as z:
        z.extractall(base)
    jars = sorted((base / 'plugins/transforms/vector-raster').rglob('*.jar'))
    shipped = {p.name for p in jars}
    classpath = os.pathsep.join([str(p) for p in jars] + [str(generator)]
                               + [p for p in host if Path(p).name not in shipped])
    subprocess.run([javac, '-cp', classpath, '-d', str(base), str(root / 'scripts/DocumentationExamplesSmoke.java')], check=True)
    subprocess.run([java, '-cp', str(base) + os.pathsep + classpath,
                    'DocumentationExamplesSmoke', str(root), str(base)], cwd=base, check=True, timeout=90)

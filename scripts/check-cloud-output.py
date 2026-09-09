#!/usr/bin/env python3
"""Smoke-test installed writer JARs, with no Hadoop or build-tree implementation classes."""
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
report = next((root / 'vector/vector-core/target/surefire-reports').glob('TEST-*.xml'))
cp = next(p.attrib['value'] for p in ET.parse(report).findall('.//property')
          if p.attrib['name'] == 'java.class.path')
# Core's provided dependency graph supplies only Hop and the shared geometry runtime.
host = [p for p in cp.split(os.pathsep) if p.endswith('.jar')]
assert not any('hadoop' in Path(p).name or 'parquet' in Path(p).name for p in host)
archive = next((root / 'assemblies/assemblies-hop-vector-raster/target').glob('*.zip'))
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'
with tempfile.TemporaryDirectory(prefix='cloud-output-') as temp:
    base = Path(temp)
    with zipfile.ZipFile(archive) as z:
        z.extractall(base)
    jars = sorted((base / 'plugins/transforms/vector-raster').rglob('*.jar'))
    # Only the smoke harness comes from test-classes; all implementations come from the ZIP.
    classpath = os.pathsep.join([str(root / 'vector/vector-transforms/target/test-classes')]
                               + [str(p) for p in jars] + host)
    subprocess.run([java, '-cp', classpath,
                    'ch.so.agi.hop.vector.transforms.CloudOutputSmoke', temp], check=True, timeout=60)

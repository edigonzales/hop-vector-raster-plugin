#!/usr/bin/env python3
"""Check the ten plain SVG assets, then render with the locally cached Hop 2.17 stack."""

import argparse
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from html.parser import HTMLParser


ROOT = Path(__file__).resolve().parent.parent
NAMESPACE = '{http://www.w3.org/2000/svg}'
EXPECTED = {
    'geotools-vector-reader', 'geotools-vector-writer', 'gdal-raster-clip',
    'geotools-raster-clip', 'interlis-input', 'interlis-structure-explode',
    'ili2db-transform', 'geometry-inspector', 'geometry-calculator', 'layer-overlay',
}
COLORS = {
    '#0E3A5A', '#F3F7FA', '#168C87', '#D9EEEB', '#C58A20', '#F7EBCF',
    '#8061B2', '#ECE5F5', '#397DB8', '#DFEBF7', 'none',
}


class LocalResources(HTMLParser):
    def handle_starttag(self, tag, attrs):
        for key, value in attrs:
            if key not in {'src', 'href'} or not value or value.startswith('#'):
                continue
            if '://' in value:
                raise ValueError(f'Preview must be self-contained: {value}')
            if not (ROOT / value.split('#')[0]).is_file():
                raise ValueError(f'Missing preview resource: {value}')


def check_assets():
    assets = sorted((ROOT / 'icons').glob('*.svg'))
    if {p.stem for p in assets} != EXPECTED:
        raise ValueError('Expected exactly the ten agreed SVG samples')
    for path in assets:
        root = ET.parse(path).getroot()
        if root.tag != NAMESPACE + 'svg' or root.attrib.get('viewBox') != '0 0 32 32':
            raise ValueError(f'Invalid namespace or viewBox: {path.name}')
        if root.attrib.get('width') != '32' or root.attrib.get('height') != '32':
            raise ValueError(f'Invalid intrinsic dimensions: {path.name}')
        for text_tag in ('title', 'desc'):
            if not root.findtext(NAMESPACE + text_tag):
                raise ValueError(f'Missing {text_tag}: {path.name}')
        for element in root.iter():
            if element.tag not in {NAMESPACE + t for t in ('svg', 'title', 'desc', 'g', 'path', 'rect', 'circle', 'ellipse', 'line', 'polyline', 'polygon')}:
                raise ValueError(f'Unsupported element in {path.name}: {element.tag}')
            for key, value in element.attrib.items():
                if key in {'fill', 'stroke'} and value not in COLORS:
                    raise ValueError(f'Color outside palette: {path.name}: {value}')
                if key in {'style', 'class'} or key.startswith('on') or 'href' in key or 'url(' in value:
                    raise ValueError(f'Non-standalone SVG attribute: {path.name}: {key}')
        print(f'XML OK {path.name} ({path.stat().st_size} bytes)', flush=True)
    # The chosen backend mark must be the only visual difference between clips.
    clips = []
    for stem in ('gdal-raster-clip', 'geotools-raster-clip'):
        elements = list(ET.parse(ROOT / 'icons' / f'{stem}.svg').getroot())
        clips.append([ET.tostring(e) for e in elements[2:-1]])
    if clips[0] != clips[1]:
        raise ValueError('Raster clip task motifs differ beyond the backend mark')
    LocalResources().feed((ROOT / 'index.html').read_text())
    print('Preview references and backend-pair consistency OK', flush=True)


def cached_classpath(cache):
    coordinates = [
        ('org/apache/hop/hop-core', '2.17.0'),
        ('org/apache/xmlgraphics/xmlgraphics-commons', '2.9'),
        ('xml-apis/xml-apis', '1.4.01'),
        ('xml-apis/xml-apis-ext', '1.3.04'),
        ('commons-io/commons-io', '2.15.1'),
        ('commons-logging/commons-logging', '1.2'),
    ]
    batik = 'anim awt-util bridge codec constants css dom ext gvt i18n parser script svg-dom svggen transcoder util xml'
    coordinates += [(f'org/apache/xmlgraphics/batik-{part}', '1.17') for part in batik.split()]
    jars = [cache / name / version / f'{Path(name).name}-{version}.jar' for name, version in coordinates]
    missing = [str(path) for path in jars if not path.is_file()]
    if missing:
        raise FileNotFoundError('Missing cached renderer dependencies (or supply --classpath):\n' + '\n'.join(missing))
    return os.pathsep.join(map(str, jars))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default=str(Path(os.environ['JAVA_HOME']) / 'bin' / 'java') if os.environ.get('JAVA_HOME') else 'java')
    parser.add_argument('--maven-repo', type=Path, default=Path.home() / '.m2' / 'repository')
    parser.add_argument('--classpath', help='Optional classpath containing Hop 2.17 core and its Batik dependencies')
    parser.add_argument('--output', type=Path, default=ROOT.parent.parent / 'target' / 'icon-design-qa')
    args = parser.parse_args()
    check_assets()
    cp = args.classpath or cached_classpath(args.maven_repo)
    subprocess.run([
        args.java, '-Djava.awt.headless=true', '--class-path', cp,
        str(ROOT / 'validation' / 'RenderIcons.java'), str(ROOT / 'icons'), str(args.output),
    ], check=True)


if __name__ == '__main__':
    main()

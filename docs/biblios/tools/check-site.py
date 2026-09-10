#!/usr/bin/env python3
"""Fail on broken local assets, fragment links, downloads and missing search entries."""
from html.parser import HTMLParser
import json
from pathlib import Path
import sys
from urllib.parse import unquote, urlsplit


class Page(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.ids, self.links = set(), []
        self.feed(text)

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if 'id' in values:
            self.ids.add(values['id'])
        for key in ('href', 'src'):
            if values.get(key):
                self.links.append(values[key])


def check(root):
    pages = {p.resolve(): Page(p.read_text()) for p in root.rglob('*.html')}
    assert (root / 'index.html').resolve() in pages, 'Missing start page'
    errors = []
    for path, page in pages.items():
        text = path.read_text()
        if 'Unresolved directive' in text or 'include::' in text or '&lt;&lt;' in text:
            errors.append(f'{path}: unresolved AsciiDoc reference/include')
        for link in page.links:
            url = urlsplit(link)
            if url.scheme or url.netloc:
                assert url.scheme != 'file', f'Local Git source leaked: {link}'
                continue
            local = unquote(url.path)
            if local.startswith('/hop-vector-raster-plugin/'):
                target = root / local[len('/hop-vector-raster-plugin/'):]
            elif local.startswith('/'):
                target = root / local.lstrip('/')
            else:
                target = path.parent / local if local else path
            if target.is_dir():
                target = target / 'index.html'
            target = target.resolve()
            if not target.exists():
                errors.append(f'{path.relative_to(root)}: missing {link}')
            elif url.fragment and target in pages and unquote(url.fragment) not in pages[target].ids:
                errors.append(f'{path.relative_to(root)}: missing anchor {link}')
    manual = (root / 'benutzerhandbuch/main/index.html').resolve()
    assert manual in pages, 'Missing single-page handbook'
    expected = ['grundlagen', 'installation', 'erste-pipeline', 'vector-reader', 'vector-writer',
                'raster-clip', 'raster-reproject', 'raster-statistik', 'formate', 'beispiele', 'fehlerbehebung', 'glossar']
    for anchor in expected:
        assert anchor in pages[manual].ids, 'Missing section: ' + anchor
    downloads = list(root.rglob('*.hpl'))
    assert len(downloads) == 7, f'Expected seven pipeline downloads, got {len(downloads)}'
    search = list(root.rglob('*search*.json'))
    assert search, 'Missing search JSON'
    combined = '\n'.join(p.read_text() for p in search)
    for name in ['Raster Clip', 'Raster Reproject', 'Raster Zonal', 'Vector Reader', 'Vector Writer']:
        assert name in combined, 'Search lacks ' + name
    for path in search:
        json.loads(path.read_text())
    assert not errors, '\n'.join(errors)
    print(f'Site checks passed: {len(pages)} HTML pages, {len(downloads)} downloads, {len(search)} search files')


if __name__ == '__main__':
    check(Path(sys.argv[1]).resolve())

#!/usr/bin/env python3
"""Check installed examples without loading the implementation under test."""
import json
import hashlib
from jsonschema import Draft202012Validator
from referencing import Registry, Resource
from decimal import Decimal
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def load(path):
    return json.loads(path.read_text(encoding="utf-8"), parse_float=Decimal)

def offline_validator():
    folder = ROOT / "scripts/tests/resources/jsonfg-1.0"
    manifest = json.loads((folder / "manifest.json").read_text())
    resources = []
    for uri, entry in manifest.items():
        data = (folder / entry["file"]).read_bytes()
        assert hashlib.sha256(data).hexdigest() == entry["sha256"], uri
        resources.append((uri, Resource.from_contents(json.loads(data))))
    registry = Registry().with_resources(resources)
    schema = registry.get_or_retrieve("https://schemas.opengis.net/json-fg/featurecollection.json").value.contents
    return Draft202012Validator(schema, registry=registry)


def check(output):
    for name in ("rfc7946", "legacy2008", "jsonfg10"):
        original = load(ROOT / "examples/geojson" / (name + ".geojson"))
        actual = load(output / (name + ".geojson"))
        assert actual["type"] == "FeatureCollection"
        assert len(actual["features"]) == len(original["features"])
        for expected, feature in zip(original["features"], actual["features"]):
            for key in ("type", "properties", "id", "geometry", "place", "time"):
                assert feature.get(key) == expected.get(key), (name, key, feature.get(key), expected.get(key))
            assert "bbox" not in feature
        if name == "rfc7946":
            assert "crs" not in actual and "coordRefSys" not in actual
            assert actual["source"] == original["source"]
        if name == "legacy2008":
            assert actual["crs"] == original["crs"]
        if name == "jsonfg10":
            offline_validator().validate(json.loads((output / (name + ".geojson")).read_text()))
            assert actual["coordRefSys"] == original["coordRefSys"]
            assert actual["features"][0]["measures"] == original["measures"]
            assert set(actual["conformsTo"]) == set(original["conformsTo"])
            assert "coordRefSys" not in actual["features"][0]
            assert "measures" not in actual["features"][0]["place"]
    print("GeoJSON installed examples: coordinates, M, fallback, time, properties and IDs preserved")

if __name__ == "__main__":
    check(Path(sys.argv[1]))

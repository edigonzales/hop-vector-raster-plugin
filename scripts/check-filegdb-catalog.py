#!/usr/bin/env python3
"""Independently check the FileGDB produced by the installed Hop example."""
import sys
from osgeo import gdal, ogr

gdal.UseExceptions()
if sys.argv[1] == "prepare":
    from pathlib import Path
    root = Path(sys.argv[2])
    source = gdal.OpenEx(str(root / "catalog-input.gdb"), gdal.OF_VECTOR)
    driver = ogr.GetDriverByName("OpenFileGDB")
    target = driver.CreateDataSource(str(root / "gdal-catalog.gdb"))
    for name in source.GetFieldDomainNames():
        assert target.AddFieldDomain(source.GetFieldDomain(name))
    for name in ("buildings", "entrances"):
        layer = source.GetLayerByName(name)
        result = target.CreateLayer(name, layer.GetSpatialRef(), layer.GetGeomType(),
                                    options=["TARGET_ARCGIS_VERSION=ARCGIS_PRO_3_2_OR_LATER"])
        for i in range(layer.GetLayerDefn().GetFieldCount()):
            field = layer.GetLayerDefn().GetFieldDefn(i)
            assert result.CreateField(field) == 0
        for feature in layer:
            row = ogr.Feature(result.GetLayerDefn())
            assert row.SetFrom(feature) == 0
            row.SetFID(-1)
            assert result.CreateFeature(row) == 0
    assert target.AddRelationship(source.GetRelationship("building_entrances"))
    target = None
    print("GDAL created independent catalog target")
    sys.exit(0)

db = gdal.OpenEx(sys.argv[1], gdal.OF_VECTOR, allowed_drivers=["OpenFileGDB"])
assert set(db.GetFieldDomainNames()) == {"status", "height"}
status = db.GetFieldDomain("status")
assert status.GetEnumeration() == {"1": "planned", "2": "existing"}
assert status.GetSplitPolicy() == ogr.OFDSP_DUPLICATE
assert status.GetMergePolicy() == ogr.OFDMP_DEFAULT_VALUE
height = db.GetFieldDomain("height")
assert height.GetMinAsDouble() == 0
assert height.GetMaxAsDouble() == 1000
for name in ("buildings", "entrances", "inspections"):
    layer = db.GetLayerByName(name)
    assert layer.GetFeatureCount() == (200 if name == "buildings" else 100)
    if name == "buildings":
        definition = layer.GetLayerDefn()
        for field in ("status", "height"):
            assert definition.GetFieldDefn(definition.GetFieldIndex(field)).GetDomainName() == field
        assert layer.GetNextFeature().GetField("status") == 2
    else:
        assert layer.GetGeomType() == ogr.wkbNone
assert set(db.GetRelationshipNames()) == {"building_entrances", "building_inspections"}
relationship = db.GetRelationship("building_entrances")
assert relationship.GetCardinality() == gdal.GRC_ONE_TO_MANY
assert relationship.GetLeftTableName() == "buildings"
assert relationship.GetRightTableName() == "entrances"
assert relationship.GetLeftTableFields() == ["id"]
assert relationship.GetRightTableFields() == ["building_id"]
print("GDAL independently verified Hop FileGDB domains, field assignments and relationship")

added = db.GetRelationship("building_inspections")
assert added.GetLeftTableName() == "buildings" and added.GetRightTableName() == "inspections"
layer = db.GetLayerByName("buildings")
layer.SetSpatialFilterRect(2600999,1200999,2601001,1201001)
assert layer.GetFeatureCount() == 100
layer.SetSpatialFilterRect(2599999,1199999,2600001,1200001)
assert layer.GetFeatureCount() == 100
print("GDAL verified mixed FileGDB append/create, preserved and new relationships, and old/new spatial queries")

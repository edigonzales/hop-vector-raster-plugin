#!/usr/bin/env python3
"""Independently check the FileGDB produced by the installed Hop example."""
import sys
from osgeo import gdal, ogr

gdal.UseExceptions()
db = gdal.OpenEx(sys.argv[1], gdal.OF_VECTOR, allowed_drivers=["OpenFileGDB"])
assert set(db.GetFieldDomainNames()) == {"status", "height"}
status = db.GetFieldDomain("status")
assert status.GetEnumeration() == {"1": "planned", "2": "existing"}
assert status.GetSplitPolicy() == ogr.OFDSP_DUPLICATE
assert status.GetMergePolicy() == ogr.OFDMP_DEFAULT_VALUE
height = db.GetFieldDomain("height")
assert height.GetMinAsDouble() == 0
assert height.GetMaxAsDouble() == 1000
for name in ("buildings", "entrances"):
    layer = db.GetLayerByName(name)
    assert layer.GetFeatureCount() == 100
    if name == "buildings":
        definition = layer.GetLayerDefn()
        for field in ("status", "height"):
            assert definition.GetFieldDefn(definition.GetFieldIndex(field)).GetDomainName() == field
        assert layer.GetNextFeature().GetField("status") == 2
    else:
        assert layer.GetGeomType() == ogr.wkbNone
assert db.GetRelationshipNames() == ["building_entrances"]
relationship = db.GetRelationship("building_entrances")
assert relationship.GetCardinality() == gdal.GRC_ONE_TO_MANY
assert relationship.GetLeftTableName() == "buildings"
assert relationship.GetRightTableName() == "entrances"
assert relationship.GetLeftTableFields() == ["id"]
assert relationship.GetRightTableFields() == ["building_id"]
print("GDAL independently verified Hop FileGDB domains, field assignments and relationship")

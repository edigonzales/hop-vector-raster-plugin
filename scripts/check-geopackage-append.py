#!/usr/bin/env python3
"""Generate independent GDAL targets and verify Hop's installed GeoPackage append output."""
from pathlib import Path
import sqlite3
import sys
from osgeo import gdal, ogr, osr

gdal.UseExceptions()
folder = Path(sys.argv[2])
if sys.argv[1] == "prepare":
    for indexed in (True, False):
        path = folder / ("gdal-indexed.gpkg" if indexed else "gdal-unindexed.gpkg")
        db = ogr.GetDriverByName("GPKG").CreateDataSource(str(path))
        srs = osr.SpatialReference()
        srs.ImportFromEPSG(2056)
        layer = db.CreateLayer("zones", srs, ogr.wkbPolygon, options=[f"SPATIAL_INDEX={'YES' if indexed else 'NO'}"])
        layer.CreateField(ogr.FieldDefn("name", ogr.OFTString))
        feature = ogr.Feature(layer.GetLayerDefn())
        feature.SetField("name", "original GDAL feature")
        feature.SetGeometry(ogr.CreateGeometryFromWkt("POLYGON ((0 0,10 0,10 10,0 10,0 0))"))
        layer.CreateFeature(feature)
        feature = layer = db = None
elif sys.argv[1] == "check":
    for filename in ("gpkg-append.gpkg", "gdal-indexed.gpkg", "gdal-unindexed.gpkg"):
        path = folder / filename
        db = gdal.OpenEx(str(path), gdal.OF_VECTOR, allowed_drivers=["GPKG"])
        layer = db.GetLayerByName("zones")
        assert layer.GetFeatureCount() == 2, filename
        assert db.GetLayerByName("zones_copy").GetFeatureCount() == 1
        if filename != "gpkg-append.gpkg":
            layer.SetSpatialFilterRect(-1, -1, 11, 11)
            assert layer.GetFeatureCount() == 1, filename
        layer.SetSpatialFilterRect(2600000, 1200000, 2600010, 1200010)
        assert layer.GetFeatureCount() == (2 if filename == "gpkg-append.gpkg" else 1), filename
        geom = layer.GetGeometryColumn()
        with sqlite3.connect(path) as c:
            assert c.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
            assert not c.execute("PRAGMA foreign_key_check").fetchall()
            quoted = '"' + f'rtree_zones_{geom}'.replace('"', '""') + '"'
            assert c.execute(f"SELECT count(*) FROM {quoted}").fetchone()[0] == 2
            if filename.startswith("gdal-"):
                bounds = c.execute("SELECT min_x,max_x,min_y,max_y FROM gpkg_contents WHERE table_name='zones'").fetchone()
                assert bounds[0] == 0 and bounds[2] == 0 and bounds[1] >= 2600004 and bounds[3] >= 1200003
    print("GDAL verified GeoPackage layer creation, append, old/new spatial queries and RTree consistency")
else:
    raise SystemExit("Expected prepare or check")

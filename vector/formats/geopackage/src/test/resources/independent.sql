CREATE TABLE gpkg_spatial_ref_sys(srs_name TEXT,srs_id INTEGER PRIMARY KEY,organization TEXT,organization_coordsys_id INTEGER,definition TEXT);
INSERT INTO gpkg_spatial_ref_sys VALUES('CH1903+ / LV95',2056,'EPSG',2056,'undefined');
-- Hand-authored fixture from OGC GeoPackage 1.4 GP header and WKB layouts.
-- No production writer or GeoTools code participates in constructing this fixture.
PRAGMA application_id=1196444487;
PRAGMA user_version=10400;
CREATE TABLE gpkg_contents(table_name TEXT PRIMARY KEY,data_type TEXT,srs_id INTEGER);
CREATE TABLE gpkg_geometry_columns(table_name TEXT,column_name TEXT,geometry_type_name TEXT,srs_id INTEGER,z INTEGER,m INTEGER);
CREATE TABLE points(fid INTEGER PRIMARY KEY,label TEXT,shape POINT);
INSERT INTO gpkg_contents VALUES('points','features',2056);
INSERT INTO gpkg_geometry_columns VALUES('points','shape','POINT',2056,0,0);
INSERT INTO points VALUES(1,'one',X'47500001080800000101000000000000000000f03f0000000000000040');
INSERT INTO points VALUES(2,'null',NULL);
INSERT INTO points VALUES(3,'empty',X'47500011080800000101000000000000000000f87f000000000000f87f');

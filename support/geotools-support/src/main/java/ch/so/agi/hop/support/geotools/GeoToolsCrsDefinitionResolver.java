package ch.so.agi.hop.support.geotools;

import ch.so.agi.hop.vector.core.CrsDefinitionResolver;

public final class GeoToolsCrsDefinitionResolver implements CrsDefinitionResolver {
  @Override
  public Definition parse(String value) throws Exception {
    if (value == null || value.isBlank() || value.matches("(?i)(EPSG:)?[0-9]+"))
      return CrsDefinitionResolver.super.parse(value);
    GeoToolsRuntimeSupport.initialize();
    var crs = org.geotools.referencing.CRS.parseWKT(value);
    Integer id = org.geotools.referencing.CRS.lookupEpsgCode(crs, true);
    return new Definition(
        id == null ? 0 : id,
        crs.getName().toString(),
        id == null ? "NONE" : "EPSG",
        id == null ? 0 : id,
        value);
  }

  public Definition resolve(int srid) throws Exception {
    if (srid <= 0)
      return new Definition(
          srid,
          "Undefined " + (srid == -1 ? "Cartesian" : "geographic"),
          "NONE",
          srid,
          "undefined");
    GeoToolsRuntimeSupport.initialize();
    var crs = org.geotools.referencing.CRS.decode("EPSG:" + srid, true);
    return new Definition(srid, crs.getName().toString(), "EPSG", srid, crs.toWKT());
  }
}

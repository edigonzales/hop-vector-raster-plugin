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

  @Override
  public boolean isGeographic(Definition definition) throws Exception {
    GeoToolsRuntimeSupport.initialize();
    var crs =
        definition.wkt() != null
                && !definition.wkt().isBlank()
                && !definition.wkt().equalsIgnoreCase("undefined")
            ? org.geotools.referencing.CRS.parseWKT(definition.wkt())
            : definition.srid() > 0
                ? org.geotools.referencing.CRS.decode("EPSG:" + definition.srid(), true)
                : null;
    return crs instanceof org.geotools.api.referencing.crs.GeographicCRS;
  }

  @Override
  public double linearUnitToMetres(Definition definition) throws Exception {
    GeoToolsRuntimeSupport.initialize();
    var crs =
        definition.wkt() != null
                && !definition.wkt().isBlank()
                && !definition.wkt().equals("undefined")
            ? org.geotools.referencing.CRS.parseWKT(definition.wkt())
            : definition.srid() > 0
                ? org.geotools.referencing.CRS.decode("EPSG:" + definition.srid(), true)
                : null;
    if (crs == null)
      throw new IllegalArgumentException(
          "Unknown CRS units: specify FileGDB resolution and tolerance");
    return crs.getCoordinateSystem()
        .getAxis(0)
        .getUnit()
        .getConverterToAny(tech.units.indriya.unit.Units.METRE)
        .convert(1);
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

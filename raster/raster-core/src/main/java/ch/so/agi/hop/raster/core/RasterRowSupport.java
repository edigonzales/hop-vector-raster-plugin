package ch.so.agi.hop.raster.core;

import ch.so.agi.hop.support.geotools.CrsSupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.locationtech.jts.geom.Geometry;

/** Shared row binding and source lifetime, without depending on GDAL transform internals. */
public final class RasterRowSupport implements AutoCloseable {
  private RasterDatasetRef current;
  private GeoTiffSource source;

  public GeoTiffSource source(String location) throws Exception {
    RasterDatasetRef next = new RasterDatasetRef(location);
    if (!next.equals(current)) {
      close();
      source = new GeoTiffSource(next);
      current = next;
    }
    return source;
  }

  public static String value(
      String setting, boolean field, Object[] row, IRowMeta meta, IVariables variables)
      throws Exception {
    String resolved = setting == null ? "" : variables.resolve(setting).trim();
    if (!field) return resolved;
    int index = meta.indexOfValue(resolved);
    if (index < 0) throw new IllegalArgumentException("Input field not found: " + resolved);
    String value = meta.getString(row, index);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Input field is empty: " + resolved);
    return value.trim();
  }

  public static Geometry geometry(
      String field,
      Object[] row,
      IRowMeta meta,
      IVariables variables,
      String explicitCrs,
      RasterSource source)
      throws Exception {
    int index = meta.indexOfValue(variables.resolve(field));
    if (index < 0) throw new IllegalArgumentException("Geometry field not found: " + field);
    if (row[index] == null) return null;
    if (!(row[index] instanceof Geometry geometry))
      throw new IllegalArgumentException("Expected a Hop Geometry value");
    PixelMask.validate(geometry);
    if (geometry.isEmpty()) return geometry.copy();
    return CrsSupport.inRasterCrs(geometry, variables.resolve(explicitCrs), source.crs());
  }

  public static List<String> statistics(String text) {
    List<String> selected = new ArrayList<>();
    for (String token : text.split("[,; ]+")) {
      String name = token.trim().toLowerCase(java.util.Locale.ROOT);
      if (name.isBlank()) continue;
      if (!List.of("mean", "min", "max", "sum", "stddev", "count").contains(name))
        throw new IllegalArgumentException("Unsupported statistic: " + name);
      if (!selected.contains(name)) selected.add(name);
    }
    if (!selected.contains("count")) selected.add("count");
    return List.copyOf(selected);
  }

  public static Double noData(String text, IVariables variables) {
    String value = variables.resolve(text == null ? "" : text).trim();
    if (value.isBlank()) return null;
    double d = Double.parseDouble(value);
    if (Double.isInfinite(d)) throw new IllegalArgumentException("NoData cannot be infinite");
    return d;
  }

  @Override
  public void close() {
    if (source != null) source.close();
    source = null;
    current = null;
  }
}

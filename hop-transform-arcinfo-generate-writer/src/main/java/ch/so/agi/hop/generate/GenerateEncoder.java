package ch.so.agi.hop.generate;

import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/** Esri Coverage (XY) and ASCII 3D (XYZ) GENERATE; never exports implicit holes or multipart. */
public final class GenerateEncoder {
  public enum Type {
    POINT,
    LINE,
    POLYGON
  }

  public enum Dimension {
    XY,
    XYZ
  }

  public record Options(
      Type type, Dimension dimension, boolean discardExtraOrdinates, int decimals, boolean comma) {
    public Options {
      if (type == null || dimension == null || decimals < -1 || decimals > 15)
        throw new IllegalArgumentException(
            "Choose geometry type, dimension and precision (-1 automatic, 0–15 fixed)");
    }
  }

  private final Options options;
  private boolean finished;

  public GenerateEncoder(Options options) {
    this.options = options;
  }

  public void writeFeature(Writer writer, long id, Geometry original) throws IOException {
    if (finished) throw new IllegalStateException("GENERATE file is finished");
    if (original == null || original.isEmpty())
      throw new IllegalArgumentException("Null or empty geometry");
    if (CurveGeometrySupport.isCurveGeometry(original))
      throw new IllegalArgumentException("Curves must be explicitly linearized upstream");
    boolean correct =
        switch (options.type()) {
          case POINT -> original instanceof Point;
          case LINE ->
              original instanceof LineString
                  && !(original instanceof org.locationtech.jts.geom.LinearRing);
          case POLYGON -> original instanceof Polygon;
        };
    if (!correct)
      throw new IllegalArgumentException(
          "Geometry must be a single " + options.type() + "; multipart is not supported");
    if (original instanceof Polygon p && p.getNumInteriorRing() != 0)
      throw new IllegalArgumentException("GENERATE polygons with holes are not supported");
    if (options.type() == Type.POLYGON && id == -99999)
      throw new IllegalArgumentException("Polygon ID -99999 is reserved for Esri islands");
    if (!original.isValid()) throw new IllegalArgumentException("Invalid input geometry");
    Coordinate[] coords = original.getCoordinates();
    Coordinate[] rounded = new Coordinate[coords.length];
    for (int i = 0; i < coords.length; i++) {
      Coordinate c = coords[i];
      if (!Double.isFinite(c.x) || !Double.isFinite(c.y))
        throw new IllegalArgumentException("Non-finite XY coordinate");
      if (options.dimension() == Dimension.XYZ && !Double.isFinite(c.getZ()))
        throw new IllegalArgumentException("XYZ requires a finite Z at every vertex");
      if (!options.discardExtraOrdinates()
          && ((options.dimension() == Dimension.XY && !Double.isNaN(c.getZ()))
              || !Double.isNaN(c.getM())))
        throw new IllegalArgumentException("Enable discarding extra ordinates to omit Z/M values");
      rounded[i] =
          new Coordinate(
              round(c.x),
              round(c.y),
              options.dimension() == Dimension.XYZ ? round(c.getZ()) : Double.NaN);
    }
    if (options.type() == Type.POLYGON) {
      if (options.dimension() == Dimension.XYZ
          && rounded[0].getZ() != rounded[rounded.length - 1].getZ())
        throw new IllegalArgumentException(
            "Polygon closing vertex must have the same Z as the first vertex");
      rounded[rounded.length - 1] = rounded[0].copy();
      Polygon p = original.getFactory().createPolygon(rounded);
      if (!p.isValid() || p.getArea() == 0)
        throw new IllegalArgumentException("Polygon becomes invalid after rounding");
      if (Orientation.isCCW(rounded)) {
        for (int i = 0, j = rounded.length - 1; i < j; i++, j--) {
          Coordinate c = rounded[i];
          rounded[i] = rounded[j];
          rounded[j] = c;
        }
      }
    } else if (options.type() == Type.LINE) {
      boolean nonzero = false;
      for (int i = 1; i < rounded.length; i++)
        nonzero |=
            !rounded[0].equals2D(rounded[i])
                || (options.dimension() == Dimension.XYZ && rounded[0].getZ() != rounded[i].getZ());
      if (!nonzero) throw new IllegalArgumentException("Line collapses after rounding");
    }
    // Validate the entire feature before writing any of it.
    String separator = options.comma() ? "," : " ";
    if (options.type() == Type.POINT) writer.write(Long.toString(id) + separator);
    else
      writer.write(
          Long.toString(id)
              + (options.type() == Type.POLYGON && options.dimension() == Dimension.XY
                  ? separator + "AUTO"
                  : "")
              + "\n");
    for (Coordinate c : rounded) {
      writer.write(number(c.x) + separator + number(c.y));
      if (options.dimension() == Dimension.XYZ) writer.write(separator + number(c.getZ()));
      writer.write('\n');
    }
    if (options.type() != Type.POINT) writer.write("END\n");
  }

  public void finish(Writer writer) throws IOException {
    if (finished) throw new IllegalStateException("GENERATE file already finished");
    writer.write("END\n");
    finished = true;
  }

  private double round(double value) {
    return options.decimals() < 0
        ? value
        : BigDecimal.valueOf(value)
            .setScale(options.decimals(), RoundingMode.HALF_UP)
            .doubleValue();
  }

  private String number(double value) {
    BigDecimal number = BigDecimal.valueOf(value == 0 ? 0 : value);
    return (options.decimals() < 0
            ? number.stripTrailingZeros()
            : number.setScale(options.decimals(), RoundingMode.HALF_UP))
        .toPlainString();
  }
}

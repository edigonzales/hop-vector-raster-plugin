package ch.so.agi.hop.vector.formats.shapefile;

import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import java.util.ArrayList;
import java.util.List;
import org.geotools.geometry.jts.CompoundCurvedGeometry;
import org.geotools.geometry.jts.SingleCurvedGeometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

/** Bridges GeoTools curved geometries and the shared Hop geometry value type. */
public final class CurveGeometryAdapter {

  private CurveGeometryAdapter() {}

  public static Geometry toHopGeometry(Geometry geometry) {
    if (geometry == null || CurveGeometrySupport.isCurveGeometry(geometry)) {
      return geometry;
    }

    Geometry converted;
    if (geometry instanceof org.geotools.geometry.jts.CurvePolygon polygon) {
      converted = toHopCurvePolygon(polygon);
    } else if (geometry instanceof org.geotools.geometry.jts.MultiCurve multiCurve) {
      converted = toHopMultiCurve(multiCurve);
    } else if (geometry instanceof org.geotools.geometry.jts.MultiSurface multiSurface) {
      converted = toHopMultiSurface(multiSurface);
    } else if (geometry instanceof SingleCurvedGeometry<?> singleCurve) {
      converted = toHopCircularString(singleCurve, geometry.getFactory());
    } else if (geometry instanceof CompoundCurvedGeometry<?> compoundCurve) {
      converted = toHopCompoundCurve(compoundCurve, geometry.getFactory());
    } else {
      return geometry;
    }

    return copyMetadata(geometry, converted);
  }

  public static Class<? extends Geometry> geometryBinding(Geometry geometry) {
    Geometry normalized = toHopGeometry(geometry);
    if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CircularString
        || normalized instanceof com.atolcd.hop.gis.geometry.curve.CompoundCurve) {
      return LineString.class;
    }
    if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CurvePolygon) {
      return Polygon.class;
    }
    if (normalized instanceof com.atolcd.hop.gis.geometry.curve.MultiCurve) {
      return MultiLineString.class;
    }
    if (normalized instanceof com.atolcd.hop.gis.geometry.curve.MultiSurface) {
      return MultiPolygon.class;
    }
    return normalized.getClass().asSubclass(Geometry.class);
  }

  /**
   * Shapefile has no native circular-arc representation. Convert Hop/GeoTools curves explicitly to
   * their already-tessellated JTS representation before handing them to the Shapefile writer.
   */
  public static Geometry toShapefileGeometry(Geometry geometry) {
    if (geometry == null) {
      return null;
    }

    Geometry normalized = toHopGeometry(geometry);
    GeometryFactory factory = normalized.getFactory();
    Geometry linearized;

    if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CircularString
        || normalized instanceof com.atolcd.hop.gis.geometry.curve.CompoundCurve) {
      linearized = factory.createLineString(normalized.getCoordinates());
    } else if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CurvePolygon polygon) {
      linearized = linearizePolygon(polygon, factory);
    } else if (normalized instanceof com.atolcd.hop.gis.geometry.curve.MultiCurve multiCurve) {
      LineString[] lines = new LineString[multiCurve.getNumGeometries()];
      for (int i = 0; i < lines.length; i++) {
        lines[i] = linearizeLine((LineString) multiCurve.getGeometryN(i), factory);
      }
      linearized = factory.createMultiLineString(lines);
    } else if (normalized instanceof com.atolcd.hop.gis.geometry.curve.MultiSurface multiSurface) {
      Polygon[] polygons = new Polygon[multiSurface.getNumGeometries()];
      for (int i = 0; i < polygons.length; i++) {
        polygons[i] = linearizePolygon((Polygon) multiSurface.getGeometryN(i), factory);
      }
      linearized = factory.createMultiPolygon(polygons);
    } else {
      return normalized;
    }

    return copyMetadata(normalized, linearized);
  }

  private static com.atolcd.hop.gis.geometry.curve.CircularString toHopCircularString(
      SingleCurvedGeometry<?> curve, GeometryFactory factory) {
    double[] ordinates = curve.getControlPoints();
    Coordinate[] controlPoints = new Coordinate[ordinates.length / 2];
    for (int i = 0; i < controlPoints.length; i++) {
      controlPoints[i] = new Coordinate(ordinates[i * 2], ordinates[i * 2 + 1]);
    }
    return new com.atolcd.hop.gis.geometry.curve.CircularString(controlPoints, factory);
  }

  private static com.atolcd.hop.gis.geometry.curve.CompoundCurve toHopCompoundCurve(
      CompoundCurvedGeometry<?> curve, GeometryFactory factory) {
    List<LineString> components = new ArrayList<>();
    for (LineString component : curve.getComponents()) {
      components.add(toHopLineString(component));
    }
    return new com.atolcd.hop.gis.geometry.curve.CompoundCurve(components, factory);
  }

  private static com.atolcd.hop.gis.geometry.curve.CurvePolygon toHopCurvePolygon(
      org.geotools.geometry.jts.CurvePolygon polygon) {
    List<LineString> rings = new ArrayList<>();
    rings.add(toHopLineString(polygon.getExteriorRing()));
    for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
      rings.add(toHopLineString(polygon.getInteriorRingN(i)));
    }
    return new com.atolcd.hop.gis.geometry.curve.CurvePolygon(rings, polygon.getFactory());
  }

  private static com.atolcd.hop.gis.geometry.curve.MultiCurve toHopMultiCurve(
      org.geotools.geometry.jts.MultiCurve multiCurve) {
    List<LineString> curves = new ArrayList<>();
    for (int i = 0; i < multiCurve.getNumGeometries(); i++) {
      curves.add(toHopLineString((LineString) multiCurve.getGeometryN(i)));
    }
    return new com.atolcd.hop.gis.geometry.curve.MultiCurve(curves, multiCurve.getFactory());
  }

  private static com.atolcd.hop.gis.geometry.curve.MultiSurface toHopMultiSurface(
      org.geotools.geometry.jts.MultiSurface multiSurface) {
    List<Polygon> surfaces = new ArrayList<>();
    for (int i = 0; i < multiSurface.getNumGeometries(); i++) {
      Geometry surface = toHopGeometry(multiSurface.getGeometryN(i));
      surfaces.add((Polygon) surface);
    }
    return new com.atolcd.hop.gis.geometry.curve.MultiSurface(surfaces, multiSurface.getFactory());
  }

  private static LineString toHopLineString(LineString line) {
    Geometry converted = toHopGeometry(line);
    if (converted instanceof LineString result) {
      return result;
    }
    throw new IllegalArgumentException(
        "Expected curve component to remain a LineString, got " + converted.getClass().getName());
  }

  private static LineString linearizeLine(LineString line, GeometryFactory factory) {
    Geometry normalized = toHopGeometry(line);
    if (CurveGeometrySupport.isCurveGeometry(normalized)) {
      return factory.createLineString(normalized.getCoordinates());
    }
    return (LineString) normalized;
  }

  private static Polygon linearizePolygon(Polygon polygon, GeometryFactory factory) {
    LinearRing shell = factory.createLinearRing(polygon.getExteriorRing().getCoordinates());
    LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
    for (int i = 0; i < holes.length; i++) {
      holes[i] = factory.createLinearRing(polygon.getInteriorRingN(i).getCoordinates());
    }
    return factory.createPolygon(shell, holes);
  }

  private static <T extends Geometry> T copyMetadata(Geometry source, T target) {
    target.setSRID(source.getSRID());
    target.setUserData(source.getUserData());
    return target;
  }
}

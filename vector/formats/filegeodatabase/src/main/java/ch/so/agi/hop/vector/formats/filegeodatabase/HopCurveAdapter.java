package ch.so.agi.hop.vector.formats.filegeodatabase;

import ch.so.agi.filegdb.geometry.*;
import ch.so.agi.filegdb.jts.*;
import com.atolcd.hop.gis.geometry.curve.*;
import java.util.*;
import org.locationtech.jts.geom.*;

/**
 * Exact circular curve transport. JTS's inherited coordinate view is deliberately not serialized.
 */
final class HopCurveAdapter {
  private final GeometryFactory factory;

  HopCurveAdapter(int srid) {
    factory = new GeometryFactory(new PrecisionModel(), srid);
  }

  FileGdbGeometry write(Geometry geometry) {
    if (geometry == null || geometry.isEmpty()) return null;
    if (geometry instanceof LineString line) return new FileGdbPolyline(List.of(part(line)));
    if (geometry instanceof MultiLineString lines) {
      List<FileGdbPart> parts = new ArrayList<>();
      for (int i = 0; i < lines.getNumGeometries(); i++)
        parts.add(part((LineString) lines.getGeometryN(i)));
      return new FileGdbPolyline(parts);
    }
    if (geometry instanceof Polygon p) return new FileGdbPolygon(polygonParts(p));
    if (geometry instanceof MultiPolygon ps) {
      List<FileGdbPart> parts = new ArrayList<>();
      for (int i = 0; i < ps.getNumGeometries(); i++)
        parts.addAll(polygonParts((Polygon) ps.getGeometryN(i)));
      return new FileGdbPolygon(parts);
    }
    return new JtsGeometryWriter().write(geometry);
  }

  private List<FileGdbPart> polygonParts(Polygon p) {
    List<LineString> rings = new ArrayList<>();
    if (p instanceof CurvePolygon cp) rings.addAll(cp.getCurveRings());
    else {
      rings.add(p.getExteriorRing());
      for (int i = 0; i < p.getNumInteriorRing(); i++) rings.add(p.getInteriorRingN(i));
    }
    List<FileGdbPart> parts = new ArrayList<>();
    for (int i = 0; i < rings.size(); i++) {
      FileGdbPart part = part(rings.get(i));
      boolean ccw =
          org.locationtech.jts.algorithm.Orientation.isCCW(
              CurveGeometrySupport.linearize(rings.get(i), 0.001).getCoordinates());
      if (ccw == (i == 0)) part = reverse(part);
      parts.add(part);
    }
    return parts;
  }

  private FileGdbPart part(LineString line) {
    List<FileGdbPoint> points = new ArrayList<>();
    List<FileGdbSegment> segments = new ArrayList<>();
    append(line, points, segments);
    return new FileGdbPart(points, segments);
  }

  private void append(LineString line, List<FileGdbPoint> points, List<FileGdbSegment> segments) {
    if (line instanceof CompoundCurve c) {
      for (var component : c.getComponents()) append(component, points, segments);
      return;
    }
    if (line instanceof CircularString c) {
      for (ArcSegment arc : c.getArcSegments()) {
        FileGdbPoint a = point(arc.getStartPoint()),
            m = point(arc.getMidPoint()),
            b = point(arc.getEndPoint());
        int start = join(points, a);
        var circle = ArcGeometry.of(a, b, new CircularArcSegment(0, m.x(), m.y(), false, false));
        if (circle == null) {
          points.add(m);
          points.add(b);
          continue;
        }
        double angle = Math.atan2(m.y() - circle.centerY(), m.x() - circle.centerX());
        double fraction =
            (circle.sweep() > 0
                    ? ArcGeometry.positive(angle - circle.start())
                    : ArcGeometry.positive(circle.start() - angle))
                / Math.abs(circle.sweep());
        boolean ccw = circle.sweep() > 0;
        if (linearOrdinate(a.z(), m.z(), b.z(), fraction)
            && linearOrdinate(a.m(), m.m(), b.m(), fraction)) {
          points.add(b);
          segments.add(
              new CircularArcSegment(start, circle.centerX(), circle.centerY(), true, ccw));
        } else {
          // Only non-linear ordinate profiles require storing the intermediate vertex.
          points.add(m);
          points.add(b);
          segments.add(
              new CircularArcSegment(start, circle.centerX(), circle.centerY(), true, ccw));
          segments.add(
              new CircularArcSegment(start + 1, circle.centerX(), circle.centerY(), true, ccw));
        }
      }
    } else {
      Coordinate[] coords = line.getCoordinates();
      if (coords.length == 0) return;
      join(points, point(coords[0]));
      for (int i = 1; i < coords.length; i++) points.add(point(coords[i]));
    }
  }

  private static boolean linearOrdinate(Double a, Double middle, Double b, double fraction) {
    if (a == null || middle == null || b == null) return a == null && middle == null && b == null;
    double expected = a + (b - a) * fraction;
    return Math.abs(middle - expected)
        <= 8 * Math.ulp(Math.max(1, Math.max(Math.abs(middle), Math.abs(expected))));
  }

  private static int join(List<FileGdbPoint> points, FileGdbPoint p) {
    if (points.isEmpty()) points.add(p);
    else if (points.getLast().x() != p.x() || points.getLast().y() != p.y())
      throw new IllegalArgumentException("Disconnected curve components");
    return points.size() - 1;
  }

  private static FileGdbPart reverse(FileGdbPart p) {
    List<FileGdbPoint> points = new ArrayList<>(p.points());
    Collections.reverse(points);
    List<FileGdbSegment> segments = new ArrayList<>();
    for (FileGdbSegment s : p.segments()) {
      CircularArcSegment a = (CircularArcSegment) s;
      segments.add(
          new CircularArcSegment(
              points.size() - 2 - a.startPointIndex(),
              a.interiorX(),
              a.interiorY(),
              a.byCenter(),
              !a.counterClockwise()));
    }
    segments.sort(Comparator.comparingInt(FileGdbSegment::startPointIndex));
    return new FileGdbPart(points, segments);
  }

  Geometry read(FileGdbGeometry geometry) {
    if (geometry == null) return null;
    List<FileGdbPart> parts =
        geometry instanceof FileGdbPolyline l
            ? l.parts()
            : geometry instanceof FileGdbPolygon p ? p.parts() : List.of();
    if (parts.stream().allMatch(p -> p.segments().isEmpty())
        || parts.stream()
            .flatMap(p -> p.segments().stream())
            .anyMatch(s -> !(s instanceof CircularArcSegment)))
      return new JtsGeometryReader(factory.getSRID()).read(geometry);
    List<LineString> curves = parts.stream().map(this::line).toList();
    if (geometry instanceof FileGdbPolyline)
      return curves.size() == 1 ? curves.getFirst() : new MultiCurve(curves, factory);
    List<LineString> shells = new ArrayList<>(), holes = new ArrayList<>();
    for (LineString ring : curves) {
      if (org.locationtech.jts.algorithm.Orientation.isCCW(ring.getCoordinates())) holes.add(ring);
      else shells.add(ring);
    }
    if (shells.isEmpty()) {
      shells.addAll(holes);
      holes.clear();
    }
    List<List<LineString>> rings = new ArrayList<>();
    for (LineString shell : shells) rings.add(new ArrayList<>(List.of(shell)));
    for (LineString hole : holes) {
      int best = -1;
      double area = Double.POSITIVE_INFINITY;
      for (int i = 0; i < shells.size(); i++) {
        Polygon polygon = factory.createPolygon(shells.get(i).getCoordinates());
        if (polygon.covers(factory.createPoint(hole.getCoordinate())) && polygon.getArea() < area) {
          best = i;
          area = polygon.getArea();
        }
      }
      if (best < 0) throw new IllegalArgumentException("Orphan curve polygon hole");
      rings.get(best).add(hole);
    }
    List<Polygon> polygons =
        rings.stream().map(r -> (Polygon) new CurvePolygon(r, factory)).toList();
    return polygons.size() == 1 ? polygons.getFirst() : new MultiSurface(polygons, factory);
  }

  private LineString line(FileGdbPart part) {
    Map<Integer, CircularArcSegment> arcs = new HashMap<>();
    for (var s : part.segments()) arcs.put(s.startPointIndex(), (CircularArcSegment) s);
    List<LineString> components = new ArrayList<>();
    for (int i = 0; i < part.points().size() - 1; i++) {
      FileGdbPoint a = part.points().get(i), b = part.points().get(i + 1);
      CircularArcSegment arc = arcs.get(i);
      if (arc == null) {
        components.add(factory.createLineString(new Coordinate[] {coordinate(a), coordinate(b)}));
        continue;
      }
      ArcGeometry circle = ArcGeometry.of(a, b, arc);
      if (circle == null) {
        components.add(factory.createLineString(new Coordinate[] {coordinate(a), coordinate(b)}));
        continue;
      }
      if (a.x() == b.x() && a.y() == b.y()) {
        Coordinate[] controls = new Coordinate[5];
        for (int k = 0; k <= 4; k++) {
          FileGdbPoint xy = circle.point(k / 4.0);
          controls[k] =
              coordinate(
                  new FileGdbPoint(
                      xy.x(),
                      xy.y(),
                      interpolate(a.z(), b.z(), k / 4.0),
                      interpolate(a.m(), b.m(), k / 4.0)));
        }
        controls[0] = coordinate(a);
        controls[4] = coordinate(b);
        components.add(new CircularString(controls, factory));
        continue;
      }
      FileGdbPoint mid = circle.point(0.5);
      components.add(
          new CircularString(
              new Coordinate[] {
                coordinate(a),
                coordinate(
                    new FileGdbPoint(mid.x(), mid.y(), mean(a.z(), b.z()), mean(a.m(), b.m()))),
                coordinate(b)
              },
              factory));
    }
    return components.size() == 1 ? components.getFirst() : new CompoundCurve(components, factory);
  }

  private static Double interpolate(Double a, Double b, double t) {
    return a == null || b == null ? null : a + (b - a) * t;
  }

  private static Double mean(Double a, Double b) {
    return a == null || b == null ? null : (a + b) / 2;
  }

  private static FileGdbPoint point(Coordinate c) {
    return new FileGdbPoint(
        c.x,
        c.y,
        Double.isNaN(c.getZ()) ? null : c.getZ(),
        Double.isNaN(c.getM()) ? null : c.getM());
  }

  private static Coordinate coordinate(FileGdbPoint p) {
    if (p.m() != null && p.z() == null) return new CoordinateXYM(p.x(), p.y(), p.m());
    return p.m() != null
        ? new CoordinateXYZM(p.x(), p.y(), p.z() == null ? Double.NaN : p.z(), p.m())
        : new Coordinate(p.x(), p.y(), p.z() == null ? Double.NaN : p.z());
  }
}

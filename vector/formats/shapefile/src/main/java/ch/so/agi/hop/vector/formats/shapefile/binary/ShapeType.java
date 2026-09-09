package ch.so.agi.hop.vector.formats.shapefile.binary;

import ch.so.agi.hop.vector.formats.shapefile.ShapefileMappingException;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum ShapeType {
  NULL(0),
  POINT(1),
  POLYLINE(3),
  POLYGON(5),
  MULTIPOINT(8),
  POINT_Z(11),
  POLYLINE_Z(13),
  POLYGON_Z(15),
  MULTIPOINT_Z(18),
  POINT_M(21),
  POLYLINE_M(23),
  POLYGON_M(25),
  MULTIPOINT_M(28),
  MULTIPATCH(31);

  private final int code;

  ShapeType(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  public static ShapeType fromCode(int code) throws ShapefileMappingException {
    for (ShapeType t : values()) {
      if (t.code == code) {
        return t;
      }
    }
    String known =
        Arrays.stream(values()).map(t -> t.code + "=" + t.name()).collect(Collectors.joining(", "));
    throw new ShapefileMappingException(
        "Unknown or unsupported shape type code: " + code + ". Known types: [" + known + "]");
  }

  public int family() {
    return code == 0 ? 0 : code % 10;
  }

  public boolean hasZ() {
    return code >= 11 && code <= 18;
  }

  public boolean hasM() {
    return code >= 21 && code <= 28;
  }
}

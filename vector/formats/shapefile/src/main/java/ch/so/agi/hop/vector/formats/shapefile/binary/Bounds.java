// Adapted from ilitransformer, Copyright (c) 2026 Stefan Ziegler, MIT. See THIRD-PARTY-NOTICES.md.
package ch.so.agi.hop.vector.formats.shapefile.binary;

public record Bounds(double xmin, double ymin, double xmax, double ymax) {

  public static Bounds read(EndianByteBuffer buf) {
    double xmin = buf.getLittleDouble();
    double ymin = buf.getLittleDouble();
    double xmax = buf.getLittleDouble();
    double ymax = buf.getLittleDouble();
    return new Bounds(xmin, ymin, xmax, ymax);
  }
}

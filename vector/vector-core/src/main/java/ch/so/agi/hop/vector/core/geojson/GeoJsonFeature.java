package ch.so.agi.hop.vector.core.geojson;

import org.locationtech.jts.geom.Geometry;

/** One feature, with structured payloads represented as lossless JSON text. */
public record GeoJsonFeature(
    Geometry geometry,
    Geometry fallbackGeometry,
    String propertiesJson,
    String idJson,
    String contextJson) {}

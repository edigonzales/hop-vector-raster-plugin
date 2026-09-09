package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.core.*;

final class VectorProviders {
  static VectorProvider get(VectorFormat f) {
    return switch (f) {
      case SHAPEFILE ->
          new ch.so.agi.hop.vector.formats.shapefile.ShapefileProvider(
              new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
      case GEOPACKAGE ->
          new ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider(
              new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
      case FLATGEOBUF -> new ch.so.agi.hop.vector.formats.flatgeobuf.FlatGeobufProvider();
      case PARQUET ->
          new ch.so.agi.hop.vector.formats.parquet.ParquetProvider(
              new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
      case ARCINFO_GENERATE -> new ch.so.agi.hop.vector.formats.generate.GenerateProvider();
    };
  }
}

package ch.so.agi.hop.vector.transforms;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class VectorSchemaProbe {

  record FieldDefinition(String name, String type, int length, int precision) {
    FieldDefinition(String name, String type) {
      this(name, type, -1, -1);
    }
  }

  record LayerDefinition(
      String name,
      String geometryFieldName,
      String geometryType,
      List<FieldDefinition> fields,
      String dimension,
      int srid,
      ch.so.agi.hop.vector.core.LayerSchema.XYPrecision xyPrecision) {
    LayerDefinition(
        String name,
        String field,
        String type,
        List<FieldDefinition> fields,
        String dimension,
        int srid) {
      this(name, field, type, fields, dimension, srid, null);
    }

    LayerDefinition(String name, String field, String type, List<FieldDefinition> fields) {
      this(name, field, type, fields, "XY", 0);
    }

    LayerDefinition {
      fields = List.copyOf(fields);
    }
  }

  private VectorSchemaProbe() {}

  static List<LayerDefinition> readLayers(Path file) throws IOException {
    return readLayers(file, "AUTO");
  }

  static List<LayerDefinition> readLayers(Path file, String format) throws IOException {
    return readLayers(new ch.so.agi.hop.vector.core.ReadRequest(file, "", ""), format);
  }

  static List<LayerDefinition> readLayers(
      ch.so.agi.hop.vector.core.ReadRequest request, String format) throws IOException {
    java.nio.file.Path file = request.file();
    try {
      List<LayerDefinition> result = new ArrayList<>();
      for (var schema :
          VectorProviders.get(ch.so.agi.hop.vector.core.VectorFormat.resolve(format, file))
              .layers(request)) {
        List<FieldDefinition> fields = new ArrayList<>();
        for (var field : schema.rowMeta().getValueMetaList())
          if (!field.getName().equals(schema.geometryColumn()))
            fields.add(
                new FieldDefinition(
                    field.getName(),
                    displayType(field.getType()),
                    field.getLength(),
                    field.getPrecision()));
        result.add(
            new LayerDefinition(
                schema.name(),
                schema.geometryColumn(),
                schema.geometryType(),
                fields,
                schema.geometry() == null ? "" : schema.geometry().dimension(),
                schema.srid(),
                schema.xyPrecision()));
      }
      return List.copyOf(result);
    } catch (Exception e) {
      throw new IOException("Unable to inspect vector schema", e);
    }
  }

  static LayerDefinition resolveLayer(List<LayerDefinition> layers, String requestedLayer) {
    if (layers == null || layers.isEmpty()) {
      throw new IllegalArgumentException("Vector dataset contains no layers.");
    }
    if (requestedLayer == null || requestedLayer.isBlank()) {
      return layers.get(0);
    }
    for (LayerDefinition layer : layers) {
      if (layer.name().equals(requestedLayer)) {
        return layer;
      }
    }
    for (LayerDefinition layer : layers) {
      if (layer.name().equalsIgnoreCase(requestedLayer)) {
        return layer;
      }
    }
    throw new IllegalArgumentException("Layer '" + requestedLayer + "' not found.");
  }

  static String formatFieldPreview(LayerDefinition layer) {
    StringBuilder preview = new StringBuilder();
    preview.append("Layer: ").append(layer.name()).append('\n');
    preview
        .append("Geometry type: ")
        .append(layer.geometryType().isBlank() ? "(none)" : layer.geometryType())
        .append('\n');
    preview
        .append("Geometry field: ")
        .append(layer.geometryFieldName().isBlank() ? "(none)" : layer.geometryFieldName())
        .append('\n');
    preview
        .append("Dimension: ")
        .append(layer.dimension())
        .append("\nCRS: ")
        .append(layer.srid())
        .append("\n");
    if (layer.xyPrecision() != null) {
      var p = layer.xyPrecision();
      preview
          .append("XY resolution: ")
          .append(p.resolution())
          .append("\nXY tolerance: ")
          .append(p.tolerance())
          .append("\nXY origin: ")
          .append(p.xOrigin())
          .append(", ")
          .append(p.yOrigin())
          .append('\n');
    }
    preview.append("Fields:");
    if (layer.fields().isEmpty()) {
      preview.append("\n- (none)");
    } else {
      for (FieldDefinition field : layer.fields()) {
        preview
            .append("\n- ")
            .append(field.name())
            .append(" (")
            .append(field.type())
            .append(')')
            .append(" length=")
            .append(field.length())
            .append(" precision=")
            .append(field.precision());
      }
    }
    return preview.toString();
  }

  private static String displayType(int type) {
    return switch (type) {
      case org.apache.hop.core.row.IValueMeta.TYPE_INTEGER -> "INTEGER";
      case org.apache.hop.core.row.IValueMeta.TYPE_NUMBER -> "NUMBER";
      case org.apache.hop.core.row.IValueMeta.TYPE_BOOLEAN -> "BOOLEAN";
      case org.apache.hop.core.row.IValueMeta.TYPE_DATE -> "DATE";
      case org.apache.hop.core.row.IValueMeta.TYPE_BINARY -> "BINARY";
      case org.apache.hop.core.row.IValueMeta.TYPE_TIMESTAMP -> "TIMESTAMP";
      default -> "STRING";
    };
  }
}

package ch.so.agi.hop.vector.core;

public interface VectorProvider {
  VectorFormat format();

  java.util.List<LayerSchema> layers(ReadRequest request) throws Exception;

  default java.util.List<LayerSchema> layers(java.nio.file.Path file) throws Exception {
    return layers(new ReadRequest(file, "", ""));
  }

  VectorSource open(ReadRequest request) throws Exception;

  default VectorSource open(java.nio.file.Path file, String layer, String geometryField)
      throws Exception {
    return open(new ReadRequest(file, layer, geometryField));
  }

  VectorSink create(WriteRequest request) throws Exception;

  static LayerSchema resolveLayer(java.util.List<LayerSchema> layers, String name) {
    if (layers.isEmpty()) throw new IllegalArgumentException("Vector dataset contains no layers");
    if (name == null || name.isBlank()) return layers.get(0);
    for (var s : layers) if (s.name().equals(name)) return s;
    for (var s : layers) if (s.name().equalsIgnoreCase(name)) return s;
    throw new IllegalArgumentException("Layer '" + name + "' not found");
  }
}

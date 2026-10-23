import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.plugins.*;
import org.apache.hop.core.row.value.ValueMetaPluginType;

public class RuntimeIdentityProbe {
  public static void main(String[] args) throws Exception {
    HopEnvironment.init();
    PluginRegistry r = PluginRegistry.getInstance();
    IPlugin raster = r.findPluginWithId(TransformPluginType.class, "SOGIS_RASTER_VALUE_CLIP");
    IPlugin geometry = r.findPluginWithId(ValueMetaPluginType.class, "43663879");
    ClassLoader first = r.getClassLoader(args[0].equals("raster-first") ? raster : geometry);
    ClassLoader a = r.getClassLoader(raster), b = r.getClassLoader(geometry);
    if (a != b) throw new AssertionError("Different classloaders");
    for (String n :
        new String[] {
          "com.atolcd.hop.gis.geometry.codec.GeometryCodec",
          "com.atolcd.hop.gis.geometry.codec.JsonSupport",
          "tools.jackson.core.json.JsonFactory",
          "org.locationtech.jts.geom.GeometryFactory",
          "com.atolcd.hop.core.row.value.ValueMetaGeometry",
          "org.geotools.ows.wmts.WebMapTileServer",
          "org.eclipse.imagen.OperationRegistry",
          "org.eclipse.imagen.PlanarImage"
        }) {
      Class<?> ca = a.loadClass(n), cb = b.loadClass(n);
      if (ca != cb) throw new AssertionError("Different classes " + n);
      java.nio.file.Path origin =
          java.nio.file.Path.of(ca.getProtectionDomain().getCodeSource().getLocation().toURI())
              .toRealPath();
      java.nio.file.Path central =
          java.nio.file.Path.of("plugins/misc/hop-geometry-type").toRealPath();
      if (!origin.startsWith(central))
        throw new AssertionError("Runtime outside central Geometry plugin: " + origin);
      System.out.println(
          "IDENTITY OK "
              + args[0]
              + " "
              + n
              + " "
              + ca.getProtectionDomain().getCodeSource().getLocation());
    }
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    Class<?> codecClass = a.loadClass("com.atolcd.hop.gis.geometry.codec.GeometryCodec");
    Class<?> limits = a.loadClass("com.atolcd.hop.gis.geometry.codec.GeometryReadLimits");
    Object codec =
        codecClass.getConstructor(limits).newInstance(limits.getMethod("defaults").invoke(null));
    Class<?> readOptions = a.loadClass("com.atolcd.hop.gis.geometry.codec.GeoJsonReadOptions");
    Object result =
        codecClass
            .getMethod("readGeoJson", String.class, readOptions)
            .invoke(
                codec,
                "{\"type\":\"Point\",\"coordinates\":[47,7],\"coordRefSys\":\"http://www.opengis.net/def/crs/EPSG/0/4326\",\"conformsTo\":[\"http://www.opengis.net/spec/json-fg-1/1.0/conf/core\"]}",
                readOptions.getMethod("defaults").invoke(null));
    Object point = result.getClass().getMethod("geometry").invoke(result);
    Object coordinate = point.getClass().getMethod("getCoordinate").invoke(point);
    if (coordinate.getClass().getField("x").getDouble(coordinate) != 7
        || coordinate.getClass().getField("y").getDouble(coordinate) != 47)
      throw new AssertionError("JSON-FG EPSG axis order was not mapped to internal XY");
    if (Thread.currentThread().getContextClassLoader() != original)
      throw new AssertionError("JSON-FG changed thread context loader");
    for (String id :
        new String[] {"SOGIS_GEOJSON_FEATURE_DECODER", "SOGIS_GEOJSON_FEATURE_ENCODER"}) {
      IPlugin feature = r.findPluginWithId(TransformPluginType.class, id);
      if (feature == null || r.getClassLoader(feature) != a)
        throw new AssertionError("Missing/shared-loader Feature plugin " + id);
    }
    System.out.println("GEOJSON RUNTIME OK " + args[0]);
    // The Geometry bootstrap must have initialized ImageN during HopEnvironment.init().
    // Keep Hop's original context loader here: changing it would mask missing bootstrap logic.
    {
      Class<?> imagen = a.loadClass("org.eclipse.imagen.ImageN");
      Object instance = imagen.getMethod("getDefaultInstance").invoke(null);
      Object registry = imagen.getMethod("getOperationRegistry").invoke(instance);
      Class<?> registryClass = a.loadClass("org.eclipse.imagen.OperationRegistry");
      for (String operation : List.of("ColorReduction", "ColorInversion")) {
        for (String kind : List.of("Descriptor", "Factory")) {
          Object value =
              registryClass
                  .getMethod("get" + kind, String.class, String.class)
                  .invoke(registry, "rendered", "org.geotools." + operation);
          String expected =
              "org.geotools.image.palette."
                  + operation
                  + (kind.equals("Descriptor") ? "Descriptor" : "CRIF");
          if (value == null || !value.getClass().getName().equals(expected))
            throw new AssertionError("Missing ImageN registration: " + expected);
          if (value.getClass().getClassLoader() != a)
            throw new AssertionError(
                "ImageN registration outside shared plugin loader: " + expected);
        }
        System.out.println("IMAGEN REGISTRY OK " + args[0] + " " + operation);
      }
    }
    Set<String> registrations = new HashSet<>();
    List<URL> resources =
        java.util.Collections.list(a.getResources("META-INF/registryFile.imagen"));
    if (resources.isEmpty())
      throw new AssertionError("No Imagen registry resource from Geometry Type");
    for (URL resource : resources)
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (line.isEmpty() || line.startsWith("#")) continue;
          String[] fields = line.split("\\s+");
          if ((fields[0].equals("descriptor") || fields[0].equals("rendered"))
              && !registrations.add(line))
            throw new AssertionError("Duplicate Imagen registration " + line + " from " + resource);
        }
      }
    for (String n :
        new String[] {
          "ch.so.agi.hop.raster.RasterDataset", "ch.so.agi.hop.raster.type.ValueMetaRaster"
        }) {
      Class<?> ca = a.loadClass(n), cb = b.loadClass(n);
      if (ca != cb) throw new AssertionError("Different raster class identity: " + n);
      var origin =
          java.nio.file.Path.of(ca.getProtectionDomain().getCodeSource().getLocation().toURI())
              .toRealPath();
      if (!origin.startsWith(java.nio.file.Path.of("plugins/misc/hop-raster-type").toRealPath()))
        throw new AssertionError("Raster class outside central type plugin: " + origin);
    }
    Class<?> backend = a.loadClass("ch.so.agi.hop.raster.geotools.GeoToolsRasterBackend");
    if (backend != b.loadClass(backend.getName()))
      throw new AssertionError("Different raster backend class identity");
    var backendOrigin =
        java.nio.file.Path.of(backend.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toRealPath();
    if (!backendOrigin.startsWith(
        java.nio.file.Path.of("plugins/transforms/vector-raster/lib").toRealPath()))
      throw new AssertionError("Raster backend outside Vector/Raster lib: " + backendOrigin);
  }
}

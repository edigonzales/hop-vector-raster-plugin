import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.value.ValueMetaPluginType;

/** Invoked only against the deliberately downgraded, disposable installed runtime. */
public final class GeoJsonLegacyRuntimeProbe {
  public static void main(String[] args) throws Exception {
    try {
      HopEnvironment.init();
      var registry = PluginRegistry.getInstance();
      var plugin = registry.findPluginWithId(ValueMetaPluginType.class, "43663879");
      var loader = registry.getClassLoader(plugin);
      var bootstrap =
          loader.loadClass("ch.so.agi.hop.vector.transforms.GeometryRuntimeClassLoaderBootstrap");
      var initialize = bootstrap.getDeclaredMethod("initialize", PluginRegistry.class);
      initialize.setAccessible(true);
      initialize.invoke(null, registry);
    } catch (Throwable failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause.getMessage() != null && cause.getMessage().contains("GeoJSON codec API")) {
          System.out.println("LEGACY GEOJSON GUARD OK: " + cause.getMessage());
          return;
        }
      }
      throw failure;
    }
    throw new AssertionError("A Geometry Type runtime without the GeoJSON API was accepted");
  }
}

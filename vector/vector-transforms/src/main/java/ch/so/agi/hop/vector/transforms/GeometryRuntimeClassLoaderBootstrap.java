package ch.so.agi.hop.vector.transforms;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.value.ValueMetaPluginType;
import org.apache.hop.core.variables.IVariables;

/** Initializes the shared Geometry runtime before a pipeline can load raster implementation data. */
@ExtensionPoint(
    id = "VectorRasterGeometryRuntimeClassLoaderBootstrap",
    extensionPointId = "HopEnvironmentAfterInit",
    description = "Initialize the shared Geometry runtime for Vector/Raster",
    classLoaderGroup = GeometryRuntimeClassLoaderBootstrap.GEOMETRY_CLASSLOADER_GROUP)
public final class GeometryRuntimeClassLoaderBootstrap
    implements IExtensionPoint<PluginRegistry> {

  static final String GEOMETRY_CLASSLOADER_GROUP = "sogeo-geometry";
  static final String GEOMETRY_VALUE_META_PLUGIN_ID = "43663879";

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, PluginRegistry pluginRegistry) throws HopException {
    initialize(pluginRegistry);
  }

  static void initialize(PluginRegistry pluginRegistry) throws HopException {
    IPlugin geometryPlugin =
        pluginRegistry.findPluginWithId(
            ValueMetaPluginType.class, GEOMETRY_VALUE_META_PLUGIN_ID);
    if (geometryPlugin == null) {
      throw new HopException(
          "Vector/Raster requires the hop-geometry-type plugin (ValueMeta plugin id "
              + GEOMETRY_VALUE_META_PLUGIN_ID
              + ")");
    }
    if (!GEOMETRY_CLASSLOADER_GROUP.equals(geometryPlugin.getClassLoaderGroup())) {
      throw new HopException(
          "Vector/Raster requires hop-geometry-type to use classloader group '"
              + GEOMETRY_CLASSLOADER_GROUP
              + "' but found '"
              + geometryPlugin.getClassLoaderGroup()
              + "'");
    }

    try {
      ClassLoader geometryLoader = pluginRegistry.getClassLoader(geometryPlugin);
      geometryLoader.loadClass("org.eclipse.imagen.PlanarImage");
    } catch (HopPluginException | ClassNotFoundException e) {
      throw new HopException("Unable to initialize the shared Geometry runtime", e);
    }
  }
}

package ch.so.agi.hop.support.geotools;

import java.util.ServiceConfigurationError;
import tech.units.indriya.function.Calculus;
import tech.units.indriya.function.DefaultNumberSystem;

/**
 * Runtime initialization needed when GeoTools runs inside Apache Hop's grouped plugin classloader.
 */
public final class GeoToolsRuntimeSupport {

  private static volatile boolean initialized;

  private GeoToolsRuntimeSupport() {}

  public static void initialize() {
    if (initialized) {
      return;
    }

    synchronized (GeoToolsRuntimeSupport.class) {
      if (initialized) {
        return;
      }

      try {
        // On a regular Maven/application classpath Indriya discovers this implementation through
        // META-INF/services. In Hop a shared plugin classloader can make that service resource
        // invisible even though the implementation class itself is available.
        Calculus.currentNumberSystem();
      } catch (IllegalArgumentException | ServiceConfigurationError e) {
        Calculus.setCurrentNumberSystem(new DefaultNumberSystem());
      }

      initialized = true;
    }
  }
}

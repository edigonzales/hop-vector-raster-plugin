package ch.so.agi.hop.support.geotools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import tech.units.indriya.function.Calculus;

class GeoToolsRuntimeSupportTest {

  private static final String NUMBER_SYSTEM_SERVICE =
      "META-INF/services/tech.units.indriya.spi.NumberSystem";

  @Test
  void initializesIndriyaAndDecodesSwissCrs() throws Exception {
    GeoToolsRuntimeSupport.initialize();
    GeoToolsRuntimeSupport.initialize();

    assertThat(Calculus.currentNumberSystem()).isNotNull();

    CoordinateReferenceSystem crs = CRS.decode("EPSG:2056", true);
    assertThat(crs).isNotNull();
    assertThat(crs.getName().getCode()).isNotBlank();
  }

  @Test
  void fallsBackWhenNumberSystemServiceIsInvisible() throws Exception {
    URL pluginClasses =
        GeoToolsRuntimeSupport.class.getProtectionDomain().getCodeSource().getLocation();
    URL indriyaJar = Calculus.class.getProtectionDomain().getCodeSource().getLocation();

    try (ServiceHidingClassLoader loader =
        new ServiceHidingClassLoader(
            new URL[] {pluginClasses, indriyaJar}, ClassLoader.getPlatformClassLoader())) {
      Class<?> runtimeSupport =
          loader.loadClass("ch.so.agi.hop.support.geotools.GeoToolsRuntimeSupport");
      Method initialize = runtimeSupport.getDeclaredMethod("initialize");
      initialize.setAccessible(true);
      initialize.invoke(null);

      Class<?> calculus = loader.loadClass("tech.units.indriya.function.Calculus");
      Object numberSystem = calculus.getMethod("currentNumberSystem").invoke(null);

      assertThat(numberSystem).isNotNull();
      assertThat(numberSystem.getClass().getName())
          .isEqualTo("tech.units.indriya.function.DefaultNumberSystem");
    }
  }

  private static final class ServiceHidingClassLoader extends URLClassLoader {

    private ServiceHidingClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    public URL getResource(String name) {
      if (NUMBER_SYSTEM_SERVICE.equals(name)) {
        return null;
      }
      return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      if (NUMBER_SYSTEM_SERVICE.equals(name)) {
        return Collections.emptyEnumeration();
      }
      return super.getResources(name);
    }
  }
}

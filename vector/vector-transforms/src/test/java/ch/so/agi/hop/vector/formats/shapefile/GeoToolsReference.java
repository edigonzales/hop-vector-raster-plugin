package ch.so.agi.hop.vector.formats.shapefile;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.geotools.api.data.DataStore;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.feature.type.GeometryDescriptor;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

public final class GeoToolsReference {

  enum VectorFormat {
    SHAPEFILE,
    GEOPACKAGE
  }

  private GeoToolsReference() {}

  public static VectorFormat detectFormat(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".shp")) {
      return VectorFormat.SHAPEFILE;
    }
    if (name.endsWith(".gpkg")) {
      return VectorFormat.GEOPACKAGE;
    }
    throw new IllegalArgumentException(
        "Unsupported vector file format for '" + file + "'. MVP supports .shp and .gpkg.");
  }

  public static DataStore open(Path file) throws IOException {
    GeoToolsRuntimeSupport.initialize();

    Path absolute = file.toAbsolutePath().normalize();
    if (!Files.isRegularFile(absolute)) {
      throw new IOException("Vector dataset does not exist: " + absolute);
    }

    return switch (detectFormat(absolute)) {
      case SHAPEFILE -> {
        DataStore store = new ShapefileDataStoreFactory().createDataStore(absolute.toUri().toURL());
        if (store == null) {
          throw new IOException("GeoTools could not open shapefile: " + absolute);
        }
        yield store;
      }
      case GEOPACKAGE -> openGeoPackageDataStore(absolute, true);
    };
  }

  public static DataStore create(Path file) throws IOException {
    GeoToolsRuntimeSupport.initialize();

    Path absolute = file.toAbsolutePath().normalize();
    if (Files.exists(absolute)) {
      throw new IOException("Output dataset already exists: " + absolute);
    }
    Path parent = absolute.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    return switch (detectFormat(absolute)) {
      case SHAPEFILE -> {
        Map<String, Object> params = new HashMap<>();
        params.put("url", absolute.toUri().toURL());
        params.put("create spatial index", Boolean.TRUE);
        DataStore store = new ShapefileDataStoreFactory().createNewDataStore(params);
        if (store == null) {
          throw new IOException("GeoTools could not create shapefile: " + absolute);
        }
        yield store;
      }
      case GEOPACKAGE -> openGeoPackageDataStore(absolute, false);
    };
  }

  private static DataStore openGeoPackageDataStore(Path file, boolean readOnly) throws IOException {
    GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();
    Map<String, Object> params = new HashMap<>();
    params.put(GeoPkgDataStoreFactory.DBTYPE.key, "geopkg");
    params.put(GeoPkgDataStoreFactory.DATABASE.key, file.toFile());
    params.put(GeoPkgDataStoreFactory.READ_ONLY.key, readOnly);
    DataStore store = factory.createDataStore(params);
    if (store == null) {
      throw new IOException("GeoTools could not open GeoPackage: " + file);
    }
    return store;
  }

  public static String resolveTypeName(DataStore store, String requestedLayer) throws IOException {
    String[] typeNames = store.getTypeNames();
    if (typeNames.length == 0) {
      throw new IOException("Vector dataset contains no layers");
    }
    if (requestedLayer == null || requestedLayer.isBlank()) {
      return typeNames[0];
    }
    for (String typeName : typeNames) {
      if (typeName.equals(requestedLayer)) {
        return typeName;
      }
    }
    for (String typeName : typeNames) {
      if (typeName.equalsIgnoreCase(requestedLayer)) {
        return typeName;
      }
    }
    throw new IOException("Layer '" + requestedLayer + "' not found");
  }

  public static RowMeta toHopRowMeta(SimpleFeatureType featureType, String geometryFieldName) {
    RowMeta rowMeta = new RowMeta();
    for (AttributeDescriptor descriptor : featureType.getAttributeDescriptors()) {
      if (descriptor instanceof GeometryDescriptor) {
        continue;
      }
      rowMeta.addValueMeta(toHopValueMeta(descriptor));
    }
    rowMeta.addValueMeta(
        new ValueMetaGeometry(resolveGeometryOutputFieldName(featureType, geometryFieldName)));
    return rowMeta;
  }

  public static String resolveGeometryOutputFieldName(
      SimpleFeatureType featureType, String geometryFieldNameOverride) {
    if (geometryFieldNameOverride != null && !geometryFieldNameOverride.isBlank()) {
      return geometryFieldNameOverride.trim();
    }

    GeometryDescriptor geometryDescriptor = featureType.getGeometryDescriptor();
    if (geometryDescriptor != null
        && geometryDescriptor.getLocalName() != null
        && !geometryDescriptor.getLocalName().isBlank()) {
      return geometryDescriptor.getLocalName();
    }
    return "geometry";
  }

  public static IValueMeta toHopValueMeta(AttributeDescriptor descriptor) {
    String name = descriptor.getLocalName();
    Class<?> binding = descriptor.getType().getBinding();
    if (binding == null) {
      return new ValueMetaString(name);
    }
    if (Boolean.class.isAssignableFrom(binding) || boolean.class.equals(binding)) {
      return new ValueMetaBoolean(name);
    }
    if (Byte.class.isAssignableFrom(binding)
        || Short.class.isAssignableFrom(binding)
        || Integer.class.isAssignableFrom(binding)
        || Long.class.isAssignableFrom(binding)
        || byte.class.equals(binding)
        || short.class.equals(binding)
        || int.class.equals(binding)
        || long.class.equals(binding)) {
      return new ValueMetaInteger(name);
    }
    if (Number.class.isAssignableFrom(binding)
        || float.class.equals(binding)
        || double.class.equals(binding)) {
      return new ValueMetaNumber(name);
    }
    if (java.util.Date.class.isAssignableFrom(binding)) {
      return new ValueMetaDate(name);
    }
    return new ValueMetaString(name);
  }

  public static SimpleFeatureType buildFeatureType(
      String layerName, IRowMeta inputRowMeta, int geometryFieldIndex, Geometry sampleGeometry)
      throws Exception {
    GeoToolsRuntimeSupport.initialize();

    SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
    builder.setName(layerName);

    CoordinateReferenceSystem crs = coordinateReferenceSystem(sampleGeometry);
    if (crs != null) {
      builder.setCRS(crs);
    }

    for (int i = 0; i < inputRowMeta.size(); i++) {
      if (i == geometryFieldIndex) {
        continue;
      }
      IValueMeta valueMeta = inputRowMeta.getValueMeta(i);
      builder.add(valueMeta.getName(), toJavaBinding(valueMeta));
    }

    String geometryName = inputRowMeta.getValueMeta(geometryFieldIndex).getName();
    builder.add(geometryName, CurveGeometryAdapter.geometryBinding(sampleGeometry));
    builder.setDefaultGeometry(geometryName);
    return builder.buildFeatureType();
  }

  public static Class<?> toJavaBinding(IValueMeta valueMeta) {
    return switch (valueMeta.getType()) {
      case IValueMeta.TYPE_BOOLEAN -> Boolean.class;
      case IValueMeta.TYPE_INTEGER -> Long.class;
      case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER -> Double.class;
      case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> java.util.Date.class;
      default -> String.class;
    };
  }

  public static Object normalizeAttributeValue(IValueMeta valueMeta, Object value) {
    if (value == null) {
      return null;
    }
    return switch (valueMeta.getType()) {
      case IValueMeta.TYPE_BOOLEAN ->
          value instanceof Boolean ? value : Boolean.valueOf(value.toString());
      case IValueMeta.TYPE_INTEGER -> value instanceof Number number ? number.longValue() : value;
      case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER ->
          value instanceof Number number ? number.doubleValue() : value;
      case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> value;
      default -> value.toString();
    };
  }

  private static CoordinateReferenceSystem coordinateReferenceSystem(Geometry geometry)
      throws Exception {
    GeoToolsRuntimeSupport.initialize();

    if (geometry == null || geometry.getSRID() <= 0) {
      return null;
    }
    return CRS.decode("EPSG:" + geometry.getSRID(), true);
  }
}

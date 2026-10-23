package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.core.*;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "SOGIS_VECTOR_READER",
    name = "Vector Reader",
    description = "Read vector features from Shapefile, GeoPackage, FileGDB or GeoJSON",
    image = "ch/so/agi/hop/vector/transforms/icons/vector-reader.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"vector", "shapefile", "geopackage", "gis"})
public class VectorReaderMeta extends BaseTransformMeta<VectorReader, VectorReaderData> {

  public VectorReaderMeta() {
    setDefault();
  }

  @HopMetadataProperty private String fileGdbXMin = "";

  public String getFileGdbXMin() {
    return fileGdbXMin;
  }

  public void setFileGdbXMin(String value) {
    fileGdbXMin = value;
  }

  @HopMetadataProperty private String fileGdbYMin = "";

  public String getFileGdbYMin() {
    return fileGdbYMin;
  }

  public void setFileGdbYMin(String value) {
    fileGdbYMin = value;
  }

  @HopMetadataProperty private String fileGdbXMax = "";

  public String getFileGdbXMax() {
    return fileGdbXMax;
  }

  public void setFileGdbXMax(String value) {
    fileGdbXMax = value;
  }

  @HopMetadataProperty private String fileGdbYMax = "";

  public String getFileGdbYMax() {
    return fileGdbYMax;
  }

  public void setFileGdbYMax(String value) {
    fileGdbYMax = value;
  }

  @HopMetadataProperty private String fileName;
  @HopMetadataProperty private String format = "AUTO";

  public String getFormat() {
    return format;
  }

  public void setFormat(String value) {
    format = value;
  }

  @HopMetadataProperty private String layerName;
  @HopMetadataProperty private String geometryFieldName;

  @HopMetadataProperty private String charset = "";

  public String getCharset() {
    return charset;
  }

  public void setCharset(String v) {
    charset = v == null ? "" : v;
  }

  @HopMetadataProperty private String timezone = "UTC";

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String v) {
    timezone = v == null ? "" : v;
  }

  @HopMetadataProperty private String crsOverride = "";

  public String getCrsOverride() {
    return crsOverride;
  }

  public void setCrsOverride(String v) {
    crsOverride = v == null ? "" : v;
  }

  @HopMetadataProperty private String geoJsonProfile = "AUTO";

  public String getGeoJsonProfile() {
    return geoJsonProfile;
  }

  public void setGeoJsonProfile(String value) {
    geoJsonProfile = value == null ? "" : value;
  }

  @HopMetadataProperty private String geoJsonFallbackField = "geometry_wgs84";

  public String getGeoJsonFallbackField() {
    return geoJsonFallbackField;
  }

  public void setGeoJsonFallbackField(String value) {
    geoJsonFallbackField = value == null ? "" : value;
  }

  @HopMetadataProperty private String geoJsonPropertiesField = "properties_json";

  public String getGeoJsonPropertiesField() {
    return geoJsonPropertiesField;
  }

  public void setGeoJsonPropertiesField(String value) {
    geoJsonPropertiesField = value == null ? "" : value;
  }

  @HopMetadataProperty private String geoJsonIdField = "feature_id_json";

  public String getGeoJsonIdField() {
    return geoJsonIdField;
  }

  public void setGeoJsonIdField(String value) {
    geoJsonIdField = value == null ? "" : value;
  }

  @HopMetadataProperty private String geoJsonContextField = "geojson_context_json";

  public String getGeoJsonContextField() {
    return geoJsonContextField;
  }

  public void setGeoJsonContextField(String value) {
    geoJsonContextField = value == null ? "" : value;
  }

  @HopMetadataProperty private String geoJsonMappings = "[]";

  public String getGeoJsonMappings() {
    return geoJsonMappings;
  }

  public void setGeoJsonMappings(String value) {
    geoJsonMappings = value == null ? "" : value;
  }

  @HopMetadataProperty private boolean geoJsonDropZ = false;

  public boolean isGeoJsonDropZ() {
    return geoJsonDropZ;
  }

  public void setGeoJsonDropZ(boolean value) {
    geoJsonDropZ = value;
  }

  @HopMetadataProperty private boolean geoJsonDropM = false;

  public boolean isGeoJsonDropM() {
    return geoJsonDropM;
  }

  public void setGeoJsonDropM(boolean value) {
    geoJsonDropM = value;
  }

  @HopMetadataProperty private boolean geoJsonEmptyToNull = false;

  public boolean isGeoJsonEmptyToNull() {
    return geoJsonEmptyToNull;
  }

  public void setGeoJsonEmptyToNull(boolean value) {
    geoJsonEmptyToNull = value;
  }

  public ch.so.agi.hop.vector.core.GeoJsonOptions geoJsonOptions(IVariables vars) {
    java.util.function.Function<String, String> resolve =
        value -> vars == null ? value : vars.resolve(value);
    try {
      return new ch.so.agi.hop.vector.core.GeoJsonOptions(
          new ch.so.agi.hop.vector.core.geojson.GeoJsonFeatureOptions(
              com.atolcd.hop.gis.geometry.codec.GeoJsonProfile.valueOf(
                  resolve.apply(geoJsonProfile)),
              resolve.apply(geometryFieldName),
              resolve.apply(geoJsonFallbackField),
              resolve.apply(geoJsonPropertiesField),
              resolve.apply(geoJsonIdField),
              resolve.apply(geoJsonContextField),
              geoJsonDropZ,
              geoJsonDropM,
              geoJsonEmptyToNull,
              ch.so.agi.hop.vector.core.geojson.GeoJsonFieldMapping.parseJson(
                  resolve.apply(geoJsonMappings))),
          false);
    } catch (com.atolcd.hop.gis.geometry.codec.GeometryCodecException e) {
      throw new IllegalArgumentException("Invalid GeoJSON mappings: " + e.getMessage(), e);
    }
  }

  @Override
  public void setDefault() {
    geoJsonProfile = "AUTO";
    geoJsonFallbackField = "geometry_wgs84";
    geoJsonPropertiesField = "properties_json";
    geoJsonIdField = "feature_id_json";
    geoJsonContextField = "geojson_context_json";
    geoJsonMappings = "[]";
    geoJsonDropZ = false;
    geoJsonDropM = false;
    geoJsonEmptyToNull = false;
    charset = "";
    timezone = "UTC";
    crsOverride = "";
    fileName = "";
    format = "AUTO";
    layerName = "";
    geometryFieldName = "";
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    String resolvedFileName = resolve(variables, fileName);
    if (resolvedFileName.isBlank() || resolvedFileName.contains("${")) {
      return;
    }

    try (var source =
        VectorProviders.get(
                ch.so.agi.hop.vector.core.VectorFormat.resolve(format, Path.of(resolvedFileName)))
            .open(request(variables, Diagnostics.NONE))) {
      IRowMeta detected = source.schema().rowMeta();
      for (var field : detected.getValueMetaList()) rowMeta.addValueMeta(field.clone());
    } catch (Exception e) {
      if (isDebug())
        logDebug("Unable to probe vector schema for design-time metadata: " + e.getMessage());
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (fileName == null || fileName.isBlank()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "Vector file name is required", transformMeta));
      return;
    }
    String resolved = resolve(variables, fileName);
    if (!resolved.contains("${")) {
      try {
        if (!ch.so.agi.hop.vector.core.VectorFormat.resolve(format, Path.of(resolved)).readable())
          throw new IllegalArgumentException("Format is not readable");
      } catch (IllegalArgumentException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
        return;
      }
    }
    remarks.add(
        new CheckResult(ICheckResult.TYPE_RESULT_OK, "Vector Reader is configured", transformMeta));
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return "";
    }
    return variables == null ? value.trim() : variables.resolve(value).trim();
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getLayerName() {
    return layerName;
  }

  public void setLayerName(String layerName) {
    this.layerName = layerName;
  }

  public String getGeometryFieldName() {
    return geometryFieldName;
  }

  public void setGeometryFieldName(String geometryFieldName) {
    this.geometryFieldName = geometryFieldName;
  }

  public ReadRequest request(IVariables vars, Diagnostics diagnostics) {
    VectorFormat selected = VectorFormat.resolve(format, Path.of(resolve(vars, fileName)));
    return new ReadRequest(
        Path.of(resolve(vars, fileName)),
        selected == VectorFormat.GEOJSON ? "" : resolve(vars, layerName),
        resolve(vars, geometryFieldName),
        selected == VectorFormat.GEOJSON ? "" : resolve(vars, crsOverride),
        VectorFormat.resolve(format, Path.of(resolve(vars, fileName))) == VectorFormat.GEOJSON
            ? geoJsonOptions(vars)
            : VectorFormat.resolve(format, Path.of(resolve(vars, fileName)))
                    == VectorFormat.SHAPEFILE
                ? new ShapefileOptions(resolve(vars, charset), resolve(vars, timezone), List.of())
                : VectorFormat.resolve(format, Path.of(resolve(vars, fileName)))
                        == VectorFormat.FILEGEODATABASE
                    ? new FileGeodatabaseOptions(
                        "LEGACY",
                        null,
                        null,
                        null,
                        null,
                        true,
                        FileGeodatabaseOptions.bounds(
                            resolve(vars, fileGdbXMin),
                            resolve(vars, fileGdbYMin),
                            resolve(vars, fileGdbXMax),
                            resolve(vars, fileGdbYMax)))
                    : new FormatOptions.None(),
        diagnostics);
  }
}

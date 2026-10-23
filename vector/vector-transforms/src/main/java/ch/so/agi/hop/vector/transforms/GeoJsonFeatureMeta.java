package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.core.geojson.*;
import com.atolcd.hop.gis.geometry.codec.*;
import java.util.*;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.*;
import org.apache.hop.pipeline.transform.*;

/** Common persisted settings; expressions are resolved only when a transform executes. */
public abstract class GeoJsonFeatureMeta<T extends BaseTransform<?, ?>, D extends BaseTransformData>
    extends BaseTransformMeta<T, D> {
  @HopMetadataProperty private String inputField = "feature_json";
  @HopMetadataProperty private String outputField = "feature_json";
  @HopMetadataProperty private String profile = "AUTO";
  @HopMetadataProperty private String geometryField = "geometry";
  @HopMetadataProperty private String fallbackField = "geometry_wgs84";
  @HopMetadataProperty private String propertiesField = "properties_json";
  @HopMetadataProperty private String idField = "feature_id_json";
  @HopMetadataProperty private String contextField = "geojson_context_json";
  @HopMetadataProperty private String mappingsJson = "[]";
  @HopMetadataProperty private String onDataError = "FAIL";
  @HopMetadataProperty private boolean dropZ;
  @HopMetadataProperty private boolean dropM;
  @HopMetadataProperty private boolean emptyToNull;

  public enum OnDataError {
    FAIL,
    RETURN_NULL,
    ROUTE_TO_ERROR
  }

  abstract boolean decoder();

  public String getInputField() {
    return inputField;
  }

  public void setInputField(String value) {
    inputField = value == null ? "" : value;
  }

  public String getOutputField() {
    return outputField;
  }

  public void setOutputField(String value) {
    outputField = value == null ? "" : value;
  }

  public String getProfile() {
    return profile;
  }

  public void setProfile(String value) {
    profile = value == null ? "" : value;
  }

  public String getGeometryField() {
    return geometryField;
  }

  public void setGeometryField(String value) {
    geometryField = value == null ? "" : value;
  }

  public String getFallbackField() {
    return fallbackField;
  }

  public void setFallbackField(String value) {
    fallbackField = value == null ? "" : value;
  }

  public String getPropertiesField() {
    return propertiesField;
  }

  public void setPropertiesField(String value) {
    propertiesField = value == null ? "" : value;
  }

  public String getIdField() {
    return idField;
  }

  public void setIdField(String value) {
    idField = value == null ? "" : value;
  }

  public String getContextField() {
    return contextField;
  }

  public void setContextField(String value) {
    contextField = value == null ? "" : value;
  }

  public String getMappingsJson() {
    return mappingsJson;
  }

  public void setMappingsJson(String value) {
    mappingsJson = value == null ? "" : value;
  }

  public String getOnDataError() {
    return onDataError;
  }

  public void setOnDataError(String value) {
    onDataError = value == null ? "" : value;
  }

  public boolean isDropZ() {
    return dropZ;
  }

  public void setDropZ(boolean value) {
    dropZ = value;
  }

  public boolean isDropM() {
    return dropM;
  }

  public void setDropM(boolean value) {
    dropM = value;
  }

  public boolean isEmptyToNull() {
    return emptyToNull;
  }

  public void setEmptyToNull(boolean value) {
    emptyToNull = value;
  }

  @Override
  public void setDefault() {
    inputField = "feature_json";
    outputField = "feature_json";
    profile = "AUTO";
    geometryField = "geometry";
    fallbackField = "geometry_wgs84";
    propertiesField = "properties_json";
    idField = "feature_id_json";
    contextField = "geojson_context_json";
    mappingsJson = "[]";
    onDataError = "FAIL";
    dropZ = false;
    dropM = false;
    emptyToNull = false;
  }

  @Override
  public void check(
      java.util.List<org.apache.hop.core.ICheckResult> remarks,
      org.apache.hop.pipeline.PipelineMeta pipelineMeta,
      TransformMeta transform,
      IRowMeta previous,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider provider) {
    try {
      GeoJsonFeatureOptions configured = options(variables);
      OnDataError mode = errorMode();
      if (mode == OnDataError.ROUTE_TO_ERROR && !transform.isDoingErrorHandling())
        throw new IllegalArgumentException("ROUTE_TO_ERROR requires an active error hop");
      if (previous != null) {
        String required = decoder() ? resolve(variables, inputField) : configured.geometryField();
        int index = previous.indexOfValue(required);
        if (index < 0) throw new IllegalArgumentException("Input field not found: " + required);
        if (decoder()
            ? previous.getValueMeta(index).getType() != IValueMeta.TYPE_STRING
            : !(previous.getValueMeta(index)
                instanceof com.atolcd.hop.core.row.value.ValueMetaGeometry))
          throw new IllegalArgumentException(
              decoder()
                  ? "Feature input must be String"
                  : "Geometry input must be native Geometry");
        if (!decoder()) GeoJsonFeatureRows.validateEncodingSchema(previous, configured);
        getFields(previous.clone(), transform.getName(), null, null, variables, provider);
      }
      remarks.add(
          new org.apache.hop.core.CheckResult(
              org.apache.hop.core.ICheckResult.TYPE_RESULT_OK,
              "GeoJSON Feature settings are valid",
              transform));
    } catch (Exception e) {
      remarks.add(
          new org.apache.hop.core.CheckResult(
              org.apache.hop.core.ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transform));
    }
  }

  public GeoJsonFeatureOptions options(IVariables variables) throws GeometryCodecException {
    return new GeoJsonFeatureOptions(
        GeoJsonProfile.valueOf(resolve(variables, profile)),
        resolve(variables, geometryField),
        resolve(variables, fallbackField),
        resolve(variables, propertiesField),
        resolve(variables, idField),
        resolve(variables, contextField),
        dropZ,
        dropM,
        emptyToNull,
        GeoJsonFieldMapping.parseJson(resolve(variables, mappingsJson)));
  }

  static String resolve(IVariables variables, String text) {
    return text == null ? "" : variables == null ? text : variables.resolve(text);
  }

  public OnDataError errorMode() {
    return OnDataError.valueOf(onDataError);
  }

  @Override
  public boolean supportsErrorHandling() {
    return true;
  }

  @Override
  public void getFields(
      IRowMeta row,
      String origin,
      IRowMeta[] info,
      TransformMeta next,
      IVariables variables,
      IHopMetadataProvider provider)
      throws HopTransformException {
    try {
      IRowMeta added;
      if (decoder()) added = GeoJsonFeatureRows.schema(options(variables));
      else {
        added = new RowMeta();
        String output = resolve(variables, outputField);
        if (output.isBlank()) throw new IllegalArgumentException("Output field is required");
        added.addValueMeta(new ValueMetaString(output));
      }
      for (var field : added.getValueMetaList()) {
        if (row.indexOfValue(field.getName()) >= 0)
          throw new IllegalArgumentException("Output field already exists: " + field.getName());
        field.setOrigin(origin);
        row.addValueMeta(field);
      }
    } catch (Exception e) {
      throw new HopTransformException("Invalid GeoJSON Feature settings", e);
    }
  }
}

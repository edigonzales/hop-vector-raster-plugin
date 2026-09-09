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
    description = "Read vector features from Shapefile or GeoPackage",
    image = "ch/so/agi/hop/vector/transforms/icons/vector-reader.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"vector", "shapefile", "geopackage", "gis"})
public class VectorReaderMeta extends BaseTransformMeta<VectorReader, VectorReaderData> {

  public VectorReaderMeta() {
    setDefault();
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

  @Override
  public void setDefault() {
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
    return new ReadRequest(
        Path.of(resolve(vars, fileName)),
        resolve(vars, layerName),
        resolve(vars, geometryFieldName),
        resolve(vars, crsOverride),
        VectorFormat.resolve(format, Path.of(resolve(vars, fileName))) == VectorFormat.SHAPEFILE
            ? new ShapefileOptions(resolve(vars, charset), resolve(vars, timezone), List.of())
            : new FormatOptions.None(),
        diagnostics);
  }
}

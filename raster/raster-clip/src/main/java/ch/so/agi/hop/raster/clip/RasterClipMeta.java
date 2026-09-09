package ch.so.agi.hop.raster.clip;

import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "SOGIS_RASTER_CLIP",
    name = "Raster Clip (GeoTools)",
    description = "Raster Clip (GeoTools)",
    image = "ch/so/agi/hop/raster/clip/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public class RasterClipMeta extends BaseTransformMeta<RasterClipTransform, RasterClipData> {
  public RasterClipMeta() {
    setDefault();
  }

  @HopMetadataProperty private String source;
  @HopMetadataProperty private boolean sourceField;
  @HopMetadataProperty private String geometryField;
  @HopMetadataProperty private String explicitCrs;
  @HopMetadataProperty private int band;
  @HopMetadataProperty private String noData;
  @HopMetadataProperty private String prefix;
  @HopMetadataProperty private String clipMethod;
  @HopMetadataProperty private String minX;
  @HopMetadataProperty private String minY;
  @HopMetadataProperty private String maxX;
  @HopMetadataProperty private String maxY;
  @HopMetadataProperty private boolean bboxFields;
  @HopMetadataProperty private String output;
  @HopMetadataProperty private boolean outputField;
  @HopMetadataProperty private boolean overwrite;

  @Override
  public void setDefault() {
    source = "";
    sourceField = false;
    geometryField = "geometry";
    explicitCrs = "";
    band = 1;
    noData = "";
    prefix = "raster_";
    clipMethod = "POLYGON";
    minX = "";
    minY = "";
    maxX = "";
    maxY = "";
    bboxFields = false;
    output = "";
    outputField = false;
    overwrite = false;
  }

  public void validateSettings() {
    if (source == null || source.isBlank())
      throw new IllegalArgumentException("Input raster is required");
    if (band < 1) throw new IllegalArgumentException("Band must be >= 1");
    if (prefix == null || prefix.isBlank())
      throw new IllegalArgumentException("Output prefix is required");
    if (!java.util.List.of("POLYGON", "BOUNDING_BOX").contains(clipMethod))
      throw new IllegalArgumentException("Choose a clip method");
    if (output == null || output.isBlank())
      throw new IllegalArgumentException("Output file is required");
    if ("POLYGON".equals(clipMethod) && (geometryField == null || geometryField.isBlank()))
      throw new IllegalArgumentException("Geometry field is required");
    if ("BOUNDING_BOX".equals(clipMethod)
        && (explicitCrs.isBlank()
            || minX.isBlank()
            || minY.isBlank()
            || maxX.isBlank()
            || maxY.isBlank()))
      throw new IllegalArgumentException(
          "Bounding box requires four coordinates and an explicit CRS");
  }

  @Override
  public boolean supportsErrorHandling() {
    return true;
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta next,
      IVariables variables,
      IHopMetadataProvider provider) {
    validateSettings();
    String p = variables.resolve(prefix);
    addField(rowMeta, new ValueMetaString(p + "output_file"));
    addField(rowMeta, new ValueMetaString(p + "status"));
  }

  private static void addField(IRowMeta meta, IValueMeta field) {
    if (meta.indexOfValue(field.getName()) >= 0)
      throw new IllegalArgumentException("Output field already exists: " + field.getName());
    meta.addValueMeta(field);
  }

  @Override
  public void check(
      java.util.List<org.apache.hop.core.ICheckResult> remarks,
      PipelineMeta pipeline,
      TransformMeta transform,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables vars,
      IHopMetadataProvider provider) {
    try {
      validateSettings();
      remarks.add(
          new org.apache.hop.core.CheckResult(
              org.apache.hop.core.ICheckResult.TYPE_RESULT_OK, "Settings are valid", transform));
    } catch (RuntimeException e) {
      remarks.add(
          new org.apache.hop.core.CheckResult(
              org.apache.hop.core.ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transform));
    }
  }

  public String getSource() {
    return source;
  }

  public void setSource(String value) {
    source = value == null ? "" : value;
  }

  public boolean isSourceField() {
    return sourceField;
  }

  public void setSourceField(boolean value) {
    sourceField = value;
  }

  public String getGeometryField() {
    return geometryField;
  }

  public void setGeometryField(String value) {
    geometryField = value == null ? "" : value;
  }

  public String getExplicitCrs() {
    return explicitCrs;
  }

  public void setExplicitCrs(String value) {
    explicitCrs = value == null ? "" : value;
  }

  public int getBand() {
    return band;
  }

  public void setBand(int value) {
    band = value;
  }

  public String getNoData() {
    return noData;
  }

  public void setNoData(String value) {
    noData = value == null ? "" : value;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String value) {
    prefix = value == null ? "" : value;
  }

  public String getClipMethod() {
    return clipMethod;
  }

  public void setClipMethod(String value) {
    clipMethod = value == null ? "" : value;
  }

  public String getMinX() {
    return minX;
  }

  public void setMinX(String value) {
    minX = value == null ? "" : value;
  }

  public String getMinY() {
    return minY;
  }

  public void setMinY(String value) {
    minY = value == null ? "" : value;
  }

  public String getMaxX() {
    return maxX;
  }

  public void setMaxX(String value) {
    maxX = value == null ? "" : value;
  }

  public String getMaxY() {
    return maxY;
  }

  public void setMaxY(String value) {
    maxY = value == null ? "" : value;
  }

  public boolean isBboxFields() {
    return bboxFields;
  }

  public void setBboxFields(boolean value) {
    bboxFields = value;
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(String value) {
    output = value == null ? "" : value;
  }

  public boolean isOutputField() {
    return outputField;
  }

  public void setOutputField(boolean value) {
    outputField = value;
  }

  public boolean isOverwrite() {
    return overwrite;
  }

  public void setOverwrite(boolean value) {
    overwrite = value;
  }
}

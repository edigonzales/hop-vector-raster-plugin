package ch.so.agi.hop.raster.reproject;

import ch.so.agi.hop.raster.core.RasterReprojectRequest;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "SOGIS_RASTER_REPROJECT",
    name = "Raster Reproject / Resample (GeoTools)",
    description = "Reproject and resample all raster bands to a target pixel grid",
    image = "ch/so/agi/hop/raster/reproject/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public class RasterReprojectMeta
    extends BaseTransformMeta<RasterReprojectTransform, RasterReprojectData> {
  public RasterReprojectMeta() {
    setDefault();
  }

  @HopMetadataProperty private String source;
  @HopMetadataProperty private String targetCrs;
  @HopMetadataProperty private String resolutionX;
  @HopMetadataProperty private String resolutionY;
  @HopMetadataProperty private String extentMode;
  @HopMetadataProperty private String minX;
  @HopMetadataProperty private String minY;
  @HopMetadataProperty private String maxX;
  @HopMetadataProperty private String maxY;
  @HopMetadataProperty private String interpolation;
  @HopMetadataProperty private String outputType;
  @HopMetadataProperty private String sourceNoData;
  @HopMetadataProperty private String outputNoData;
  @HopMetadataProperty private String output;
  @HopMetadataProperty private String prefix;
  @HopMetadataProperty private boolean sourceField;
  @HopMetadataProperty private boolean targetCrsField;
  @HopMetadataProperty private boolean resolutionFields;
  @HopMetadataProperty private boolean bboxFields;
  @HopMetadataProperty private boolean outputField;
  @HopMetadataProperty private boolean overwrite;

  @Override
  public void setDefault() {
    source = "";
    targetCrs = "";
    resolutionX = "";
    resolutionY = "";
    extentMode = "AUTO";
    minX = "";
    minY = "";
    maxX = "";
    maxY = "";
    interpolation = "NEAREST";
    outputType = "AUTO";
    sourceNoData = "";
    outputNoData = "";
    output = "";
    prefix = "raster_";
    sourceField = false;
    targetCrsField = false;
    resolutionFields = false;
    bboxFields = false;
    outputField = false;
    overwrite = false;
  }

  public void validateSettings() {
    if (source.isBlank() || output.isBlank())
      throw new IllegalArgumentException("Input raster and output file are required");
    if (resolutionX.isBlank() || resolutionY.isBlank())
      throw new IllegalArgumentException("Both pixel sizes are required");
    if (targetCrsField && targetCrs.isBlank())
      throw new IllegalArgumentException("Target CRS field is required");
    if (prefix.isBlank()) throw new IllegalArgumentException("Output prefix is required");
    if (!java.util.List.of("AUTO", "BOUNDING_BOX").contains(extentMode))
      throw new IllegalArgumentException("Choose an extent mode");
    if ("BOUNDING_BOX".equals(extentMode)
        && (minX.isBlank() || minY.isBlank() || maxX.isBlank() || maxY.isBlank()))
      throw new IllegalArgumentException("Bounding box requires four coordinates in target CRS");
    RasterReprojectRequest.Interpolation.valueOf(interpolation);
    RasterReprojectRequest.OutputType.valueOf(outputType);
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
      IHopMetadataProvider provider) {
    validateSettings();
    String p = variables.resolve(prefix);
    if (p == null || p.isBlank())
      throw new IllegalArgumentException("Resolved output prefix is empty");
    for (String suffix : new String[] {"output_file", "status"}) {
      String name = p + suffix;
      if (row.indexOfValue(name) >= 0)
        throw new IllegalArgumentException("Output field already exists: " + name);
      var field = new ValueMetaString(name);
      field.setOrigin(origin);
      row.addValueMeta(field);
    }
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

  public void copyFrom(RasterReprojectMeta other) {
    source = other.source;
    targetCrs = other.targetCrs;
    resolutionX = other.resolutionX;
    resolutionY = other.resolutionY;
    extentMode = other.extentMode;
    minX = other.minX;
    minY = other.minY;
    maxX = other.maxX;
    maxY = other.maxY;
    interpolation = other.interpolation;
    outputType = other.outputType;
    sourceNoData = other.sourceNoData;
    outputNoData = other.outputNoData;
    output = other.output;
    prefix = other.prefix;
    sourceField = other.sourceField;
    targetCrsField = other.targetCrsField;
    resolutionFields = other.resolutionFields;
    bboxFields = other.bboxFields;
    outputField = other.outputField;
    overwrite = other.overwrite;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String v) {
    source = v == null ? "" : v;
  }

  public String getTargetCrs() {
    return targetCrs;
  }

  public void setTargetCrs(String v) {
    targetCrs = v == null ? "" : v;
  }

  public String getResolutionX() {
    return resolutionX;
  }

  public void setResolutionX(String v) {
    resolutionX = v == null ? "" : v;
  }

  public String getResolutionY() {
    return resolutionY;
  }

  public void setResolutionY(String v) {
    resolutionY = v == null ? "" : v;
  }

  public String getExtentMode() {
    return extentMode;
  }

  public void setExtentMode(String v) {
    extentMode = v == null ? "" : v;
  }

  public String getMinX() {
    return minX;
  }

  public void setMinX(String v) {
    minX = v == null ? "" : v;
  }

  public String getMinY() {
    return minY;
  }

  public void setMinY(String v) {
    minY = v == null ? "" : v;
  }

  public String getMaxX() {
    return maxX;
  }

  public void setMaxX(String v) {
    maxX = v == null ? "" : v;
  }

  public String getMaxY() {
    return maxY;
  }

  public void setMaxY(String v) {
    maxY = v == null ? "" : v;
  }

  public String getInterpolation() {
    return interpolation;
  }

  public void setInterpolation(String v) {
    interpolation = v == null ? "" : v;
  }

  public String getOutputType() {
    return outputType;
  }

  public void setOutputType(String v) {
    outputType = v == null ? "" : v;
  }

  public String getSourceNoData() {
    return sourceNoData;
  }

  public void setSourceNoData(String v) {
    sourceNoData = v == null ? "" : v;
  }

  public String getOutputNoData() {
    return outputNoData;
  }

  public void setOutputNoData(String v) {
    outputNoData = v == null ? "" : v;
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(String v) {
    output = v == null ? "" : v;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String v) {
    prefix = v == null ? "" : v;
  }

  public boolean isSourceField() {
    return sourceField;
  }

  public void setSourceField(boolean v) {
    sourceField = v;
  }

  public boolean isTargetCrsField() {
    return targetCrsField;
  }

  public void setTargetCrsField(boolean v) {
    targetCrsField = v;
  }

  public boolean isResolutionFields() {
    return resolutionFields;
  }

  public void setResolutionFields(boolean v) {
    resolutionFields = v;
  }

  public boolean isBboxFields() {
    return bboxFields;
  }

  public void setBboxFields(boolean v) {
    bboxFields = v;
  }

  public boolean isOutputField() {
    return outputField;
  }

  public void setOutputField(boolean v) {
    outputField = v;
  }

  public boolean isOverwrite() {
    return overwrite;
  }

  public void setOverwrite(boolean v) {
    overwrite = v;
  }
}

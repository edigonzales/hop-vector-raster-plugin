package ch.so.agi.hop.generate;

import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "ARCINFO_GENERATE_WRITER",
    name = "ArcInfo Generate Writer",
    description = "ArcInfo Generate Writer",
    image = "ch/so/agi/hop/generate/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public class ArcInfoGenerateWriterMeta
    extends BaseTransformMeta<ArcInfoGenerateWriterTransform, ArcInfoGenerateWriterData> {
  public ArcInfoGenerateWriterMeta() {
    setDefault();
  }

  @HopMetadataProperty private String output;
  @HopMetadataProperty private String geometryField;
  @HopMetadataProperty private String geometryType;
  @HopMetadataProperty private String dimension;
  @HopMetadataProperty private String idField;
  @HopMetadataProperty private long startId;
  @HopMetadataProperty private boolean discardExtraOrdinates;
  @HopMetadataProperty private int decimals;
  @HopMetadataProperty private boolean comma;
  @HopMetadataProperty private boolean skipEmpty;
  @HopMetadataProperty private boolean overwrite;

  @Override
  public void setDefault() {
    output = "";
    geometryField = "geometry";
    geometryType = "LINE";
    dimension = "XY";
    idField = "";
    startId = 1;
    discardExtraOrdinates = false;
    decimals = -1;
    comma = false;
    skipEmpty = false;
    overwrite = false;
  }

  public void validateSettings() {
    if (output == null || output.isBlank() || geometryField == null || geometryField.isBlank())
      throw new IllegalArgumentException("Output and geometry field are required");
    options();
  }

  @Override
  public boolean supportsErrorHandling() {
    return false;
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

  public GenerateEncoder.Options options() {
    return new GenerateEncoder.Options(
        GenerateEncoder.Type.valueOf(geometryType),
        GenerateEncoder.Dimension.valueOf(dimension),
        discardExtraOrdinates,
        decimals,
        comma);
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(String value) {
    output = value == null ? "" : value;
  }

  public String getGeometryField() {
    return geometryField;
  }

  public void setGeometryField(String value) {
    geometryField = value == null ? "" : value;
  }

  public String getGeometryType() {
    return geometryType;
  }

  public void setGeometryType(String value) {
    geometryType = value == null ? "" : value;
  }

  public String getDimension() {
    return dimension;
  }

  public void setDimension(String value) {
    dimension = value == null ? "" : value;
  }

  public String getIdField() {
    return idField;
  }

  public void setIdField(String value) {
    idField = value == null ? "" : value;
  }

  public long getStartId() {
    return startId;
  }

  public void setStartId(long value) {
    startId = value;
  }

  public boolean isDiscardExtraOrdinates() {
    return discardExtraOrdinates;
  }

  public void setDiscardExtraOrdinates(boolean value) {
    discardExtraOrdinates = value;
  }

  public int getDecimals() {
    return decimals;
  }

  public void setDecimals(int value) {
    decimals = value;
  }

  public boolean isComma() {
    return comma;
  }

  public void setComma(boolean value) {
    comma = value;
  }

  public boolean isSkipEmpty() {
    return skipEmpty;
  }

  public void setSkipEmpty(boolean value) {
    skipEmpty = value;
  }

  public boolean isOverwrite() {
    return overwrite;
  }

  public void setOverwrite(boolean value) {
    overwrite = value;
  }
}

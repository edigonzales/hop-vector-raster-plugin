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
    id = "SOGIS_VECTOR_WRITER",
    name = "Vector Writer",
    description = "Write vector features to Shapefile, GeoPackage, GENERATE, FlatGeobuf or Parquet",
    image = "ch/so/agi/hop/vector/transforms/icons/vector-writer.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"vector", "shapefile", "geopackage", "gis"})
public class VectorWriterMeta extends BaseTransformMeta<VectorWriter, VectorWriterData> {

  public VectorWriterMeta() {
    setDefault();
  }

  @HopMetadataProperty private boolean flatGeobufIndex = true;

  public boolean isFlatGeobufIndex() {
    return flatGeobufIndex;
  }

  public void setFlatGeobufIndex(boolean value) {
    flatGeobufIndex = value;
  }

  @HopMetadataProperty private boolean flatGeobufSkipEmpty = false;

  public boolean isFlatGeobufSkipEmpty() {
    return flatGeobufSkipEmpty;
  }

  public void setFlatGeobufSkipEmpty(boolean value) {
    flatGeobufSkipEmpty = value;
  }

  @HopMetadataProperty private String parquetLogicalType = "GEOMETRY";

  public String getParquetLogicalType() {
    return parquetLogicalType;
  }

  public void setParquetLogicalType(String value) {
    parquetLogicalType = value;
  }

  @HopMetadataProperty private String parquetAlgorithm = "SPHERICAL";

  public String getParquetAlgorithm() {
    return parquetAlgorithm;
  }

  public void setParquetAlgorithm(String value) {
    parquetAlgorithm = value;
  }

  @HopMetadataProperty private String parquetCompression = "GZIP";

  public String getParquetCompression() {
    return parquetCompression;
  }

  public void setParquetCompression(String value) {
    parquetCompression = value;
  }

  @HopMetadataProperty private long parquetRowGroupSize = 128L * 1024 * 1024;

  public long getParquetRowGroupSize() {
    return parquetRowGroupSize;
  }

  public void setParquetRowGroupSize(long value) {
    parquetRowGroupSize = value;
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
  @HopMetadataProperty private String geometryField;

  @HopMetadataProperty private String geometryType = "LINE";
  @HopMetadataProperty private String dimension = "XY";
  @HopMetadataProperty private String idField = "";
  @HopMetadataProperty private long startId = 1;
  @HopMetadataProperty private boolean discardExtraOrdinates;
  @HopMetadataProperty private int decimals = -1;
  @HopMetadataProperty private boolean comma;
  @HopMetadataProperty private boolean skipEmpty;
  @HopMetadataProperty private boolean overwrite;

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

  @HopMetadataProperty private String layerGeometryType = "AUTO";

  public String getLayerGeometryType() {
    return layerGeometryType;
  }

  public void setLayerGeometryType(String v) {
    layerGeometryType = v == null ? "" : v;
  }

  @HopMetadataProperty private String layerDimension = "AUTO";

  public String getLayerDimension() {
    return layerDimension;
  }

  public void setLayerDimension(String v) {
    layerDimension = v == null ? "" : v;
  }

  @HopMetadataProperty(groupKey = "shapefileFields", key = "field")
  private java.util.List<ShapefileFieldMeta> shapefileFields = new java.util.ArrayList<>();

  public java.util.List<ShapefileFieldMeta> getShapefileFields() {
    return shapefileFields;
  }

  public void setShapefileFields(java.util.List<ShapefileFieldMeta> v) {
    shapefileFields = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
  }

  @Override
  public void setDefault() {
    parquetRowGroupSize = 128L * 1024 * 1024;
    parquetCompression = "GZIP";
    parquetAlgorithm = "SPHERICAL";
    parquetLogicalType = "GEOMETRY";
    flatGeobufSkipEmpty = false;
    flatGeobufIndex = true;
    charset = "";
    timezone = "UTC";
    crsOverride = "";
    layerGeometryType = "AUTO";
    layerDimension = "AUTO";
    shapefileFields = new java.util.ArrayList<>();
    fileName = "";
    format = "AUTO";
    layerName = "";
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

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    // Sink transform: no output rows.
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
              ICheckResult.TYPE_RESULT_ERROR,
              "Vector output file name is required",
              transformMeta));
      return;
    }

    String resolvedFileName = resolve(variables, fileName);
    if (!resolvedFileName.contains("${")) {
      try {
        ch.so.agi.hop.vector.core.VectorFormat.resolve(format, Path.of(resolvedFileName));
      } catch (IllegalArgumentException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
        return;
      }
    }

    if (geometryField == null || geometryField.isBlank()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "Geometry input field is required", transformMeta));
      return;
    }

    if (prev != null
        && prev.size() > 0
        && prev.indexOfValue(resolve(variables, geometryField)) < 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Geometry input field '" + geometryField + "' was not found",
              transformMeta));
      return;
    }

    remarks.add(
        new CheckResult(ICheckResult.TYPE_RESULT_OK, "Vector Writer is configured", transformMeta));
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

  public String getGeometryField() {
    return geometryField;
  }

  public void setGeometryField(String geometryField) {
    this.geometryField = geometryField;
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

  public void validateSettings() {
    if (fileName == null || fileName.isBlank() || geometryField == null || geometryField.isBlank())
      throw new IllegalArgumentException("Output and geometry field are required");
    if (("AUTO".equals(format) || format == null || format.isBlank()) && fileName.contains("${"))
      return;
    options(VectorFormat.resolve(format, Path.of(fileName)), null);
    if (ch.so.agi.hop.vector.core.VectorFormat.resolve(format, Path.of(fileName))
        == ch.so.agi.hop.vector.core.VectorFormat.ARCINFO_GENERATE)
      new ch.so.agi.hop.vector.formats.generate.GenerateEncoder.Options(
          ch.so.agi.hop.vector.formats.generate.GenerateEncoder.Type.valueOf(geometryType),
          ch.so.agi.hop.vector.formats.generate.GenerateEncoder.Dimension.valueOf(dimension),
          discardExtraOrdinates,
          decimals,
          comma);
  }

  public FormatOptions options(VectorFormat f, IVariables vars) {
    java.util.function.Function<String, String> resolve = v -> vars == null ? v : vars.resolve(v);
    if (f == VectorFormat.FLATGEOBUF)
      return new FlatGeobufOptions(flatGeobufIndex, flatGeobufSkipEmpty, overwrite);
    if (f == VectorFormat.PARQUET)
      return new ParquetOptions(
          parquetLogicalType, parquetAlgorithm, parquetCompression, parquetRowGroupSize, overwrite);
    if (f == VectorFormat.ARCINFO_GENERATE)
      return new GenerateOptions(
          geometryType,
          dimension,
          discardExtraOrdinates,
          decimals,
          comma,
          skipEmpty,
          overwrite,
          resolve.apply(idField),
          startId);
    if (f == VectorFormat.SHAPEFILE)
      return new ShapefileOptions(
          resolve.apply(charset),
          resolve.apply(timezone),
          shapefileFields.stream()
              .map(
                  v ->
                      new ShapefileOptions.Field(
                          v.getSource(), v.getTarget(), v.getWidth(), v.getScale()))
              .toList());
    return new FormatOptions.None();
  }

  public FormatOptions options() {
    return options(VectorFormat.resolve(format, Path.of(fileName)), null);
  }
}

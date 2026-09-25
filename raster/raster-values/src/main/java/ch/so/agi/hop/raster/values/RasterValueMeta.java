package ch.so.agi.hop.raster.values;

import ch.so.agi.hop.raster.type.ValueMetaRaster;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;

public abstract class RasterValueMeta
    extends BaseTransformMeta<RasterValueTransform, RasterValueData> {
  public abstract String operation();

  @HopMetadataProperty private int version = 1;

  public int getVersion() {
    return version;
  }

  public void setVersion(int value) {
    version = value;
  }

  @HopMetadataProperty private String source = "";

  public String getSource() {
    return source;
  }

  public void setSource(String value) {
    source = value;
  }

  @HopMetadataProperty private boolean sourceField = false;

  public boolean isSourceField() {
    return sourceField;
  }

  public void setSourceField(boolean value) {
    sourceField = value;
  }

  @HopMetadataProperty private String rasterField = "raster";

  public String getRasterField() {
    return rasterField;
  }

  public void setRasterField(String value) {
    rasterField = value;
  }

  @HopMetadataProperty private String outputRasterField = "";

  public String getOutputRasterField() {
    return outputRasterField;
  }

  public void setOutputRasterField(String value) {
    outputRasterField = value;
  }

  @HopMetadataProperty private String geometryField = "geometry";

  public String getGeometryField() {
    return geometryField;
  }

  public void setGeometryField(String value) {
    geometryField = value;
  }

  @HopMetadataProperty private String explicitCrs = "";

  public String getExplicitCrs() {
    return explicitCrs;
  }

  public void setExplicitCrs(String value) {
    explicitCrs = value;
  }

  @HopMetadataProperty private String bands = "ALL";

  public String getBands() {
    return bands;
  }

  public void setBands(String value) {
    bands = value;
  }

  @HopMetadataProperty private String noData = "";

  public String getNoData() {
    return noData;
  }

  public void setNoData(String value) {
    noData = value;
  }

  @HopMetadataProperty private String clipMethod = "POLYGON";

  public String getClipMethod() {
    return clipMethod;
  }

  public void setClipMethod(String value) {
    clipMethod = value;
  }

  @HopMetadataProperty private String minX = "";

  public String getMinX() {
    return minX;
  }

  public void setMinX(String value) {
    minX = value;
  }

  @HopMetadataProperty private String minY = "";

  public String getMinY() {
    return minY;
  }

  public void setMinY(String value) {
    minY = value;
  }

  @HopMetadataProperty private String maxX = "";

  public String getMaxX() {
    return maxX;
  }

  public void setMaxX(String value) {
    maxX = value;
  }

  @HopMetadataProperty private String maxY = "";

  public String getMaxY() {
    return maxY;
  }

  public void setMaxY(String value) {
    maxY = value;
  }

  @HopMetadataProperty private boolean bboxFields = false;

  public boolean isBboxFields() {
    return bboxFields;
  }

  public void setBboxFields(boolean value) {
    bboxFields = value;
  }

  @HopMetadataProperty private String targetCrs = "";

  public String getTargetCrs() {
    return targetCrs;
  }

  public void setTargetCrs(String value) {
    targetCrs = value;
  }

  @HopMetadataProperty private boolean targetCrsField = false;

  public boolean isTargetCrsField() {
    return targetCrsField;
  }

  public void setTargetCrsField(boolean value) {
    targetCrsField = value;
  }

  @HopMetadataProperty private String resolutionX = "";

  public String getResolutionX() {
    return resolutionX;
  }

  public void setResolutionX(String value) {
    resolutionX = value;
  }

  @HopMetadataProperty private String resolutionY = "";

  public String getResolutionY() {
    return resolutionY;
  }

  public void setResolutionY(String value) {
    resolutionY = value;
  }

  @HopMetadataProperty private boolean resolutionFields = false;

  public boolean isResolutionFields() {
    return resolutionFields;
  }

  public void setResolutionFields(boolean value) {
    resolutionFields = value;
  }

  @HopMetadataProperty private String extentMode = "AUTO";

  public String getExtentMode() {
    return extentMode;
  }

  public void setExtentMode(String value) {
    extentMode = value;
  }

  @HopMetadataProperty private String interpolation = "NEAREST";

  public String getInterpolation() {
    return interpolation;
  }

  public void setInterpolation(String value) {
    interpolation = value;
  }

  @HopMetadataProperty private String outputType = "AUTO";

  public String getOutputType() {
    return outputType;
  }

  public void setOutputType(String value) {
    outputType = value;
  }

  @HopMetadataProperty private String sourceNoData = "";

  public String getSourceNoData() {
    return sourceNoData;
  }

  public void setSourceNoData(String value) {
    sourceNoData = value;
  }

  @HopMetadataProperty private String outputNoData = "";

  public String getOutputNoData() {
    return outputNoData;
  }

  public void setOutputNoData(String value) {
    outputNoData = value;
  }

  @HopMetadataProperty private String output = "";

  public String getOutput() {
    return output;
  }

  public void setOutput(String value) {
    output = value;
  }

  @HopMetadataProperty private boolean outputField = false;

  public boolean isOutputField() {
    return outputField;
  }

  public void setOutputField(boolean value) {
    outputField = value;
  }

  @HopMetadataProperty private String compression = "Deflate";

  public String getCompression() {
    return compression;
  }

  public void setCompression(String value) {
    compression = value;
  }

  @HopMetadataProperty private String format = "GEOTIFF";

  public String getFormat() {
    return format;
  }

  public void setFormat(String value) {
    format = value;
  }

  @HopMetadataProperty private String overviews = "AUTO";

  public String getOverviews() {
    return overviews;
  }

  public void setOverviews(String value) {
    overviews = value;
  }

  @HopMetadataProperty private String overviewResampling = "AVERAGE";

  public String getOverviewResampling() {
    return overviewResampling;
  }

  public void setOverviewResampling(String value) {
    overviewResampling = value;
  }

  @HopMetadataProperty private int jpegQuality = 75;

  public int getJpegQuality() {
    return jpegQuality;
  }

  public void setJpegQuality(int value) {
    jpegQuality = value;
  }

  @HopMetadataProperty private boolean overwrite = false;

  public boolean isOverwrite() {
    return overwrite;
  }

  public void setOverwrite(boolean value) {
    overwrite = value;
  }

  @HopMetadataProperty private String prefix = "raster_";

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String value) {
    prefix = value;
  }

  @HopMetadataProperty private String statistics = "mean min max";

  public String getStatistics() {
    return statistics;
  }

  public void setStatistics(String value) {
    statistics = value;
  }

  @HopMetadataProperty private int band = 1;

  public int getBand() {
    return band;
  }

  public void setBand(int value) {
    band = value;
  }

  @HopMetadataProperty private String infoFields = "width height crs bands";

  public String getInfoFields() {
    return infoFields;
  }

  public void setInfoFields(String value) {
    infoFields = value;
  }

  @Override
  public void setDefault() {}

  @Override
  public String getDialogClassName() {
    return RasterValueDialog.class.getName();
  }

  @Override
  public ITransform createTransform(
      TransformMeta t, ITransformData d, int copy, PipelineMeta pm, Pipeline p) {
    return new RasterValueTransform(t, this, (RasterValueData) d, copy, pm, p);
  }

  @Override
  public ITransformData createTransformData() {
    return new RasterValueData();
  }

  @Override
  public boolean supportsErrorHandling() {
    return true;
  }

  public void validateSettings() {
    validateSettings(null);
  }

  /**
   * Validates the settings with Hop variables resolved, because pipeline parameters may supply
   * the format, overview or compression values (for example {@code ${OVERVIEWS}}).
   */
  public void validateSettings(IVariables vars) {
    String formatValue = resolve(vars, format);
    String overviewsValue = resolve(vars, overviews);
    String overviewResamplingValue = resolve(vars, overviewResampling);
    String clipMethodValue = resolve(vars, clipMethod);
    String extentModeValue = resolve(vars, extentMode);
    String resolutionXValue = resolve(vars, resolutionX);
    String resolutionYValue = resolve(vars, resolutionY);
    if (version != 1)
      throw new IllegalArgumentException("Unsupported raster value version: " + version);
    if (rasterField == null || rasterField.isBlank())
      throw new IllegalArgumentException("Raster field is required");
    if (operation().equals("READER") && (source == null || source.isBlank()))
      throw new IllegalArgumentException("Raster source is required");
    if (operation().equals("WRITER") && (output == null || output.isBlank()))
      throw new IllegalArgumentException("Output GeoTIFF is required");
    if (operation().equals("WRITER") && !List.of("GEOTIFF", "COG").contains(formatValue))
      throw new IllegalArgumentException("Output format must be GEOTIFF or COG");
    if (operation().equals("WRITER") && !List.of("AUTO", "NONE").contains(overviewsValue))
      throw new IllegalArgumentException("Overview mode must be AUTO or NONE");
    if (operation().equals("WRITER")
        && !List.of("AVERAGE", "NEAREST").contains(overviewResamplingValue))
      throw new IllegalArgumentException("Overview resampling must be AVERAGE or NEAREST");
    if (operation().equals("WRITER") && (jpegQuality < 1 || jpegQuality > 100))
      throw new IllegalArgumentException("JPEG quality must be between 1 and 100");
    if (operation().equals("CLIP") && !List.of("POLYGON", "BOUNDING_BOX").contains(clipMethodValue))
      throw new IllegalArgumentException("Invalid clip method");
    if (operation().equals("REPROJECT")
        && (resolutionXValue.isBlank()
            || resolutionYValue.isBlank()
            || !List.of("AUTO", "BOUNDING_BOX").contains(extentModeValue)))
      throw new IllegalArgumentException("Target pixel sizes and extent mode are required");
    if (band < 1) throw new IllegalArgumentException("Band numbers start at 1");
  }

  private static String resolve(IVariables vars, String value) {
    String resolved = vars == null || value == null ? value : vars.resolve(value);
    return resolved == null ? "" : resolved.trim();
  }

  public String resultField(IVariables vars) {
    return vars.resolve(
        outputRasterField == null || outputRasterField.isBlank() ? rasterField : outputRasterField);
  }

  static List<String> tokens(String text) {
    return Arrays.stream(text.trim().split("[,; ]+")).filter(s -> !s.isBlank()).distinct().toList();
  }

  public List<String> selectedStats() {
    var result = new ArrayList<>(tokens(statistics));
    for (String s : result)
      if (!List.of("mean", "min", "max", "sum", "stddev", "count").contains(s))
        throw new IllegalArgumentException("Unsupported statistic: " + s);
    if (!result.contains("count")) result.add("count");
    return result;
  }

  @Override
  public void getFields(
      IRowMeta row,
      String origin,
      IRowMeta[] info,
      TransformMeta next,
      IVariables vars,
      IHopMetadataProvider provider) {
    validateSettings(vars);
    String op = operation(), input = vars.resolve(rasterField), p = vars.resolve(prefix);
    if (!op.equals("READER")) {
      int i = row.indexOfValue(input);
      if (i < 0 || row.getValueMeta(i).getType() != ValueMetaRaster.TYPE_RASTER)
        throw new IllegalArgumentException("Expected Raster field: " + input);
    }
    if (op.equals("READER")) add(row, new ValueMetaRaster(input));
    if (op.equals("CLIP") || op.equals("REPROJECT")) {
      String name = resultField(vars);
      if (!name.equals(input)) add(row, new ValueMetaRaster(name));
    }
    if (op.equals("WRITER")) {
      add(row, new ValueMetaString(p + "output_file"));
      add(row, new ValueMetaString(p + "status"));
    }
    if (op.equals("STATS")) {
      for (String s : selectedStats())
        add(row, s.equals("count") ? new ValueMetaInteger(p + s) : new ValueMetaNumber(p + s));
      add(row, new ValueMetaString(p + "status"));
    }
    if (op.equals("INFO"))
      for (String s : tokens(infoFields)) {
        if (!List.of("width", "height", "crs", "bands").contains(s))
          throw new IllegalArgumentException("Unknown raster information: " + s);
        add(
            row,
            s.equals("width") || s.equals("height")
                ? new ValueMetaInteger(p + s)
                : new ValueMetaString(p + s));
      }
  }

  private static void add(IRowMeta row, IValueMeta field) {
    if (row.indexOfValue(field.getName()) >= 0)
      throw new IllegalArgumentException("Output field already exists: " + field.getName());
    row.addValueMeta(field);
  }
}

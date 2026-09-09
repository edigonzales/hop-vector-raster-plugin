package ch.so.agi.hop.raster.stats;

import ch.so.agi.hop.raster.core.RasterRowSupport;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "SOGIS_RASTER_ZONAL_STATS",
    name = "Raster Zonal Statistics (GeoTools)",
    description = "Raster Zonal Statistics (GeoTools)",
    image = "ch/so/agi/hop/raster/stats/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public class RasterZonalStatsMeta
    extends BaseTransformMeta<RasterZonalStatsTransform, RasterZonalStatsData> {
  public RasterZonalStatsMeta() {
    setDefault();
  }

  @HopMetadataProperty private String source;
  @HopMetadataProperty private boolean sourceField;
  @HopMetadataProperty private String geometryField;
  @HopMetadataProperty private String explicitCrs;
  @HopMetadataProperty private int band;
  @HopMetadataProperty private String noData;
  @HopMetadataProperty private String prefix;
  @HopMetadataProperty private String statistics;

  @Override
  public void setDefault() {
    source = "";
    sourceField = false;
    geometryField = "geometry";
    explicitCrs = "";
    band = 1;
    noData = "";
    prefix = "raster_";
    statistics = "mean min max";
  }

  public void validateSettings() {
    if (source == null || source.isBlank())
      throw new IllegalArgumentException("Input raster is required");
    if (band < 1) throw new IllegalArgumentException("Band must be >= 1");
    if (prefix == null || prefix.isBlank())
      throw new IllegalArgumentException("Output prefix is required");
    if (geometryField == null || geometryField.isBlank())
      throw new IllegalArgumentException("Geometry field is required");
    RasterRowSupport.statistics(statistics);
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
    for (String statistic : RasterRowSupport.statistics(statistics))
      addField(
          rowMeta,
          statistic.equals("count")
              ? new ValueMetaInteger(p + statistic)
              : new ValueMetaNumber(p + statistic));
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

  public String getStatistics() {
    return statistics;
  }

  public void setStatistics(String value) {
    statistics = value == null ? "" : value;
  }
}

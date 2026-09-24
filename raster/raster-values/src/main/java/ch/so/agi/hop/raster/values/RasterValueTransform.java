package ch.so.agi.hop.raster.values;

import ch.so.agi.hop.raster.*;
import ch.so.agi.hop.raster.geotools.*;
import java.nio.file.Path;
import java.util.*;
import org.apache.hop.core.exception.*;
import org.apache.hop.core.row.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;
import org.locationtech.jts.geom.*;

public final class RasterValueTransform extends BaseTransform<RasterValueMeta, RasterValueData> {
  public RasterValueTransform(
      TransformMeta t, RasterValueMeta m, RasterValueData d, int c, PipelineMeta pm, Pipeline p) {
    super(t, m, d, c, pm, p);
  }

  @Override
  public boolean processRow() throws HopException {
    boolean standalone =
        meta.operation().equals("READER")
            && getPipelineMeta().findPreviousTransforms(getTransformMeta()).isEmpty();
    if (isStopped()) {
      data.backend.close();
      return false;
    }
    Object[] row = standalone ? (data.emitted ? null : new Object[0]) : getRow();
    if (row == null) {
      data.backend.close();
      setOutputDone();
      return false;
    }
    data.emitted = true;
    if (data.outputMeta == null) {
      data.inputMeta = standalone ? new RowMeta() : getInputRowMeta();
      data.outputMeta = data.inputMeta.clone();
      try {
        meta.getFields(data.outputMeta, getTransformName(), null, null, this, null);
      } catch (Exception e) {
        throw new HopTransformException("Invalid raster settings", e);
      }
    }
    RasterDataset raster = null;
    try {
      Object[] result = RowDataUtil.resizeArray(row, data.outputMeta.size());
      String op = meta.operation();
      if (!op.equals("READER")) {
        int i = data.inputMeta.indexOfValue(resolve(meta.getRasterField()));
        if (i < 0 || !(row[i] instanceof RasterDataset))
          throw new IllegalArgumentException(
              "Missing or null Raster value: " + meta.getRasterField());
        raster = (RasterDataset) row[i];
      }
      switch (op) {
        case "READER" ->
            result[index(meta.getRasterField())] =
                data.backend.describe(value(meta.getSource(), meta.isSourceField(), row));
        case "CLIP" -> {
          try (var planning = new DescriptorSource(raster.result())) {
            Geometry region = geometry(row, planning, true);
            if (region == null || region.isEmpty())
              throw new IllegalArgumentException("Clip geometry is empty");
            List<Integer> selected = new ArrayList<>();
            String bands = resolve(meta.getBands());
            if (bands.equalsIgnoreCase("ALL")) {
              for (int b = 0; b < raster.result().bands().size(); b++) selected.add(b);
            } else
              for (String s : RasterValueMeta.tokens(bands)) selected.add(Integer.parseInt(s) - 1);
            var operation =
                new RasterOperation.Clip(
                    getTransformName(),
                    region.toText(),
                    meta.getClipMethod().equals("POLYGON"),
                    selected,
                    noData(meta.getNoData()));
            result[data.outputMeta.indexOfValue(meta.resultField(this))] =
                data.backend.derive(raster, operation);
          }
        }
        case "REPROJECT" -> {
          List<Double> extent =
              meta.getExtentMode().equals("BOUNDING_BOX")
                  ? List.of(
                      number(meta.getMinX(), meta.isBboxFields(), row),
                      number(meta.getMinY(), meta.isBboxFields(), row),
                      number(meta.getMaxX(), meta.isBboxFields(), row),
                      number(meta.getMaxY(), meta.isBboxFields(), row))
                  : List.of();
          var operation =
              new RasterOperation.Reproject(
                  getTransformName(),
                  value(meta.getTargetCrs(), meta.isTargetCrsField(), row),
                  number(meta.getResolutionX(), meta.isResolutionFields(), row),
                  number(meta.getResolutionY(), meta.isResolutionFields(), row),
                  extent,
                  meta.getInterpolation(),
                  meta.getOutputType(),
                  noData(meta.getSourceNoData()),
                  noData(meta.getOutputNoData()));
          result[data.outputMeta.indexOfValue(meta.resultField(this))] =
              data.backend.derive(raster, operation);
        }
        case "WRITER" -> {
          Path output =
              Path.of(value(meta.getOutput(), meta.isOutputField(), row))
                  .toAbsolutePath()
                  .normalize();
          var progress = new RasterWriteProgress(message -> logBasic(message));
          progress.started(output, meta.getCompression());
          data.backend.write(
              raster,
              output,
              meta.isOverwrite(),
              this::isStopped,
              meta.getCompression(),
              progress::report);
          progress.completed();
          result[index(meta.getPrefix() + "output_file")] = output.toString();
          result[index(meta.getPrefix() + "status")] = "OK";
        }
        case "STATS" -> {
          try (var session = data.backend.session(raster, this::isStopped)) {
            Geometry zone = geometry(row, session.source(), false);
            var stats =
                ZonalStatistics.compute(
                    session.source(),
                    zone,
                    meta.getBand() - 1,
                    noData(meta.getNoData()),
                    this::isStopped);
            for (String name : meta.selectedStats())
              result[index(meta.getPrefix() + name)] = stats.value(name);
            result[index(meta.getPrefix() + "status")] = stats.status();
          }
        }
        case "INFO" -> {
          var d = raster.result();
          for (String name : RasterValueMeta.tokens(meta.getInfoFields()))
            result[index(meta.getPrefix() + name)] =
                switch (name) {
                  case "width" -> (long) d.grid().width();
                  case "height" -> (long) d.grid().height();
                  case "crs" -> d.crsWkt();
                  case "bands" -> bandsJson(d);
                  default -> throw new IllegalArgumentException("Unknown info field");
                };
        }
        default ->
            throw new IllegalArgumentException(
                "Legacy raster pipeline: migrate to Raster Reader → operation → Raster Writer");
      }
      putRow(data.outputMeta, result);
    } catch (Exception e) {
      data.backend.close();
      if (isStopped()) return false;
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      if (raster != null) {
        message =
            "Raster source "
                + raster.source().location()
                + "; consumer "
                + getTransformName()
                + "; operations "
                + raster.steps().stream().map(step -> step.operation().origin()).toList()
                + ": "
                + message;
      }
      var error = getTransformMeta().getTransformErrorMeta();
      if (getTransformMeta().isDoingErrorHandling() || (error != null && error.isEnabled()))
        putError(
            data.inputMeta,
            row,
            1L,
            message,
            meta.getRasterField(),
            "RASTER_" + meta.operation() + "_ERROR");
      else throw new HopTransformException(message, e);
    }
    return true;
  }

  private Geometry geometry(Object[] row, RasterSource source, boolean clip) throws Exception {
    Geometry geom;
    if (clip && meta.getClipMethod().equals("BOUNDING_BOX")) {
      double minX = number(meta.getMinX(), meta.isBboxFields(), row),
          minY = number(meta.getMinY(), meta.isBboxFields(), row),
          maxX = number(meta.getMaxX(), meta.isBboxFields(), row),
          maxY = number(meta.getMaxY(), meta.isBboxFields(), row);
      if (minX >= maxX || minY >= maxY)
        throw new IllegalArgumentException("Bounding box minimum must be below maximum");
      geom = new GeometryFactory().toGeometry(new Envelope(minX, maxX, minY, maxY));
      geom =
          org.locationtech.jts.densify.Densifier.densify(
              geom, Math.max(maxX - minX, maxY - minY) / 100);
    } else {
      int i = data.inputMeta.indexOfValue(resolve(meta.getGeometryField()));
      if (i < 0) throw new IllegalArgumentException("Geometry field missing");
      if (row[i] == null) return null;
      if (!(row[i] instanceof Geometry))
        throw new IllegalArgumentException("Expected Geometry value");
      geom = (Geometry) row[i];
      PixelMask.validate(geom);
      if (geom.isEmpty()) return geom.copy();
    }
    return CrsSupport.inRasterCrs(geom, resolve(meta.getExplicitCrs()), source.crs());
  }

  private String value(String text, boolean field, Object[] row) throws Exception {
    String resolved = resolve(text == null ? "" : text).trim();
    if (!field) return resolved;
    int i = data.inputMeta.indexOfValue(resolved);
    if (i < 0) throw new IllegalArgumentException("Input field missing: " + resolved);
    String s = data.inputMeta.getString(row, i);
    if (s == null || s.isBlank())
      throw new IllegalArgumentException("Input field empty: " + resolved);
    return s.trim();
  }

  private double number(String text, boolean field, Object[] row) throws Exception {
    double n = Double.parseDouble(value(text, field, row));
    if (!Double.isFinite(n))
      throw new IllegalArgumentException("Expected finite coordinate/resolution");
    return n;
  }

  private Double noData(String text) {
    String s = resolve(text == null ? "" : text).trim();
    if (s.isEmpty()) return null;
    double d = Double.parseDouble(s);
    if (Double.isInfinite(d)) throw new IllegalArgumentException("Infinite NoData");
    return d;
  }

  private int index(String name) {
    return data.outputMeta.indexOfValue(resolve(name));
  }

  private static String bandsJson(RasterDescriptor d) throws Exception {
    var bands = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < d.bands().size(); i++) {
      var b = d.bands().get(i);
      var item = new LinkedHashMap<String, Object>();
      item.put("band", i + 1);
      item.put("name", b.name());
      item.put("dataType", b.dataType());
      item.put(
          "noData",
          b.noData() == null
              ? null
              : Double.isFinite(b.noData()) ? b.noData() : b.noData().toString());
      item.put("scale", b.scale());
      item.put("offset", b.offset());
      item.put("color", d.color());
      item.put("alpha", d.alphaBand() == i);
      bands.add(item);
    }
    return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(bands);
  }

  @Override
  public void dispose() {
    data.backend.close();
    super.dispose();
  }
}

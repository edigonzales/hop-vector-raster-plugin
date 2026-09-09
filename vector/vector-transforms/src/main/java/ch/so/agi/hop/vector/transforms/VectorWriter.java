package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.core.*;
import java.nio.file.Path;
import org.apache.hop.core.exception.*;
import org.locationtech.jts.geom.Geometry;

public class VectorWriter
    extends org.apache.hop.pipeline.transform.BaseTransform<VectorWriterMeta, VectorWriterData> {
  public VectorWriter(
      org.apache.hop.pipeline.transform.TransformMeta t,
      VectorWriterMeta m,
      VectorWriterData d,
      int copy,
      org.apache.hop.pipeline.PipelineMeta pm,
      org.apache.hop.pipeline.Pipeline p) {
    super(t, m, d, copy, pm, p);
  }

  private final VectorDiagnostics diagnostics = new VectorDiagnostics(this::logBasic);

  public boolean processRow() throws HopException {
    Object[] row = getRow();
    try {
      if (isStopped()) {
        closeSink();
        return false;
      }
      meta.validateSettings();
      Path file = Path.of(resolve(meta.getFileName()));
      VectorFormat format = VectorFormat.resolve(meta.getFormat(), file);
      if (getTransformMeta().getCopies(this) > 1)
        throw new IllegalArgumentException(
            "Vector Writer requires one transform copy per output file");
      if (data.sink == null) {
        var rm = getInputRowMeta();
        if (rm == null) rm = getPipelineMeta().getPrevTransformFields(this, getTransformMeta());
        boolean explicit =
            !meta.getLayerGeometryType().equals("AUTO") && !meta.getLayerGeometryType().isBlank();
        if (rm == null && format.requiresGeometrySample())
          throw new IllegalArgumentException("Upstream attribute schema unavailable");
        int gi = rm == null ? -1 : rm.indexOfValue(resolve(meta.getGeometryField()));
        if ((row != null || format.requiresGeometrySample()) && gi < 0)
          throw new IllegalArgumentException("Geometry field not found");
        Object value = row == null ? null : row[gi];
        if (value != null && !(value instanceof Geometry))
          throw new IllegalArgumentException("Expected Hop Geometry value");
        if (format.requiresGeometrySample()
            && !explicit
            && (value == null || ((Geometry) value).isEmpty())) {
          if (row != null) {
            if (data.pending.size() >= 10000)
              throw new IllegalArgumentException(
                  "More than 10000 initial NULL/EMPTY geometries; configure explicit geometry type"
                      + " and dimension");
            data.pending.add(row.clone());
            return true;
          }
          if (!data.pending.isEmpty())
            throw new IllegalArgumentException(
                "Cannot infer geometry type: all input geometries are null");
          setOutputDone();
          return false;
        }
        String layer = meta.getLayerName() == null ? "" : resolve(meta.getLayerName());
        if (layer.isBlank()) layer = defaultLayerName(file);
        var options = meta.options(format, this);
        GeometrySchema geometry = null;
        if (format.requiresGeometrySample()) {
          var resolver = new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver();
          if (explicit) {
            if (meta.getLayerDimension().equals("AUTO"))
              throw new IllegalArgumentException(
                  "Explicit geometry type requires explicit dimension");
            geometry =
                GeometrySchema.explicit(
                    meta.getLayerGeometryType(),
                    meta.getLayerDimension(),
                    meta.getCrsOverride().isBlank() && value != null
                        ? resolver.resolve(((Geometry) value).getSRID())
                        : resolver.parse(resolve(meta.getCrsOverride())));
          } else {
            geometry = GeometrySchema.infer((Geometry) value);
            var definition =
                meta.getCrsOverride().isBlank()
                    ? resolver.resolve(geometry.srid())
                    : resolver.parse(resolve(meta.getCrsOverride()));
            geometry =
                meta.getLayerDimension().equals("AUTO")
                    ? new GeometrySchema(geometry.type(), geometry.z(), geometry.m(), definition)
                    : GeometrySchema.explicit(
                        geometry.type(), meta.getLayerDimension(), definition);
          }
        }
        data.sink =
            VectorProviders.get(format)
                .create(
                    new WriteRequest(
                        file, layer, rm, gi, (Geometry) value, geometry, options, diagnostics));
        for (Object[] pending : data.pending) {
          diagnostics.nextRow();
          if (data.sink.write(pending)) incrementLinesOutput();
        }
        data.pending.clear();
      }
      if (row == null) {
        data.sink.finish();
        if (data.skipped > 0) logBasic("Skipped " + data.skipped + " null/empty geometries");
        closeSink();
        setOutputDone();
        return false;
      }
      diagnostics.nextRow();
      if (data.sink.write(row)) incrementLinesOutput();
      else data.skipped++;
      return true;
    } catch (Exception e) {
      closeSink();
      throw new HopTransformException("Vector export failed: " + e.getMessage(), e);
    }
  }

  private void closeSink() {
    if (data.sink != null) {
      try {
        data.sink.close();
      } catch (Exception e) {
        logError("Unable to close vector output", e);
      }
      data.sink = null;
      diagnostics.finish();
    }
    data.pending.clear();
  }

  @Override
  public void dispose() {
    closeSink();
    super.dispose();
  }

  static String defaultLayerName(Path file) {
    String n = file.getFileName().toString();
    int dot = n.lastIndexOf('.');
    return dot > 0 ? n.substring(0, dot) : n;
  }
}

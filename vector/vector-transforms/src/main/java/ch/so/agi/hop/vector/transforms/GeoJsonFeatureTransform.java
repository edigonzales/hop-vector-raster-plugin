package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.core.geojson.*;
import com.atolcd.hop.gis.geometry.codec.GeometryCodecException;
import org.apache.hop.core.exception.*;
import org.apache.hop.core.row.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;

/** Row execution shared by the Feature encoder and decoder. */
public abstract class GeoJsonFeatureTransform<
        M extends GeoJsonFeatureMeta<?, ?>, D extends GeoJsonFeatureData>
    extends BaseTransform<M, D> {
  protected GeoJsonFeatureTransform(
      TransformMeta transform,
      M meta,
      D data,
      int copy,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transform, meta, data, copy, pipelineMeta, pipeline);
  }

  @Override
  public boolean init() {
    if (!super.init()) return false;
    try {
      data.options = meta.options(this);
      data.errorMode = meta.errorMode();
      data.codec = new GeoJsonFeatureCodec();
      if (data.errorMode == GeoJsonFeatureMeta.OnDataError.ROUTE_TO_ERROR
          && !getTransformMeta().isDoingErrorHandling())
        throw new IllegalArgumentException("ROUTE_TO_ERROR requires an active error hop");
      return true;
    } catch (Exception | LinkageError e) {
      logError("Invalid GeoJSON Feature configuration or shared Geometry runtime", e);
      return false;
    }
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      setOutputDone();
      return false;
    }
    if (first) {
      first = false;
      configure();
    }
    Object[] out = java.util.Arrays.copyOf(row, data.outputMeta.size());
    try {
      if (meta.decoder()) {
        Object value = row[data.inputIndex];
        if (value != null) {
          Object[] feature =
              GeoJsonFeatureRows.decodeRow(
                  data.codec.decode(
                      getInputRowMeta().getValueMeta(data.inputIndex).getString(value),
                      data.options,
                      null),
                  data.options);
          System.arraycopy(feature, 0, out, data.inputSize, feature.length);
        }
      } else
        out[data.inputSize] =
            data.codec.encode(
                GeoJsonFeatureRows.encodeRow(getInputRowMeta(), row, data.options), data.options);
    } catch (GeometryCodecException | IllegalArgumentException e) {
      switch (data.errorMode) {
        case FAIL ->
            throw new HopTransformException("GeoJSON Feature data error: " + e.getMessage(), e);
        case RETURN_NULL -> {
          java.util.Arrays.fill(out, data.inputSize, out.length, null);
        }
        case ROUTE_TO_ERROR -> {
          putError(
              getInputRowMeta(),
              row,
              1,
              e.getMessage(),
              meta.decoder()
                  ? GeoJsonFeatureMeta.resolve(this, meta.getInputField())
                  : e instanceof GeoJsonFieldException field
                      ? field.field()
                      : data.options.geometryField(),
              "GEOJSON_DATA_ERROR");
          return true;
        }
      }
    } catch (LinkageError e) {
      throw new HopTransformException(
          "GeoJSON requires the matching Geometry Type runtime; upgrade the shared Geometry plugin",
          e);
    }
    putRow(data.outputMeta, out);
    return true;
  }

  private void configure() throws HopTransformException {
    try {
      data.options = meta.options(this);
      data.errorMode = meta.errorMode();
      data.codec = new GeoJsonFeatureCodec();
      if (data.errorMode == GeoJsonFeatureMeta.OnDataError.ROUTE_TO_ERROR
          && !getTransformMeta().isDoingErrorHandling())
        throw new IllegalArgumentException("ROUTE_TO_ERROR requires an active error hop");
      data.inputSize = getInputRowMeta().size();
      data.outputMeta = getInputRowMeta().clone();
      if (meta.decoder()) {
        String input = GeoJsonFeatureMeta.resolve(this, meta.getInputField());
        data.inputIndex = getInputRowMeta().indexOfValue(input);
        if (data.inputIndex < 0
            || getInputRowMeta().getValueMeta(data.inputIndex).getType() != IValueMeta.TYPE_STRING)
          throw new IllegalArgumentException(
              "Feature input must be an existing String field: " + input);
      } else {
        GeoJsonFeatureRows.validateEncodingSchema(getInputRowMeta(), data.options);
      }
      meta.getFields(data.outputMeta, getTransformName(), null, null, this, null);
    } catch (Exception | LinkageError e) {
      throw new HopTransformException("Invalid GeoJSON Feature configuration or shared runtime", e);
    }
  }
}

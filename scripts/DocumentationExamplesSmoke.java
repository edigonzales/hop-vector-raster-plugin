import ch.so.agi.hop.vector.core.*;
import ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.RowAdapter;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.gce.geotiff.*;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;

/** Runs the actual example files; no transform configuration is reconstructed by the test. */
public class DocumentationExamplesSmoke {
  static final List<Object[]> output = Collections.synchronizedList(new ArrayList<>());
  static IRowMeta fields;

  static void run(Path hpl, String last, Map<String, String> values) throws Exception {
    output.clear();
    var variables = new Variables();
    values.forEach(variables::setVariable);
    var metadata = new MemoryMetadataProvider();
    var meta = new PipelineMeta(hpl.toString(), metadata, variables);
    var pipeline = new LocalPipelineEngine(meta);
    pipeline.setMetadataProvider(metadata);
    pipeline.initializeFrom(variables);
    pipeline.copyParametersFromDefinitions(meta);
    for (var parameter : meta.listParameters())
      pipeline.setParameterValue(parameter, values.getOrDefault(parameter, meta.getParameterDefault(parameter)));
    pipeline.activateParameters(pipeline);
    pipeline.prepareExecution();
    pipeline.getTransform(last, 0).addRowListener(new RowAdapter() {
      @Override public void rowWrittenEvent(IRowMeta rowMeta, Object[] row) {
        fields = rowMeta;
        output.add(Arrays.copyOf(row, rowMeta.size()));
      }
    });
    pipeline.startThreads();
    pipeline.waitUntilFinished();
    if (pipeline.getErrors() != 0 || output.size() != 1)
      throw new AssertionError(hpl + ": errors=" + pipeline.getErrors() + ", rows=" + output.size());
    if (!"OK".equals(output.get(0)[fields.indexOfValue("raster_status")]))
      throw new AssertionError("Unexpected status");
  }

  public static void main(String[] args) throws Exception {
    HopEnvironment.init();
    Path root = Path.of(args[0]), temp = Path.of(args[1]);
    Path raster = temp.resolve("input.tif"), clipped = temp.resolve("clip.tif");
    var image = new BufferedImage(4, 3, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < 3; y++)
      for (int x = 0; x < 4; x++) image.getRaster().setSample(x, y, 0, x + y * 4);
    var coverage = new GridCoverageFactory().create("fixture", image,
        new ReferencedEnvelope(2600000, 2600004, 1200000, 1200003, CRS.decode("EPSG:2056", true)));
    var writer = new GeoTiffWriter(raster.toFile());
    try { writer.write(coverage); } finally { writer.dispose(); coverage.dispose(true); }
    run(root.resolve("examples/raster-clip/clip-cog.hpl"), "Clip raster", Map.of(
        "INPUT_RASTER", raster.toString(), "OUTPUT_FILE", clipped.toString(),
        "BBOX_CRS", "EPSG:2056", "MIN_X", "2600001", "MIN_Y", "1200001",
        "MAX_X", "2600003", "MAX_Y", "1200003", "NODATA", "255"));
    var reader = new GeoTiffReader(clipped.toFile());
    var result = reader.read();
    try {
      var pixels = result.getRenderedImage().getData();
      if (pixels.getWidth() != 2 || pixels.getHeight() != 2
          || pixels.getSampleDouble(0, 0, 0) != 1 || pixels.getSampleDouble(1, 1, 0) != 6)
        throw new AssertionError("Clip pixels differ from documented source-grid semantics");
      if (result.getEnvelope2D().getMinX() != 2600001 || result.getEnvelope2D().getMaxY() != 1200003)
        throw new AssertionError("Clip georeferencing mismatch");
    } finally { result.dispose(true); reader.dispose(); }
    System.out.println("Actual clip HPL: 2x2 source-grid pixels and georeferencing OK");
    var geometry = new GeometryFactory(new PrecisionModel(), 2056)
        .toGeometry(new Envelope(2600000, 2600004, 1200000, 1200003));
    var rm = new RowMeta();
    rm.addValueMeta(new ValueMetaString("name"));
    rm.addValueMeta(new ValueMetaGeometry("geometry"));
    Path vector = temp.resolve("zones.gpkg");
    var provider = new GeoPackageProvider(new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
    try (var sink = provider.create(new WriteRequest(vector, "zones", rm, 1, geometry,
        GeometrySchema.infer(geometry), null, Diagnostics.NONE))) {
      sink.write(new Object[] {"whole raster", geometry}); sink.finish();
    }
    run(root.resolve("examples/raster-stats/zonal-statistics.hpl"), "Zonal statistics", Map.of(
        "INPUT_VECTOR", vector.toString(), "INPUT_LAYER", "zones", "INPUT_RASTER", raster.toString(),
        "GEOMETRY_CRS", "EPSG:2056", "NODATA", "255"));
    Object[] row = output.get(0);
    for (var entry : Map.of("raster_mean", 5.5, "raster_min", 0.0, "raster_max", 11.0, "raster_count", 12.0).entrySet())
      if (((Number) row[fields.indexOfValue(entry.getKey())]).doubleValue() != entry.getValue())
        throw new AssertionError("Wrong " + entry.getKey());
    if (!"whole raster".equals(row[fields.indexOfValue("name")])) throw new AssertionError("Lost input attribute");
    System.out.println("Actual statistics HPL: mean=5.5, min=0, max=11, count=12; zero and attributes preserved");
  }
}

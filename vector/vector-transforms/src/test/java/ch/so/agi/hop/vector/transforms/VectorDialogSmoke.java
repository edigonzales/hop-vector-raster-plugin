package ch.so.agi.hop.vector.transforms;

import java.lang.reflect.Field;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

public class VectorDialogSmoke {
  static Object field(Object o, String name) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  private static void whenOpen(Display display, Shell parent, Runnable checks) {
    display.timerExec(
        100,
        () -> {
          // GTK can dispatch timers inside Shell.open(). Closing the dialog there
          // would dispose it before Hop starts its normal modal event loop.
          boolean opening =
              java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                  .anyMatch(
                      frame ->
                          frame.getClassName().equals(Shell.class.getName())
                              && frame.getMethodName().equals("open"));
          boolean visible =
              java.util.Arrays.stream(parent.getShells())
                  .anyMatch(shell -> !shell.isDisposed() && shell.isVisible());
          if (opening || !visible) {
            whenOpen(display, parent, checks);
          } else {
            checks.run();
          }
        });
  }

  public static void main(String[] args) throws Exception {
    HopEnvironment.init();
    Display display = new Display();
    Shell parent = new Shell(display);
    try {
      VectorWriterMeta meta = new VectorWriterMeta();
      meta.setFileName("/tmp/demo.gpkg");
      PipelineMeta pm = new PipelineMeta();
      pm.addTransform(new TransformMeta("Writer", meta));
      VectorWriterDialog dialog = new VectorWriterDialog(parent, new Variables(), meta, pm);
      whenOpen(
          display,
          parent,
          () -> {
            try {
              Composite group = (Composite) field(dialog, "generateOptions");
              if (group.getVisible())
                throw new AssertionError("Generate options visible for GeoPackage");
              Combo format = (Combo) field(dialog, "wFormat");
              format.setText("ARCINFO_GENERATE");
              format.notifyListeners(SWT.Selection, new Event());
              if (!group.getVisible()) throw new AssertionError("Generate options hidden");
              for (String name :
                  new String[] {
                    "wGeometryType",
                    "wDimension",
                    "wIdField",
                    "wStartId",
                    "wDecimals",
                    "wComma",
                    "wSkipEmpty",
                    "wDiscardExtraOrdinates"
                  }) {
                Control c = (Control) field(dialog, name);
                if (c.getParent() != group || !c.getVisible())
                  throw new AssertionError("Wrong option grouping: " + name);
              }
              format.setText("SHAPEFILE");
              format.notifyListeners(SWT.Selection, new Event());
              if (group.getVisible())
                throw new AssertionError("Generate options visible for Shapefile");
              Composite shape = (Composite) field(dialog, "shapeOptions");
              if (!shape.getVisible()) throw new AssertionError("Shapefile options hidden");
              for (String name : new String[] {"wCharset", "wTimezone", "wFields"})
                if (!((Control) field(dialog, name)).getVisible())
                  throw new AssertionError("Shapefile control hidden: " + name);
              for (String f : new String[] {"FLATGEOBUF", "PARQUET"}) {
                format.setText(f);
                format.notifyListeners(SWT.Selection, new Event());
                String groupName = f.equals("FLATGEOBUF") ? "flatGeobufOptions" : "parquetOptions";
                if (!((Composite) field(dialog, groupName)).getVisible())
                  throw new AssertionError(groupName + " hidden");
                if (shape.getVisible() || group.getVisible())
                  throw new AssertionError("Wrong options visible");
                if (!((Button) field(dialog, "wOverwrite")).getVisible())
                  throw new AssertionError("Overwrite hidden");
              }
              // GeoPackage has its own non-destructive modes and target schema preview.
              java.nio.file.Path gpkg = java.nio.file.Files.createTempFile("hop-dialog-", ".gpkg");
              java.nio.file.Files.delete(gpkg);
              var rows = new org.apache.hop.core.row.RowMeta();
              rows.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("name"));
              rows.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("geom"));
              var point =
                  new org.locationtech.jts.geom.GeometryFactory(
                          new org.locationtech.jts.geom.PrecisionModel(), 2056)
                      .createPoint();
              var provider =
                  new ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider(
                      new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
              try (var sink =
                  provider.create(
                      new ch.so.agi.hop.vector.core.WriteRequest(
                          gpkg, "places", rows, 1, point, null, null, null))) {
                sink.finish();
              }
              format.setText("GEOPACKAGE");
              ((org.apache.hop.ui.core.widget.TextVar) field(dialog, "wFileName"))
                  .setText(gpkg.toString());
              format.notifyListeners(SWT.Selection, new Event());
              if (((Button) field(dialog, "wOverwrite")).getVisible())
                throw new AssertionError("GeoPackage overwrite must be hidden");
              Combo mode = (Combo) field(dialog, "wGeoPackageMode");
              mode.select(2);
              mode.notifyListeners(SWT.Selection, new Event());
              if (((Combo) field(dialog, "wLayerType")).getEnabled())
                throw new AssertionError("Append schema controls must be disabled");
              ((org.apache.hop.ui.core.widget.ComboVar) field(dialog, "wLayer")).setText("places");
              ((Button) field(dialog, "wGeoPackageLoad"))
                  .notifyListeners(SWT.Selection, new Event());
              if (!((Text) field(dialog, "wGeoPackagePreview"))
                  .getText()
                  .contains("Spatial index: true"))
                throw new AssertionError("Missing target preview");
              mode.select(1);
              mode.notifyListeners(SWT.Selection, new Event());
              if (!((Combo) field(dialog, "wLayerType")).getEnabled())
                throw new AssertionError("New layer schema must be editable");
              java.nio.file.Files.delete(gpkg);
              format.setText("PARQUET");
              format.notifyListeners(SWT.Selection, new Event());
              ((Combo) field(dialog, "wParquetType")).setText("GEOGRAPHY");
              ((Combo) field(dialog, "wParquetType")).notifyListeners(SWT.Selection, new Event());
              if (!((Combo) field(dialog, "wParquetAlgorithm")).getEnabled())
                throw new AssertionError("Interpolation disabled");
              ((Combo) field(dialog, "wLayerType")).setText("POINT");
              ((Combo) field(dialog, "wLayerDimension")).setText("XYZM");
              ((org.apache.hop.ui.core.widget.TextVar) field(dialog, "wCharset"))
                  .setText("windows-1252");
              var save = VectorWriterDialog.class.getDeclaredMethod("ok");
              save.setAccessible(true);
              save.invoke(dialog);
              if (!meta.getLayerDimension().equals("XYZM")
                  || !meta.getCharset().equals("windows-1252"))
                throw new AssertionError("Dialog settings not saved");
              System.out.println(
                  "Writer dialog: all five formats, conditional controls and save OK");
            } catch (Throwable e) {
              e.printStackTrace();
              System.exit(1);
            }
          });
      dialog.open();
      VectorReaderMeta reader = new VectorReaderMeta();
      PipelineMeta rpm = new PipelineMeta();
      rpm.addTransform(new TransformMeta("Reader", reader));
      VectorReaderDialog rd = new VectorReaderDialog(parent, new Variables(), reader, rpm);
      whenOpen(
          display,
          parent,
          () -> {
            try {
              Combo formats = (Combo) field(rd, "wFormat");
              if (!java.util.Arrays.equals(
                  formats.getItems(),
                  new String[] {"AUTO", "SHAPEFILE", "GEOPACKAGE", "FILEGEODATABASE"}))
                throw new AssertionError("Wrong reader formats");
              System.out.println("Reader dialog format selection OK");
              rd.dispose();
            } catch (Throwable e) {
              e.printStackTrace();
              System.exit(1);
            }
          });
      rd.open();
    } finally {
      parent.dispose();
      display.dispose();
    }
  }
}

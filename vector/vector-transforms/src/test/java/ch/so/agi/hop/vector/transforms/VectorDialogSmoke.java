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

  private static boolean containsLabel(Control control, String expected) {
    if (control instanceof Label label && expected.equals(label.getText())) return true;
    if (control instanceof Composite composite)
      for (Control child : composite.getChildren()) if (containsLabel(child, expected)) return true;
    return false;
  }

  private static String childSizes(Composite composite) {
    StringBuilder result = new StringBuilder();
    for (Control child : composite.getChildren()) {
      result.append(child.getClass().getSimpleName()).append('=').append(child.getBounds());
      if (child instanceof Composite nested) result.append('[').append(childSizes(nested)).append(']');
      result.append(' ');
    }
    return result.toString();
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
      Variables variables = new Variables();
      variables.setVariable("TARGET_LAYER", "places");
      VectorWriterDialog dialog = new VectorWriterDialog(parent, variables, meta, pm);
      whenOpen(
          display,
          parent,
          () -> {
            try {
              Composite group = (Composite) field(dialog, "generateOptions");
              if (group.getVisible())
                throw new AssertionError("Generate options visible for GeoPackage");
              Object layerControl = field(dialog, "wLayer");
              if (!(layerControl instanceof org.apache.hop.ui.core.widget.TextVar))
                throw new AssertionError("Layer field must be a TextVar");
              org.apache.hop.ui.core.widget.TextVar layer =
                  (org.apache.hop.ui.core.widget.TextVar) layerControl;
              if (!(field(dialog, "wGeometryField")
                  instanceof org.apache.hop.ui.core.widget.ComboVar))
                throw new AssertionError("Geometry field must remain a ComboVar");
              layer.setText("${TARGET_LAYER}");
              if (!layer.getText().equals("${TARGET_LAYER}")
                  || !variables.resolve(layer.getText()).equals("places"))
                throw new AssertionError("Layer variable was not preserved or resolved");
              Combo format = (Combo) field(dialog, "wFormat");
              if (!java.util.Arrays.equals(
                  VectorWriterDialog.fileDialogFilterExtensions("AUTO"),
                  new String[] {"*.*"}))
                throw new AssertionError("AUTO save dialog must preserve the typed suffix");
              if (!java.util.Arrays.equals(
                  VectorWriterDialog.fileDialogFilterExtensions("SHAPEFILE"),
                  new String[] {"*.shp"}))
                throw new AssertionError("Shapefile save dialog filter is wrong");
              var writerFileDialog = new org.eclipse.swt.widgets.FileDialog(parent, SWT.SAVE);
              var initializeFileDialog =
                  VectorWriterDialog.class.getDeclaredMethod(
                      "initializeFileDialog",
                      org.eclipse.swt.widgets.FileDialog.class,
                      String.class);
              initializeFileDialog.setAccessible(true);
              initializeFileDialog.invoke(dialog, writerFileDialog, "/tmp/hop-out/foo.shp");
              if (!"/tmp/hop-out".equals(writerFileDialog.getFilterPath())
                  || !"foo.shp".equals(writerFileDialog.getFileName()))
                throw new AssertionError(
                    "Writer save dialog was not initialized with the current path and name");
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
              layer.setText("places");
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
              java.nio.file.Path fgdb =
                  java.nio.file.Files.createTempDirectory("hop-dialog-").resolve("test.gdb");
              var fgdbProvider =
                  new ch.so.agi.hop.vector.formats.filegeodatabase.FileGeodatabaseProvider(
                      new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
              try (var sink =
                  fgdbProvider.create(
                      new ch.so.agi.hop.vector.core.WriteRequest(
                          fgdb, "places", rows, 1, point, null, null, null))) {
                sink.finish();
              }
              format.setText("FILEGEODATABASE");
              ((org.apache.hop.ui.core.widget.TextVar) field(dialog, "wFileName"))
                  .setText(fgdb.toString());
              format.notifyListeners(SWT.Selection, new Event());
              Combo fgdbMode = (Combo) field(dialog, "wFileGdbWriteMode");
              fgdbMode.select(2);
              fgdbMode.notifyListeners(SWT.Selection, new Event());
              if (((Button) field(dialog, "wOverwrite")).getVisible()
                  || ((Combo) field(dialog, "wLayerType")).getEnabled()
                  || ((org.apache.hop.ui.core.widget.TextVar) field(dialog, "wFileGdbResolution"))
                      .getTextWidget()
                      .getEnabled())
                throw new AssertionError(
                    "FileGDB append controls: format="
                        + format.getText()
                        + ", overwrite="
                        + ((Button) field(dialog, "wOverwrite")).getVisible()
                        + ", type="
                        + ((Combo) field(dialog, "wLayerType")).getEnabled()
                        + ", resolution="
                        + ((org.apache.hop.ui.core.widget.TextVar)
                                field(dialog, "wFileGdbResolution"))
                            .getTextWidget()
                            .getEnabled());
              ((Button) field(dialog, "wFileGdbLoad")).notifyListeners(SWT.Selection, new Event());
              if (!((Text) field(dialog, "wFileGdbPreview"))
                  .getText()
                  .contains("Spatial index: true"))
                throw new AssertionError(
                    "FileGDB target preview missing: "
                        + ((Text) field(dialog, "wFileGdbPreview")).getText());
              fgdbMode.select(1);
              fgdbMode.notifyListeners(SWT.Selection, new Event());
              if (!((Combo) field(dialog, "wLayerType")).getEnabled())
                throw new AssertionError("FileGDB add schema must be editable");
              try (var paths = java.nio.file.Files.walk(fgdb.getParent())) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                  java.nio.file.Files.delete(path);
              }

              format.setText("PARQUET");
              format.notifyListeners(SWT.Selection, new Event());
              ((Combo) field(dialog, "wParquetType")).setText("GEOGRAPHY");
              ((Combo) field(dialog, "wParquetType")).notifyListeners(SWT.Selection, new Event());
              if (!((Combo) field(dialog, "wParquetAlgorithm")).getEnabled())
                throw new AssertionError("Interpolation disabled");
              ((Combo) field(dialog, "wLayerType")).setText("POINT");
              ((Combo) field(dialog, "wLayerDimension")).setText("XYZM");
              org.apache.hop.ui.core.widget.ComboVar geometryField =
                  (org.apache.hop.ui.core.widget.ComboVar) field(dialog, "wGeometryField");
              if (!geometryField.getText().isBlank())
                throw new AssertionError("Geometry field must be empty without input metadata");
              geometryField.setText("geometry");
              ((org.apache.hop.ui.core.widget.TextVar) field(dialog, "wCharset"))
                  .setText("windows-1252");
              var save = VectorWriterDialog.class.getDeclaredMethod("ok");
              save.setAccessible(true);
              save.invoke(dialog);
              if (!meta.getLayerDimension().equals("XYZM")
                  || !meta.getCharset().equals("windows-1252")
                  || !meta.getLayerName().equals("places"))
                throw new AssertionError("Dialog settings not saved");
              System.out.println(
                  "Writer dialog: all five formats, conditional controls and save OK");
            } catch (Throwable e) {
              e.printStackTrace();
              System.exit(1);
            }
          });
      dialog.open();
      FileGdbWriterMeta multi = new FileGdbWriterMeta();
      multi.setExistingDatabase(true);
      multi.setFileName("${UNRESOLVED}/target.gdb");
      var mapping = new FileGdbWriterMeta.Input("buildings", "Source");
      mapping.setAction("APPEND_ROWS");
      mapping.setGeometryField("shape");
      mapping.setSpatialIndex(false);
      multi.setInputs(new java.util.ArrayList<>(java.util.List.of(mapping)));
      PipelineMeta mpm = new PipelineMeta();
      var source = new TransformMeta("Source", new VectorReaderMeta());
      var target = new TransformMeta("Multi", multi);
      mpm.addTransform(source);
      mpm.addTransform(target);
      mpm.addPipelineHop(new org.apache.hop.pipeline.PipelineHopMeta(source, target));
      var md = new FileGdbWriterDialog(parent, new Variables(), multi, mpm);
      whenOpen(
          display,
          parent,
          () -> {
            try {
              Combo modes = (Combo) field(md, "wMode");
              Composite mappings = (Composite) field(md, "mappings");
              Combo action =
                  java.util.Arrays.stream(mappings.getChildren())
                      .filter(c -> c instanceof Combo)
                      .map(c -> (Combo) c)
                      .findFirst()
                      .orElseThrow();
              if (modes.getSelectionIndex() != 1
                  || action.getSelectionIndex() != 1
                  || !action.getEnabled())
                throw new AssertionError("Existing mapping was not loaded");
              modes.select(0);
              modes.notifyListeners(SWT.Selection, new Event());
              if (action.getEnabled() || action.getSelectionIndex() != 0)
                throw new AssertionError("New GDB cannot append");
              modes.select(1);
              modes.notifyListeners(SWT.Selection, new Event());
              action.select(1);
              action.notifyListeners(SWT.Selection, new Event());
              if (!((Text) field(md, "preview")).getText().contains("Variable"))
                throw new AssertionError("Unresolved variable preview");
              for (Control control : mappings.getShell().getChildren())
                if (control instanceof Button button && button.getText().equals("OK")) {
                  button.notifyListeners(SWT.Selection, new Event());
                  break;
                }
              if (!multi.isExistingDatabase()
                  || !multi.getInputs().getFirst().getAction().equals("APPEND_ROWS")
                  || !multi.getInputs().getFirst().getGeometryField().equals("shape")
                  || multi.getInputs().getFirst().isSpatialIndex())
                throw new AssertionError("FileGDB mapping save failed");
              System.out.println("FileGDB Writer dialog modes, mapping, variables and save OK");
            } catch (Throwable e) {
              e.printStackTrace();
              System.exit(1);
            }
          });
      md.open();
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
              VectorReaderDialogComposite content =
                  (VectorReaderDialogComposite) field(rd, "content");
              if (VectorReaderDialog.usesDirectoryBrowser("AUTO")
                  || VectorReaderDialog.usesDirectoryBrowser("SHAPEFILE")
                  || VectorReaderDialog.usesDirectoryBrowser("GEOPACKAGE")
                  || !VectorReaderDialog.usesDirectoryBrowser("FILEGEODATABASE")
                  || !VectorReaderDialog.browseButtonLabel("FILEGEODATABASE")
                      .equals("Browse folder...")
                  || !VectorReaderDialog.browseButtonLabel("AUTO").equals("Browse..."))
                throw new AssertionError("Wrong reader browse mode selection");
              Button browse = (Button) field(rd, "wbFile");
              if (!browse.getText().equals("Browse..."))
                throw new AssertionError("Default browse label is wrong: " + browse.getText());
              formats.setText("FILEGEODATABASE");
              formats.notifyListeners(SWT.Selection, new Event());
              if (!browse.getText().equals("Browse folder..."))
                throw new AssertionError("FileGDB browse label is wrong: " + browse.getText());
              formats.setText("GEOPACKAGE");
              formats.notifyListeners(SWT.Selection, new Event());
              if (!browse.getText().equals("Browse..."))
                throw new AssertionError("File browse label was not restored: " + browse.getText());
              java.nio.file.Path fgdb =
                  java.nio.file.Files.createTempDirectory("hop-reader-dialog-").resolve("sample.gdb");
              var readerRows = new org.apache.hop.core.row.RowMeta();
              readerRows.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("name"));
              readerRows.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("shape"));
              var readerPoint =
                  new org.locationtech.jts.geom.GeometryFactory(
                          new org.locationtech.jts.geom.PrecisionModel(), 2056)
                      .createPoint(new org.locationtech.jts.geom.Coordinate(2600000, 1200000));
              var readerProvider =
                  new ch.so.agi.hop.vector.formats.filegeodatabase.FileGeodatabaseProvider(
                      new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
              try (var sink =
                  readerProvider.create(
                      new ch.so.agi.hop.vector.core.WriteRequest(
                          fgdb, "places", readerRows, 1, readerPoint, null, null, null))) {
                sink.write(new Object[] {"place", readerPoint});
                sink.finish();
              }
              formats.setText("FILEGEODATABASE");
              formats.notifyListeners(SWT.Selection, new Event());
              ((org.apache.hop.ui.core.widget.TextVar) field(content, "fileName"))
                  .setText(fgdb.toString());
              if (!((Text) field(rd, "wAvailableFieldsPreview"))
                  .getText()
                  .contains("Layer: places"))
                throw new AssertionError("FileGDB folder path did not load schema");
              try (var paths = java.nio.file.Files.walk(fgdb.getParent())) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                  java.nio.file.Files.delete(path);
              }
              Text preview = content.getAvailableFieldsPreview();
              int initialHeight = preview.getSize().y;
              if (containsLabel(content, "FileGDB X min (source CRS)"))
                throw new AssertionError("Legacy FileGDB bounds must not be visible");
              var readerShell = content.getShell();
              var readerSize = readerShell.getSize();
              readerShell.setSize(readerSize.x, readerSize.y + 180);
              readerShell.layout(true, true);
              if (preview.getSize().y <= initialHeight)
                throw new AssertionError(
                    "Available fields preview did not grow with the dialog: initial="
                        + initialHeight
                        + ", current="
                        + preview.getSize().y
                        + ", shell="
                        + readerSize.y
                        + " -> "
                        + readerShell.getSize().y
                        + ", content="
                        + content.getSize().y
                        + ", children="
                        + childSizes(content));
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

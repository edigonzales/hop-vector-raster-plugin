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
      display.timerExec(
          600,
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
                    "wOverwrite",
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
                  "Writer dialog format switching, Shapefile/GENERATE controls and save OK");
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
      display.timerExec(
          600,
          () -> {
            try {
              Combo formats = (Combo) field(rd, "wFormat");
              if (!java.util.Arrays.equals(
                  formats.getItems(), new String[] {"AUTO", "SHAPEFILE", "GEOPACKAGE"}))
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

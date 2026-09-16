package ch.so.agi.hop.raster.values;

import java.util.*;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Operation-specific fields, edited on a clone so Cancel cannot change the pipeline. */
public final class RasterValueDialog extends BaseTransformDialog {
  private final RasterValueMeta input;

  public RasterValueDialog(
      Shell parent, IVariables vars, RasterValueMeta meta, PipelineMeta pipeline) {
    super(parent, vars, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("Raster " + input.operation() + " (GeoTools)");
    shell.setLayout(new GridLayout(1, false));
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    var scroll = new ScrolledComposite(shell, SWT.V_SCROLL);
    scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);
    var body = new Composite(scroll, SWT.NONE);
    body.setLayout(new GridLayout(2, false));
    new Label(body, SWT.NONE).setText("Transform name");
    wTransformName = new Text(body, SWT.BORDER);
    wTransformName.setText(transformName);
    wTransformName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    var controls = new LinkedHashMap<String, Control>();
    String fields =
        switch (input.operation()) {
          case "READER" -> "source sourceField rasterField";
          case "CLIP" ->
              "rasterField outputRasterField clipMethod geometryField explicitCrs bands noData minX"
                  + " minY maxX maxY bboxFields";
          case "REPROJECT" ->
              "rasterField outputRasterField targetCrs targetCrsField resolutionX resolutionY"
                  + " resolutionFields extentMode minX minY maxX maxY bboxFields interpolation"
                  + " outputType sourceNoData outputNoData";
          case "WRITER" -> "rasterField output outputField overwrite prefix";
          case "STATS" -> "rasterField geometryField explicitCrs band noData statistics prefix";
          case "INFO" -> "rasterField infoFields prefix";
          default -> "";
        };
    try {
      for (String name : fields.split(" ")) {
        if (name.isEmpty()) continue;
        var field = RasterValueMeta.class.getDeclaredField(name);
        field.setAccessible(true);
        new Label(body, SWT.NONE).setText(label(name));
        Control control;
        if (field.getType() == boolean.class) {
          var button = new Button(body, SWT.CHECK);
          button.setSelection(field.getBoolean(input));
          control = button;
        } else {
          var choices =
              switch (name) {
                case "clipMethod" -> new String[] {"POLYGON", "BOUNDING_BOX"};
                case "extentMode" -> new String[] {"AUTO", "BOUNDING_BOX"};
                case "interpolation" -> new String[] {"NEAREST", "BILINEAR"};
                case "outputType" -> new String[] {"AUTO", "SOURCE", "FLOAT32", "FLOAT64"};
                default -> new String[0];
              };
          if (choices.length > 0 || name.equals("rasterField") || name.equals("geometryField")) {
            var combo = new ComboVar(variables, body, SWT.BORDER);
            if (choices.length == 0)
              try {
                choices =
                    pipelineMeta.getPrevTransformFields(variables, transformName).getFieldNames();
              } catch (Exception ignored) {
              }
            combo.setItems(choices);
            combo.setText(String.valueOf(field.get(input)));
            combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            controls.put(name, combo);
            continue;
          }
          var text = new TextVar(variables, body, SWT.BORDER);
          text.setText(String.valueOf(field.get(input)));
          text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
          control = text;
        }
        controls.put(name, control);
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    scroll.setContent(body);
    scroll.setMinSize(body.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    var buttons = new Composite(shell, SWT.NONE);
    buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
    buttons.setLayout(new GridLayout(2, true));
    var ok = new Button(buttons, SWT.PUSH);
    ok.setText("OK");
    var cancel = new Button(buttons, SWT.PUSH);
    cancel.setText("Cancel");
    ok.addListener(
        SWT.Selection,
        event -> {
          try {
            var edited = (RasterValueMeta) input.clone();
            for (var entry : controls.entrySet()) {
              var field = RasterValueMeta.class.getDeclaredField(entry.getKey());
              field.setAccessible(true);
              Object value =
                  entry.getValue() instanceof Button b
                      ? b.getSelection()
                      : entry.getValue() instanceof ComboVar combo
                          ? combo.getText()
                          : ((TextVar) entry.getValue()).getText();
              if (field.getType() == int.class) value = Integer.parseInt((String) value);
              field.set(edited, value);
            }
            edited.validateSettings();
            if (wTransformName.getText().isBlank())
              throw new IllegalArgumentException("Transform name is required");
            for (String name : controls.keySet()) {
              var field = RasterValueMeta.class.getDeclaredField(name);
              field.setAccessible(true);
              field.set(input, field.get(edited));
            }
            transformName = wTransformName.getText();
            input.setChanged();
            shell.dispose();
          } catch (Exception e) {
            var box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
            box.setText("Raster settings");
            box.setMessage(e.getMessage());
            box.open();
          }
        });
    cancel.addListener(
        SWT.Selection,
        event -> {
          transformName = null;
          shell.dispose();
        });
    shell.addListener(SWT.Close, event -> transformName = null);
    shell.setDefaultButton(ok);
    shell.setSize(760, 720);
    shell.open();
    while (!shell.isDisposed())
      if (!shell.getDisplay().readAndDispatch()) shell.getDisplay().sleep();
    return transformName;
  }

  private static String label(String field) {
    return switch (field) {
      case "source" -> "Local GeoTIFF / public COG URL";
      case "rasterField" -> "Input raster field / Reader output field";
      case "outputRasterField" -> "Output raster field (empty: replace input)";
      case "bands" -> "Bands (ALL or 1-based list)";
      case "clipMethod" -> "Clip method (POLYGON / BOUNDING_BOX)";
      case "interpolation" -> "Interpolation (NEAREST / BILINEAR)";
      case "outputType" -> "Numeric type (AUTO / SOURCE / FLOAT32 / FLOAT64)";
      case "extentMode" -> "Extent (AUTO / BOUNDING_BOX)";
      case "infoFields" -> "Metadata fields (width height crs bands)";
      case "output" -> "Output GeoTIFF";
      default -> field.replaceAll("([A-Z])", " $1");
    };
  }
}

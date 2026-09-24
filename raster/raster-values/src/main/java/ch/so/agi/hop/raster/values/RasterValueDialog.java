package ch.so.agi.hop.raster.values;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import ch.so.agi.hop.commons.ui.EditorKind;
import ch.so.agi.hop.commons.ui.ValueOrFieldControl;
import ch.so.agi.hop.raster.type.ValueMetaRaster;
import java.util.*;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Operation-specific fields, edited on a clone so Cancel cannot change the pipeline. */
public final class RasterValueDialog extends BaseTransformDialog {
  private final RasterValueMeta input;

  public RasterValueDialog(
      Shell parent, IVariables vars, RasterReaderMeta meta, PipelineMeta pipeline) {
    this(parent, vars, (RasterValueMeta) meta, pipeline);
  }

  public RasterValueDialog(
      Shell parent, IVariables vars, RasterClipMeta meta, PipelineMeta pipeline) {
    this(parent, vars, (RasterValueMeta) meta, pipeline);
  }

  public RasterValueDialog(
      Shell parent, IVariables vars, RasterReprojectMeta meta, PipelineMeta pipeline) {
    this(parent, vars, (RasterValueMeta) meta, pipeline);
  }

  public RasterValueDialog(
      Shell parent, IVariables vars, RasterZonalStatsMeta meta, PipelineMeta pipeline) {
    this(parent, vars, (RasterValueMeta) meta, pipeline);
  }

  public RasterValueDialog(
      Shell parent, IVariables vars, RasterInfoMeta meta, PipelineMeta pipeline) {
    this(parent, vars, (RasterValueMeta) meta, pipeline);
  }

  public RasterValueDialog(
      Shell parent, IVariables vars, RasterWriterMeta meta, PipelineMeta pipeline) {
    this(parent, vars, (RasterValueMeta) meta, pipeline);
  }

  public RasterValueDialog(
      Shell parent, IVariables vars, RasterValueMeta meta, PipelineMeta pipeline) {
    super(parent, vars, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("Raster " + input.operation() + " (GeoTools)");
    var outerLayout = new FormLayout();
    outerLayout.marginWidth = 8;
    outerLayout.marginHeight = 8;
    shell.setLayout(outerLayout);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    var scroll = new ScrolledComposite(shell, SWT.V_SCROLL);
    var scrollData = new FormData();
    scrollData.left = new FormAttachment(0, 0);
    scrollData.right = new FormAttachment(100, 0);
    scrollData.top = new FormAttachment(0, 0);
    scroll.setLayoutData(scrollData);
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);
    var body = new Composite(scroll, SWT.NONE);
    body.setLayout(new GridLayout(2, false));
    new Label(body, SWT.NONE).setText("Transform name");
    wTransformName = new Text(body, SWT.BORDER);
    wTransformName.setText(transformName);
    wTransformName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    var controls = new LinkedHashMap<String, Control>();
    var fieldLabels = new LinkedHashMap<String, Label>();
    String fields =
        switch (input.operation()) {
          case "READER" -> "rasterField";
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
      if (input.operation().equals("READER")) {
        new Label(body, SWT.NONE).setText(label("source", input.operation()));
        var sourceControl =
            ValueOrFieldControl.builder(body, variables)
                .editor(EditorKind.FILE_OPEN)
                .fileFilters(
                    new String[] {"*.tif", "*.tiff", "*"},
                    new String[] {"GeoTIFF", "TIFF", "All files"})
                .fieldProvider(() -> inputFieldNames(false))
                .build();
        sourceControl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sourceControl.setValue(
            input.isSourceField()
                ? new ValueOrField(SourceMode.FIELD, "", input.getSource())
                : new ValueOrField(SourceMode.CONFIGURED, input.getSource(), ""));
        controls.put("source", sourceControl);
      }
      for (String name : fields.split(" ")) {
        if (name.isEmpty()) continue;
        var field = RasterValueMeta.class.getDeclaredField(name);
        field.setAccessible(true);
        var fieldLabel = new Label(body, SWT.NONE);
        fieldLabel.setText(label(name, input.operation()));
        fieldLabels.put(name, fieldLabel);
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
          if (name.equals("rasterField") && input.operation().equals("READER")) {
            var text = new TextVar(variables, body, SWT.BORDER);
            text.setText(String.valueOf(field.get(input)));
            text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            controls.put(name, text);
            continue;
          }
          if (choices.length > 0 || name.equals("rasterField") || name.equals("geometryField")) {
            var combo = new ComboVar(variables, body, SWT.BORDER);
            if (name.equals("rasterField")) {
              choices = inputFieldNames(true);
            } else if (choices.length == 0)
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
      if (input.operation().equals("CLIP")) {
        var clipMethod = (ComboVar) controls.get("clipMethod");
        clipMethod.addSelectionListener(
            new SelectionAdapter() {
              @Override
              public void widgetSelected(SelectionEvent event) {
                updateClipControls(clipMethod.getText(), controls, fieldLabels);
              }
            });
        clipMethod.addModifyListener(
            event -> updateClipControls(clipMethod.getText(), controls, fieldLabels));
        updateClipControls(clipMethod.getText(), controls, fieldLabels);
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    scroll.setContent(body);
    scroll.setMinSize(body.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    var buttons = new Composite(shell, SWT.NONE);
    var buttonData = new FormData();
    buttonData.right = new FormAttachment(100, 0);
    buttonData.bottom = new FormAttachment(100, 0);
    buttons.setLayoutData(buttonData);
    scrollData.bottom = new FormAttachment(buttons, -8);
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
              Object value;
              if (entry.getValue() instanceof ValueOrFieldControl sourceControl) {
                var source = sourceControl.getValue();
                value = configuredSource(source);
                edited.setSourceField(source.mode() == SourceMode.FIELD);
              } else if (entry.getValue() instanceof Button b) {
                value = b.getSelection();
              } else if (entry.getValue() instanceof ComboVar combo) {
                value = combo.getText();
              } else {
                value = ((TextVar) entry.getValue()).getText();
              }
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
            if (controls.containsKey("source")) input.setSourceField(edited.isSourceField());
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
    var display = shell.getDisplay();
    while (!shell.isDisposed())
      if (!display.readAndDispatch()) display.sleep();
    return transformName;
  }

  private static String configuredSource(ValueOrField source) {
    return source.mode() == SourceMode.FIELD ? source.fieldName() : source.configuredValue();
  }

  private static void updateClipControls(
      String method, Map<String, Control> controls, Map<String, Label> labels) {
    boolean polygon = "POLYGON".equalsIgnoreCase(method);
    boolean boundingBox = "BOUNDING_BOX".equalsIgnoreCase(method);
    boolean recognized = polygon || boundingBox;
    setFieldEnabled("geometryField", polygon || !recognized, controls, labels);
    boolean enableBounds = boundingBox || !recognized;
    for (String name : java.util.List.of("minX", "minY", "maxX", "maxY", "bboxFields"))
      setFieldEnabled(name, enableBounds, controls, labels);
  }

  private static void setFieldEnabled(
      String name, boolean enabled, Map<String, Control> controls, Map<String, Label> labels) {
    Control control = controls.get(name);
    Label fieldLabel = labels.get(name);
    if (control != null) setControlTreeEnabled(control, enabled);
    if (fieldLabel != null) fieldLabel.setEnabled(enabled);
  }

  private static void setControlTreeEnabled(Control control, boolean enabled) {
    control.setEnabled(enabled);
    if (control instanceof Composite composite)
      for (Control child : composite.getChildren()) setControlTreeEnabled(child, enabled);
  }

  private String[] inputFieldNames(boolean rastersOnly) {
    try {
      IRowMeta fields = pipelineMeta.getPrevTransformFields(variables, transformName);
      if (fields == null) return new String[0];
      return rastersOnly ? rasterFieldNames(fields) : fields.getFieldNames();
    } catch (Exception ignored) {
      return new String[0];
    }
  }

  static String[] rasterFieldNames(IRowMeta fields) {
    if (fields == null) return new String[0];
    return fields.getValueMetaList().stream()
        .filter(value -> value.getType() == ValueMetaRaster.TYPE_RASTER)
        .map(org.apache.hop.core.row.IValueMeta::getName)
        .toArray(String[]::new);
  }

  private static String label(String field, String operation) {
    return switch (field) {
      case "source" -> "Local GeoTIFF / public COG URL";
      case "rasterField" ->
          operation.equals("READER") ? "Reader output field" : "Input raster field";
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

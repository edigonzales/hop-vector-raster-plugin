package ch.so.agi.hop.geotools.clip;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

public class RasterClipDialog extends BaseTransformDialog {
  private final RasterClipMeta input;
  private TextVar wSource;
  private Button wSourceField;
  private ComboVar wGeometryField;
  private TextVar wExplicitCrs;
  private TextVar wBand;
  private TextVar wNoData;
  private TextVar wPrefix;
  private Combo wClipMethod;
  private TextVar wMinX;
  private TextVar wMinY;
  private TextVar wMaxX;
  private TextVar wMaxY;
  private Button wBboxFields;
  private TextVar wOutput;
  private Button wOutputField;
  private Button wOverwrite;

  public RasterClipDialog(
      Shell parent, IVariables variables, RasterClipMeta meta, PipelineMeta pipeline) {
    super(parent, variables, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("Raster Clip (GeoTools)");
    shell.setLayout(new GridLayout(1, false));
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    ScrolledComposite scroll = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL);
    scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);
    Composite body = new Composite(scroll, SWT.NONE);
    body.setLayout(new GridLayout(2, false));
    PropsUi.setLook(body);
    new Label(body, SWT.NONE).setText("Transform name");
    wTransformName = new Text(body, SWT.BORDER);
    wTransformName.setText(transformName);
    wTransformName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Input raster");
    wSource = fileInput(body, false, "*.tif;*.tiff");
    wSource.setText(String.valueOf(input.getSource()));
    wSource.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Raster address from input field");
    wSourceField = new Button(body, SWT.CHECK);
    wSourceField.setSelection(input.isSourceField());
    new Label(body, SWT.NONE).setText("Geometry field");
    wGeometryField = new ComboVar(variables, body, SWT.BORDER);
    try {
      wGeometryField.setItems(
          pipelineMeta.getPrevTransformFields(variables, transformName).getFieldNames());
    } catch (Exception ignored) {
      /* Upstream metadata may not be available yet. */
    }
    wGeometryField.setText(String.valueOf(input.getGeometryField()));
    wGeometryField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Explicit geometry CRS (empty: geometry SRID)");
    wExplicitCrs = new TextVar(variables, body, SWT.BORDER);
    wExplicitCrs.setText(String.valueOf(input.getExplicitCrs()));
    wExplicitCrs.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Band (1 based)");
    wBand = new TextVar(variables, body, SWT.BORDER);
    wBand.setText(String.valueOf(input.getBand()));
    wBand.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("NoData override (empty: raster metadata)");
    wNoData = new TextVar(variables, body, SWT.BORDER);
    wNoData.setText(String.valueOf(input.getNoData()));
    wNoData.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Output field prefix");
    wPrefix = new TextVar(variables, body, SWT.BORDER);
    wPrefix.setText(String.valueOf(input.getPrefix()));
    wPrefix.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Clip method");
    wClipMethod = new Combo(body, SWT.READ_ONLY);
    wClipMethod.setItems(new String[] {"POLYGON", "BOUNDING_BOX"});
    wClipMethod.setText(input.getClipMethod());
    wClipMethod.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Minimum X");
    wMinX = new TextVar(variables, body, SWT.BORDER);
    wMinX.setText(String.valueOf(input.getMinX()));
    wMinX.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Minimum Y");
    wMinY = new TextVar(variables, body, SWT.BORDER);
    wMinY.setText(String.valueOf(input.getMinY()));
    wMinY.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Maximum X");
    wMaxX = new TextVar(variables, body, SWT.BORDER);
    wMaxX.setText(String.valueOf(input.getMaxX()));
    wMaxX.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Maximum Y");
    wMaxY = new TextVar(variables, body, SWT.BORDER);
    wMaxY.setText(String.valueOf(input.getMaxY()));
    wMaxY.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Bounding box values from input fields");
    wBboxFields = new Button(body, SWT.CHECK);
    wBboxFields.setSelection(input.isBboxFields());
    new Label(body, SWT.NONE).setText("Output GeoTIFF");
    wOutput = fileInput(body, true, "*.tif;*.tiff");
    wOutput.setText(String.valueOf(input.getOutput()));
    wOutput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Output path from input field");
    wOutputField = new Button(body, SWT.CHECK);
    wOutputField.setSelection(input.isOutputField());
    new Label(body, SWT.NONE).setText("Overwrite existing file");
    wOverwrite = new Button(body, SWT.CHECK);
    wOverwrite.setSelection(input.isOverwrite());
    Runnable enableMethod =
        () -> {
          boolean polygon = "POLYGON".equals(wClipMethod.getText());
          wGeometryField.setEnabled(polygon);
          for (Control field : new Control[] {wMinX, wMinY, wMaxX, wMaxY, wBboxFields})
            field.setEnabled(!polygon);
        };
    wClipMethod.addListener(SWT.Selection, e -> enableMethod.run());
    enableMethod.run();
    scroll.setContent(body);
    scroll.setMinSize(body.computeSize(760, SWT.DEFAULT));
    Composite buttons = new Composite(shell, SWT.NONE);
    buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
    buttons.setLayout(new GridLayout(2, true));
    Button ok = new Button(buttons, SWT.PUSH);
    ok.setText("OK");
    ok.addListener(SWT.Selection, e -> ok());
    Button cancel = new Button(buttons, SWT.PUSH);
    cancel.setText("Cancel");
    cancel.addListener(SWT.Selection, e -> cancel());
    shell.setSize(860, Math.min(820, getParent().getDisplay().getClientArea().height - 100));
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void ok() {
    try {
      if (wTransformName.getText().isBlank())
        throw new IllegalArgumentException("Transform name is required");
      RasterClipMeta draft = new RasterClipMeta();
      draft.setDefault();
      draft.setSource(wSource.getText());
      draft.setSourceField(wSourceField.getSelection());
      draft.setGeometryField(wGeometryField.getText());
      draft.setExplicitCrs(wExplicitCrs.getText());
      draft.setBand(Integer.parseInt(wBand.getText().trim()));
      draft.setNoData(wNoData.getText());
      draft.setPrefix(wPrefix.getText());
      draft.setClipMethod(wClipMethod.getText());
      draft.setMinX(wMinX.getText());
      draft.setMinY(wMinY.getText());
      draft.setMaxX(wMaxX.getText());
      draft.setMaxY(wMaxY.getText());
      draft.setBboxFields(wBboxFields.getSelection());
      draft.setOutput(wOutput.getText());
      draft.setOutputField(wOutputField.getSelection());
      draft.setOverwrite(wOverwrite.getSelection());
      draft.validateSettings();
      input.setSource(draft.getSource());
      input.setSourceField(draft.isSourceField());
      input.setGeometryField(draft.getGeometryField());
      input.setExplicitCrs(draft.getExplicitCrs());
      input.setBand(draft.getBand());
      input.setNoData(draft.getNoData());
      input.setPrefix(draft.getPrefix());
      input.setClipMethod(draft.getClipMethod());
      input.setMinX(draft.getMinX());
      input.setMinY(draft.getMinY());
      input.setMaxX(draft.getMaxX());
      input.setMaxY(draft.getMaxY());
      input.setBboxFields(draft.isBboxFields());
      input.setOutput(draft.getOutput());
      input.setOutputField(draft.isOutputField());
      input.setOverwrite(draft.isOverwrite());
      transformName = wTransformName.getText();
      input.setChanged();
      dispose();
    } catch (RuntimeException e) {
      MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      box.setText("Invalid settings");
      box.setMessage(e.getMessage() == null ? "Invalid settings" : e.getMessage());
      box.open();
    }
  }

  private TextVar fileInput(Composite parent, boolean save, String extensions) {
    Composite group = new Composite(parent, SWT.NONE);
    group.setLayout(new GridLayout(2, false));
    group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    PropsUi.setLook(group);
    TextVar text = new TextVar(variables, group, SWT.BORDER);
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button browse = new Button(group, SWT.PUSH);
    browse.setText("Browse…");
    browse.addListener(
        SWT.Selection,
        e -> {
          FileDialog dialog = new FileDialog(shell, save ? SWT.SAVE : SWT.OPEN);
          dialog.setFilterExtensions(new String[] {extensions, "*.*"});
          String selected = dialog.open();
          if (selected != null) text.setText(selected);
        });
    return text;
  }

  private void cancel() {
    transformName = null;
    dispose();
  }
}

package ch.so.agi.hop.raster.stats;

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

public class RasterZonalStatsDialog extends BaseTransformDialog {
  private final RasterZonalStatsMeta input;
  private TextVar wSource;
  private Button wSourceField;
  private ComboVar wGeometryField;
  private TextVar wExplicitCrs;
  private TextVar wBand;
  private TextVar wNoData;
  private TextVar wPrefix;
  private TextVar wStatistics;

  public RasterZonalStatsDialog(
      Shell parent, IVariables variables, RasterZonalStatsMeta meta, PipelineMeta pipeline) {
    super(parent, variables, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("Raster Zonal Statistics (GeoTools)");
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
    new Label(body, SWT.NONE)
        .setText("Statistics (mean min max sum stddev; count always included)");
    wStatistics = new TextVar(variables, body, SWT.BORDER);
    wStatistics.setText(String.valueOf(input.getStatistics()));
    wStatistics.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
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
      RasterZonalStatsMeta draft = new RasterZonalStatsMeta();
      draft.setDefault();
      draft.setSource(wSource.getText());
      draft.setSourceField(wSourceField.getSelection());
      draft.setGeometryField(wGeometryField.getText());
      draft.setExplicitCrs(wExplicitCrs.getText());
      draft.setBand(Integer.parseInt(wBand.getText().trim()));
      draft.setNoData(wNoData.getText());
      draft.setPrefix(wPrefix.getText());
      draft.setStatistics(wStatistics.getText());
      draft.validateSettings();
      input.setSource(draft.getSource());
      input.setSourceField(draft.isSourceField());
      input.setGeometryField(draft.getGeometryField());
      input.setExplicitCrs(draft.getExplicitCrs());
      input.setBand(draft.getBand());
      input.setNoData(draft.getNoData());
      input.setPrefix(draft.getPrefix());
      input.setStatistics(draft.getStatistics());
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

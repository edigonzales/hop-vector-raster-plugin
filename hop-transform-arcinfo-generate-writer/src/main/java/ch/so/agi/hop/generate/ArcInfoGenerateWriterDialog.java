package ch.so.agi.hop.generate;

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

public class ArcInfoGenerateWriterDialog extends BaseTransformDialog {
  private final ArcInfoGenerateWriterMeta input;
  private TextVar wOutput;
  private ComboVar wGeometryField;
  private Combo wGeometryType;
  private Combo wDimension;
  private ComboVar wIdField;
  private TextVar wStartId;
  private Button wDiscardExtraOrdinates;
  private TextVar wDecimals;
  private Button wComma;
  private Button wSkipEmpty;
  private Button wOverwrite;

  public ArcInfoGenerateWriterDialog(
      Shell parent, IVariables variables, ArcInfoGenerateWriterMeta meta, PipelineMeta pipeline) {
    super(parent, variables, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("ArcInfo Generate Writer");
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
    new Label(body, SWT.NONE).setText("Output GENERATE file");
    wOutput = fileInput(body, true, "*.gen");
    wOutput.setText(String.valueOf(input.getOutput()));
    wOutput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
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
    new Label(body, SWT.NONE).setText("Geometry type");
    wGeometryType = new Combo(body, SWT.READ_ONLY);
    wGeometryType.setItems(new String[] {"POINT", "LINE", "POLYGON"});
    wGeometryType.setText(input.getGeometryType());
    wGeometryType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Dimension");
    wDimension = new Combo(body, SWT.READ_ONLY);
    wDimension.setItems(new String[] {"XY", "XYZ"});
    wDimension.setText(input.getDimension());
    wDimension.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("ID field (empty: sequential IDs)");
    wIdField = new ComboVar(variables, body, SWT.BORDER);
    try {
      wIdField.setItems(
          pipelineMeta.getPrevTransformFields(variables, transformName).getFieldNames());
    } catch (Exception ignored) {
      /* Upstream metadata may not be available yet. */
    }
    wIdField.setText(String.valueOf(input.getIdField()));
    wIdField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Sequential start ID");
    wStartId = new TextVar(variables, body, SWT.BORDER);
    wStartId.setText(String.valueOf(input.getStartId()));
    wStartId.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Discard extra Z/M ordinates");
    wDiscardExtraOrdinates = new Button(body, SWT.CHECK);
    wDiscardExtraOrdinates.setSelection(input.isDiscardExtraOrdinates());
    new Label(body, SWT.NONE).setText("Decimal places (-1: preserve precision, 0–15: fixed)");
    wDecimals = new TextVar(variables, body, SWT.BORDER);
    wDecimals.setText(String.valueOf(input.getDecimals()));
    wDecimals.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Comma delimiter (default: space)");
    wComma = new Button(body, SWT.CHECK);
    wComma.setSelection(input.isComma());
    new Label(body, SWT.NONE).setText("Skip null / empty geometries");
    wSkipEmpty = new Button(body, SWT.CHECK);
    wSkipEmpty.setSelection(input.isSkipEmpty());
    new Label(body, SWT.NONE).setText("Overwrite existing file");
    wOverwrite = new Button(body, SWT.CHECK);
    wOverwrite.setSelection(input.isOverwrite());
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
      ArcInfoGenerateWriterMeta draft = new ArcInfoGenerateWriterMeta();
      draft.setDefault();
      draft.setOutput(wOutput.getText());
      draft.setGeometryField(wGeometryField.getText());
      draft.setGeometryType(wGeometryType.getText());
      draft.setDimension(wDimension.getText());
      draft.setIdField(wIdField.getText());
      draft.setStartId(Long.parseLong(wStartId.getText().trim()));
      draft.setDiscardExtraOrdinates(wDiscardExtraOrdinates.getSelection());
      draft.setDecimals(Integer.parseInt(wDecimals.getText().trim()));
      draft.setComma(wComma.getSelection());
      draft.setSkipEmpty(wSkipEmpty.getSelection());
      draft.setOverwrite(wOverwrite.getSelection());
      draft.validateSettings();
      input.setOutput(draft.getOutput());
      input.setGeometryField(draft.getGeometryField());
      input.setGeometryType(draft.getGeometryType());
      input.setDimension(draft.getDimension());
      input.setIdField(draft.getIdField());
      input.setStartId(draft.getStartId());
      input.setDiscardExtraOrdinates(draft.isDiscardExtraOrdinates());
      input.setDecimals(draft.getDecimals());
      input.setComma(draft.isComma());
      input.setSkipEmpty(draft.isSkipEmpty());
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

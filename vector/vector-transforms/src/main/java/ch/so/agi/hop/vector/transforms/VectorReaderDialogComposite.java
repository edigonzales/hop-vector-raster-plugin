package ch.so.agi.hop.vector.transforms;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Row-based dialog content matching the layout conventions used by the OGR input transform. */
final class VectorReaderDialogComposite extends Composite {

  private final IVariables variables;
  private final int middlePct;
  private final int margin;

  private TextVar fileName;
  private Button browseFileButton;
  private ComboVar layerName;
  private Text availableFieldsPreview;
  private TextVar geometryFieldName;
  private TextVar charset, timezone, crs;

  TextVar getCharset() {
    return charset;
  }

  TextVar getTimezone() {
    return timezone;
  }

  TextVar getCrs() {
    return crs;
  }

  VectorReaderDialogComposite(Composite parent, int style, IVariables variables, int middlePct) {
    super(parent, style);
    this.variables = variables;
    this.middlePct = middlePct;
    this.margin = PropsUi.getMargin();
    buildUi();
  }

  private void buildUi() {
    FormLayout layout = new FormLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    setLayout(layout);
    PropsUi.setLook(this);

    Composite lastRow = null;

    Composite fileRow = createRow(lastRow);
    fileName = new TextVar(variables, fileRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    browseFileButton = new Button(fileRow, SWT.PUSH | SWT.CENTER);
    buildRowControlWithButton(fileRow, "Vector file", fileName, browseFileButton, "Browse...");
    lastRow = fileRow;

    Composite layerRow = createRow(lastRow);
    layerName = new ComboVar(variables, layerRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    buildRowControl(layerRow, "Layer name (optional)", layerName);
    lastRow = layerRow;

    Composite charsetRow = createRow(lastRow);
    charset = new TextVar(variables, charsetRow, SWT.BORDER);
    buildRowControl(charsetRow, "DBF encoding (empty: auto)", charset);
    lastRow = charsetRow;
    Composite timezoneRow = createRow(lastRow);
    timezone = new TextVar(variables, timezoneRow, SWT.BORDER);
    buildRowControl(timezoneRow, "Date timezone", timezone);
    lastRow = timezoneRow;
    Composite crsRow = createRow(lastRow);
    crs = new TextVar(variables, crsRow, SWT.BORDER);
    buildRowControl(crsRow, "Assign CRS (EPSG or WKT)", crs);
    lastRow = crsRow;
    Composite fieldsRow = createRow(lastRow);
    availableFieldsPreview =
        new Text(fieldsRow, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
    buildRowMultiline(fieldsRow, "Available fields", availableFieldsPreview, 180);
    lastRow = fieldsRow;

    Composite geometryRow = createRow(lastRow);
    geometryFieldName = new TextVar(variables, geometryRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    geometryFieldName.setToolTipText(
        "Optional. If empty, the source geometry attribute name is preserved.");
    buildRowControl(geometryRow, "Geometry output field", geometryFieldName);
  }

  private Composite createRow(Composite underRow) {
    Composite row = new Composite(this, SWT.NONE);
    PropsUi.setLook(row);
    FormLayout rowLayout = new FormLayout();
    rowLayout.marginWidth = 0;
    rowLayout.marginHeight = 0;
    row.setLayout(rowLayout);

    FormData fdRow = new FormData();
    fdRow.left = new FormAttachment(0, 0);
    fdRow.right = new FormAttachment(100, 0);
    fdRow.top = underRow == null ? new FormAttachment(0, 0) : new FormAttachment(underRow, margin);
    row.setLayoutData(fdRow);
    return row;
  }

  private void buildRowControlWithButton(
      Composite row, String labelText, Control control, Button actionButton, String buttonLabel) {
    Label label = createRowLabel(row, labelText);

    PropsUi.setLook(actionButton);
    actionButton.setText(buttonLabel);
    FormData fdAction = new FormData();
    fdAction.right = new FormAttachment(100, 0);
    fdAction.top = new FormAttachment(0, 0);
    actionButton.setLayoutData(fdAction);

    PropsUi.setLook(control);
    FormData fdControl = new FormData();
    fdControl.left = new FormAttachment(middlePct, 0);
    fdControl.right = new FormAttachment(actionButton, -margin);
    fdControl.top = new FormAttachment(0, margin);
    control.setLayoutData(fdControl);

    FormData fdLabel = new FormData();
    fdLabel.left = new FormAttachment(0, 0);
    fdLabel.right = new FormAttachment(middlePct, -margin);
    fdLabel.top = new FormAttachment(control, 0, SWT.TOP);
    label.setLayoutData(fdLabel);
  }

  private void buildRowControl(Composite row, String labelText, Control control) {
    Label label = createRowLabel(row, labelText);

    PropsUi.setLook(control);
    FormData fdControl = new FormData();
    fdControl.left = new FormAttachment(middlePct, 0);
    fdControl.right = new FormAttachment(100, 0);
    fdControl.top = new FormAttachment(0, 0);
    control.setLayoutData(fdControl);

    FormData fdLabel = new FormData();
    fdLabel.left = new FormAttachment(0, 0);
    fdLabel.right = new FormAttachment(middlePct, -margin);
    fdLabel.top = new FormAttachment(control, 0, SWT.TOP);
    label.setLayoutData(fdLabel);
  }

  private void buildRowMultiline(Composite row, String labelText, Control control, int height) {
    Label label = createRowLabel(row, labelText);

    PropsUi.setLook(control);
    FormData fdControl = new FormData();
    fdControl.left = new FormAttachment(middlePct, 0);
    fdControl.right = new FormAttachment(100, 0);
    fdControl.top = new FormAttachment(0, 0);
    fdControl.height = height;
    control.setLayoutData(fdControl);

    FormData fdLabel = new FormData();
    fdLabel.left = new FormAttachment(0, 0);
    fdLabel.right = new FormAttachment(middlePct, -margin);
    fdLabel.top = new FormAttachment(control, 0, SWT.TOP);
    label.setLayoutData(fdLabel);
  }

  private Label createRowLabel(Composite row, String labelText) {
    Label label = new Label(row, SWT.RIGHT);
    label.setText(labelText);
    PropsUi.setLook(label);
    return label;
  }

  TextVar getFileName() {
    return fileName;
  }

  Button getBrowseFileButton() {
    return browseFileButton;
  }

  ComboVar getLayerName() {
    return layerName;
  }

  Text getAvailableFieldsPreview() {
    return availableFieldsPreview;
  }

  TextVar getGeometryFieldName() {
    return geometryFieldName;
  }
}

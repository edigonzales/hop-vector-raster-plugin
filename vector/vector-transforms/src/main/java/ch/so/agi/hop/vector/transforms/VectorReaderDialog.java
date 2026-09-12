package ch.so.agi.hop.vector.transforms;

import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class VectorReaderDialog extends BaseTransformDialog {

  private final VectorReaderMeta input;
  private TextVar[] wBounds;
  private TextVar wFileName, wCharset, wTimezone, wCrs;
  private org.eclipse.swt.widgets.Combo wFormat;
  private Button wbFile;
  private ComboVar wLayerName;
  private Text wAvailableFieldsPreview;
  private TextVar wGeometryFieldName;
  private List<VectorSchemaProbe.LayerDefinition> layerDefinitions = List.of();
  private boolean suppressSchemaRefresh;

  public VectorReaderDialog(
      Shell parent,
      IVariables variables,
      VectorReaderMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("Vector Reader");
    shell.setMinimumSize(800, 560);

    changed = input.hasChanged();
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();

    wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText("Transform name");
    PropsUi.setLook(wlTransformName);
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlTransformName.top = new FormAttachment(0, margin);
    wlTransformName.setLayoutData(fdlTransformName);

    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wTransformName.setText(transformName);
    PropsUi.setLook(wTransformName);
    fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(props.getMiddlePct(), 0);
    fdTransformName.right = new FormAttachment(100, 0);
    fdTransformName.top = new FormAttachment(0, margin);
    wTransformName.setLayoutData(fdTransformName);

    Label formatLabel = new Label(shell, SWT.RIGHT);
    formatLabel.setText("Format");
    FormData fl = new FormData();
    fl.left = new FormAttachment(0, 0);
    fl.right = new FormAttachment(props.getMiddlePct(), -margin);
    fl.top = new FormAttachment(wTransformName, margin);
    formatLabel.setLayoutData(fl);
    wFormat = new org.eclipse.swt.widgets.Combo(shell, SWT.READ_ONLY);
    wFormat.setItems(new String[] {"AUTO", "SHAPEFILE", "GEOPACKAGE", "FILEGEODATABASE"});
    FormData ff = new FormData();
    ff.left = new FormAttachment(props.getMiddlePct(), 0);
    ff.right = new FormAttachment(100, 0);
    ff.top = new FormAttachment(wTransformName, margin);
    wFormat.setLayoutData(ff);
    wFormat.setText(input.getFormat());
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText("OK");
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText("Cancel");
    setButtonPositions(new Button[] {wOk, wCancel}, margin, null);

    FormData fdMain = new FormData();
    fdMain.left = new FormAttachment(0, 0);
    fdMain.top = new FormAttachment(wFormat, margin * 2);
    fdMain.right = new FormAttachment(100, 0);
    fdMain.bottom = new FormAttachment(wOk, -margin * 2);

    VectorReaderDialogComposite content =
        new VectorReaderDialogComposite(shell, SWT.NONE, variables, props.getMiddlePct());
    content.setLayoutData(fdMain);

    wBounds = content.getFileGdbBounds();
    wCharset = content.getCharset();
    wTimezone = content.getTimezone();
    wCrs = content.getCrs();
    wFileName = content.getFileName();
    wbFile = content.getBrowseFileButton();
    wLayerName = content.getLayerName();
    wAvailableFieldsPreview = content.getAvailableFieldsPreview();
    wGeometryFieldName = content.getGeometryFieldName();

    wTransformName.addModifyListener(e -> input.setChanged());
    wFileName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressSchemaRefresh) {
            loadLayersAndPreview();
          }
        });
    wLayerName.addModifyListener(
        e -> {
          input.setChanged();
          if (!suppressSchemaRefresh) {
            refreshFieldsPreview();
          }
        });
    wGeometryFieldName.addModifyListener(e -> input.setChanged());
    wbFile.addListener(SWT.Selection, e -> browse());
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    wFormat.addListener(
        SWT.Selection,
        e -> {
          input.setChanged();
          loadLayersAndPreview();
        });
    for (var option : new TextVar[] {wCharset, wTimezone, wCrs})
      option.addModifyListener(
          e -> {
            if (!suppressSchemaRefresh) loadLayersAndPreview();
          });
    getData();
    loadLayersAndPreview();
    input.setChanged(changed);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void browse() {
    FileDialog dialog = new FileDialog(shell, SWT.OPEN);
    dialog.setFilterExtensions(new String[] {"*.shp;*.gpkg", "*.*"});
    dialog.setFilterNames(new String[] {"Vector files", "All files"});
    String current = wFileName.getText();
    if (!Utils.isEmpty(current)) {
      dialog.setFilterPath(current);
    }
    String selected = dialog.open();
    if (selected != null) {
      wFileName.setText(selected);
    }
  }

  private void getData() {
    suppressSchemaRefresh = true;
    try {
      wBounds[0].setText(input.getFileGdbXMin());
      wBounds[1].setText(input.getFileGdbYMin());
      wBounds[2].setText(input.getFileGdbXMax());
      wBounds[3].setText(input.getFileGdbYMax());
      wCharset.setText(input.getCharset());
      wTimezone.setText(input.getTimezone());
      wCrs.setText(input.getCrsOverride());
      wFileName.setText(Utils.isEmpty(input.getFileName()) ? "" : input.getFileName());
      wLayerName.setText(Utils.isEmpty(input.getLayerName()) ? "" : input.getLayerName());
      wGeometryFieldName.setText(
          Utils.isEmpty(input.getGeometryFieldName()) ? "" : input.getGeometryFieldName());
      resetAvailableFieldsPreview("Select a vector file to inspect its schema.");
    } finally {
      suppressSchemaRefresh = false;
    }
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void loadLayersAndPreview() {
    boolean shp =
        wFormat.getText().equals("SHAPEFILE")
            || wFormat.getText().equals("AUTO")
                && wFileName.getText().toLowerCase(java.util.Locale.ROOT).endsWith(".shp");
    wCharset.setEnabled(shp);
    wTimezone.setEnabled(shp);
    String resolvedFileName = resolveUiValue(wFileName.getText());
    if (resolvedFileName.isBlank()) {
      layerDefinitions = List.of();
      clearLayerCombo();
      resetAvailableFieldsPreview("Select a vector file to inspect its schema.");
      return;
    }
    if (resolvedFileName.contains("${")) {
      layerDefinitions = List.of();
      clearLayerCombo();
      resetAvailableFieldsPreview(
          "Schema preview unavailable because the file name contains unresolved variables.");
      return;
    }

    try {
      layerDefinitions =
          VectorSchemaProbe.readLayers(
              draft().request(variables, ch.so.agi.hop.vector.core.Diagnostics.NONE),
              wFormat.getText());
      populateLayerCombo(layerDefinitions);
      refreshFieldsPreview();
    } catch (Throwable e) {
      // Schema inspection is optional design-time assistance. A broken third-party SPI or native
      // service must never make the whole transform dialog impossible to open.
      layerDefinitions = List.of();
      clearLayerCombo();
      resetAvailableFieldsPreview("No schema information available.\n" + rootCauseMessage(e));
    }
  }

  private void populateLayerCombo(List<VectorSchemaProbe.LayerDefinition> layers) {
    String currentLayer = resolveUiValue(wLayerName.getText());
    suppressSchemaRefresh = true;
    try {
      wLayerName.removeAll();
      for (VectorSchemaProbe.LayerDefinition layer : layers) {
        wLayerName.add(layer.name());
      }

      if (layers.isEmpty()) {
        wLayerName.setText("");
        return;
      }

      try {
        wLayerName.setText(VectorSchemaProbe.resolveLayer(layers, currentLayer).name());
      } catch (IllegalArgumentException e) {
        wLayerName.setText(layers.get(0).name());
      }
    } finally {
      suppressSchemaRefresh = false;
    }
  }

  private void clearLayerCombo() {
    suppressSchemaRefresh = true;
    try {
      wLayerName.removeAll();
      wLayerName.setText("");
    } finally {
      suppressSchemaRefresh = false;
    }
  }

  private void refreshFieldsPreview() {
    if (layerDefinitions.isEmpty()) {
      resetAvailableFieldsPreview("Vector dataset contains no layers.");
      return;
    }

    try {
      VectorSchemaProbe.LayerDefinition layer =
          VectorSchemaProbe.resolveLayer(layerDefinitions, resolveUiValue(wLayerName.getText()));
      if (!layer.name().equals(wLayerName.getText())) {
        suppressSchemaRefresh = true;
        try {
          wLayerName.setText(layer.name());
        } finally {
          suppressSchemaRefresh = false;
        }
      }
      wAvailableFieldsPreview.setText(VectorSchemaProbe.formatFieldPreview(layer));
    } catch (IllegalArgumentException e) {
      resetAvailableFieldsPreview(e.getMessage());
    }
  }

  private void resetAvailableFieldsPreview(String message) {
    if (wAvailableFieldsPreview != null && !wAvailableFieldsPreview.isDisposed()) {
      wAvailableFieldsPreview.setText(message == null ? "" : message);
    }
  }

  private static String rootCauseMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    if (!Utils.isEmpty(message)) {
      return message;
    }
    return current.getClass().getSimpleName();
  }

  private String resolveUiValue(String value) {
    if (value == null) {
      return "";
    }
    return variables == null ? value.trim() : variables.resolve(value).trim();
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    transformName = wTransformName.getText();
    input.setFileGdbXMin(wBounds[0].getText());
    input.setFileGdbYMin(wBounds[1].getText());
    input.setFileGdbXMax(wBounds[2].getText());
    input.setFileGdbYMax(wBounds[3].getText());
    input.setCharset(wCharset.getText());
    input.setTimezone(wTimezone.getText());
    input.setCrsOverride(wCrs.getText());
    input.setFileName(wFileName.getText());
    input.setFormat(wFormat.getText());
    input.setLayerName(wLayerName.getText());
    input.setGeometryFieldName(wGeometryFieldName.getText());
    dispose();
  }

  private VectorReaderMeta draft() {
    var m = new VectorReaderMeta();
    m.setFileName(wFileName.getText());
    m.setFormat(wFormat.getText());
    m.setLayerName("");
    m.setGeometryFieldName(wGeometryFieldName.getText());
    m.setCharset(wCharset.getText());
    m.setTimezone(wTimezone.getText());
    m.setCrsOverride(wCrs.getText());
    return m;
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}

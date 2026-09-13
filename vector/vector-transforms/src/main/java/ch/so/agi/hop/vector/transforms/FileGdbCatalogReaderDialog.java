package ch.so.agi.hop.vector.transforms;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class FileGdbCatalogReaderDialog extends BaseTransformDialog {
  private final FileGdbCatalogReaderMeta input;

  public FileGdbCatalogReaderDialog(
      Shell parent, IVariables vars, FileGdbCatalogReaderMeta meta, PipelineMeta pipeline) {
    super(parent, vars, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("FileGDB Catalog Reader");
    shell.setLayout(new GridLayout(2, false));
    new Label(shell, SWT.NONE).setText("Transform name");
    Text name = new Text(shell, SWT.BORDER);
    name.setText(transformName);
    name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(shell, SWT.NONE).setText("Geodatabase directory");
    TextVar file = new TextVar(variables, shell, SWT.BORDER);
    file.setText(input.getFileName());
    file.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(shell, SWT.NONE).setText("Catalog output");
    Combo mode = new Combo(shell, SWT.READ_ONLY);
    mode.setItems(
        java.util.Arrays.stream(
                ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbCatalog.Mode.values())
            .map(Enum::name)
            .toArray(String[]::new));
    mode.setText(input.getMode());
    new Label(shell, SWT.NONE).setText("Exact name filter (optional)");
    TextVar filter = new TextVar(variables, shell, SWT.BORDER);
    filter.setText(input.getNameFilter());
    filter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button ok = new Button(shell, SWT.PUSH);
    ok.setText("OK");
    ok.addListener(
        SWT.Selection,
        e -> {
          transformName = name.getText();
          input.setFileName(file.getText());
          input.setMode(mode.getText());
          input.setNameFilter(filter.getText());
          input.setChanged();
          shell.dispose();
        });
    Button cancel = new Button(shell, SWT.PUSH);
    cancel.setText("Cancel");
    cancel.addListener(
        SWT.Selection,
        e -> {
          transformName = null;
          shell.dispose();
        });
    shell.addListener(SWT.Close, e -> transformName = null);
    shell.setDefaultButton(ok);
    shell.setSize(650, 240);
    shell.open();
    while (!shell.isDisposed())
      if (!shell.getDisplay().readAndDispatch()) shell.getDisplay().sleep();
    return transformName;
  }
}

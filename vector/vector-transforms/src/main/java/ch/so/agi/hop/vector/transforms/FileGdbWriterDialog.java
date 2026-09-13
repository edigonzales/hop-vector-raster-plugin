package ch.so.agi.hop.vector.transforms;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class FileGdbWriterDialog extends BaseTransformDialog {
  private final FileGdbWriterMeta input;

  public FileGdbWriterDialog(
      Shell parent, IVariables vars, FileGdbWriterMeta meta, PipelineMeta pipeline) {
    super(parent, vars, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("FileGDB Writer");
    shell.setLayout(new GridLayout(2, false));
    new Label(shell, SWT.NONE).setText("Transform name");
    Text name = new Text(shell, SWT.BORDER);
    name.setText(transformName);
    name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(shell, SWT.NONE).setText("New geodatabase directory");
    TextVar file = new TextVar(variables, shell, SWT.BORDER);
    file.setText(input.getFileName());
    file.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(shell, SWT.NONE).setText("JSON schema file");
    TextVar schema = new TextVar(variables, shell, SWT.BORDER);
    schema.setText(input.getSchemaFile());
    schema.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button browse = new Button(shell, SWT.PUSH);
    browse.setText("Browse schema…");
    browse.addListener(
        SWT.Selection,
        e -> {
          FileDialog d = new FileDialog(shell, SWT.OPEN);
          d.setFilterExtensions(new String[] {"*.json"});
          String p = d.open();
          if (p != null) schema.setText(p);
        });
    Button load = new Button(shell, SWT.PUSH);
    load.setText("Load datasets");
    Composite mappings = new Composite(shell, SWT.NONE);
    mappings.setLayout(new GridLayout(2, false));
    mappings.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
    java.util.Map<String, Combo> choices = new java.util.LinkedHashMap<>();
    java.util.function.Consumer<java.util.List<String>> populate =
        datasets -> {
          var previous = new java.util.HashMap<String, String>();
          choices.forEach((k, v) -> previous.put(k, v.getText()));
          for (Control c : mappings.getChildren()) c.dispose();
          choices.clear();
          for (String dataset : datasets) {
            new Label(mappings, SWT.NONE).setText(dataset);
            Combo from = new Combo(mappings, SWT.READ_ONLY);
            from.setItems(
                java.util.Arrays.stream(
                        pipelineMeta.getPrevTransforms(pipelineMeta.findTransform(transformName)))
                    .map(org.apache.hop.pipeline.transform.TransformMeta::getName)
                    .toArray(String[]::new));
            String selected =
                previous.getOrDefault(
                    dataset,
                    input.getInputs().stream()
                        .filter(i -> i.getDataset().equals(dataset))
                        .map(FileGdbWriterMeta.Input::getTransform)
                        .findFirst()
                        .orElse(""));
            from.setText(selected);
            from.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            choices.put(dataset, from);
          }
          mappings.layout(true, true);
          shell.layout(true, true);
        };
    populate.accept(input.getInputs().stream().map(FileGdbWriterMeta.Input::getDataset).toList());
    load.addListener(
        SWT.Selection,
        e -> {
          try {
            var s =
                ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbExportSchema.read(
                    java.nio.file.Path.of(variables.resolve(schema.getText())));
            populate.accept(
                s.datasets().stream()
                    .map(
                        ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbExportSchema.DatasetSpec
                            ::name)
                    .toList());
          } catch (Exception ex) {
            MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
            box.setMessage(ex.getMessage());
            box.open();
          }
        });
    Button ok = new Button(shell, SWT.PUSH);
    ok.setText("OK");
    ok.addListener(
        SWT.Selection,
        e -> {
          if (name.getText().isBlank()
              || choices.isEmpty()
              || choices.values().stream().anyMatch(c -> c.getText().isBlank())) {
            MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
            box.setMessage(
                "Provide a name and select one upstream transform for every dataset. Use Load"
                    + " datasets first.");
            box.open();
            return;
          }
          transformName = name.getText();
          input.setFileName(file.getText());
          input.setSchemaFile(schema.getText());
          input.setInputs(
              choices.entrySet().stream()
                  .map(v -> new FileGdbWriterMeta.Input(v.getKey(), v.getValue().getText()))
                  .toList());
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
    shell.setSize(760, 500);
    shell.open();
    while (!shell.isDisposed())
      if (!shell.getDisplay().readAndDispatch()) shell.getDisplay().sleep();
    return transformName;
  }
}

package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbExportSchema;
import ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbExportSession;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** One row per named input; each row explicitly creates or appends one dataset. */
public class FileGdbWriterDialog extends BaseTransformDialog {
  private final FileGdbWriterMeta input;
  private TextVar wFile, wSchema;
  private Combo wMode;
  private Composite mappings;
  private Text preview;
  private final List<InputRow> rows = new ArrayList<>();

  private record InputRow(
      TextVar dataset,
      Combo action,
      Combo transform,
      TextVar geometry,
      Button index,
      Button remove) {}

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
    new Label(shell, SWT.NONE).setText("Geodatabase");
    wFile = text(input.getFileName());
    new Label(shell, SWT.NONE).setText("Schreibmodus");
    wMode = new Combo(shell, SWT.READ_ONLY);
    wMode.setItems(new String[] {"Neue GDB erstellen", "Bestehende GDB ergänzen"});
    wMode.select(input.isExistingDatabase() ? 1 : 0);
    new Label(shell, SWT.NONE).setText("JSON-Schema (für neue Datasets)");
    wSchema = text(input.getSchemaFile());
    Composite tools = new Composite(shell, SWT.NONE);
    tools.setLayout(new GridLayout(5, false));
    tools.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    button(
        tools,
        "GDB auswählen…",
        () -> {
          DirectoryDialog dialog = new DirectoryDialog(shell);
          dialog.setFilterPath(variables.resolve(wFile.getText()));
          String selected = dialog.open();
          if (selected != null) wFile.setText(selected);
        });
    button(
        tools,
        "Schema auswählen…",
        () -> {
          FileDialog dialog = new FileDialog(shell, SWT.OPEN);
          dialog.setFilterExtensions(new String[] {"*.json"});
          String selected = dialog.open();
          if (selected != null) wSchema.setText(selected);
        });
    button(tools, "Datasets laden", this::load);
    button(tools, "Eingang hinzufügen", () -> add(new FileGdbWriterMeta.Input("", "")));
    button(tools, "Eingangsschema prüfen", () -> showPreview(true));
    var scroll = new org.eclipse.swt.custom.ScrolledComposite(shell, SWT.H_SCROLL | SWT.V_SCROLL);
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);
    scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
    mappings = new Composite(scroll, SWT.NONE);
    mappings.setLayout(new GridLayout(6, false));
    for (String label :
        new String[] {
          "Dataset", "Aktion", "Eingangstransform", "Quellgeometrie", "Index anlegen", ""
        }) new Label(mappings, SWT.NONE).setText(label);
    scroll.setContent(mappings);
    preview = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.WRAP);
    GridData previewData = new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1);
    previewData.heightHint = 150;
    preview.setLayoutData(previewData);
    for (var mapping : input.getInputs()) add(mapping);
    Label hint = new Label(shell, SWT.WRAP);
    hint.setText(
        "Eine Arbeitskopie sichert den gesamten Lauf ab. Andere Programme müssen die GDB schließen."
            + " Vorhandene räumliche Indizes werden immer nachgeführt.");
    hint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    wMode.addListener(
        SWT.Selection,
        e -> {
          updateMode();
          showPreview(false);
        });
    wFile.addModifyListener(
        e -> preview.setText("Datasets laden, um die Vorschau zu aktualisieren."));
    wSchema.addModifyListener(
        e -> preview.setText("Datasets laden, um die Schemavorschau zu aktualisieren."));
    updateMode();
    button(
        shell,
        "OK",
        () -> {
          if (name.getText().isBlank()
              || rows.isEmpty()
              || rows.stream()
                  .anyMatch(
                      r -> r.dataset().getText().isBlank() || r.transform().getText().isBlank())) {
            preview.setText("Name und genau einen Eingang je Dataset angeben.");
            return;
          }
          transformName = name.getText();
          input.setFileName(wFile.getText());
          input.setSchemaFile(wSchema.getText());
          input.setExistingDatabase(wMode.getSelectionIndex() == 1);
          var inputs = new ArrayList<FileGdbWriterMeta.Input>();
          for (var row : rows) {
            var value =
                new FileGdbWriterMeta.Input(row.dataset().getText(), row.transform().getText());
            value.setAction(
                row.action().getSelectionIndex() == 1 ? "APPEND_ROWS" : "CREATE_DATASET");
            value.setGeometryField(row.geometry().getText());
            value.setSpatialIndex(row.index().getSelection());
            value.setIndexConfigured(Boolean.TRUE.equals(row.index().getData("configured")));
            inputs.add(value);
          }
          input.setInputs(inputs);
          input.setChanged();
          shell.dispose();
        });
    button(
        shell,
        "Cancel",
        () -> {
          transformName = null;
          shell.dispose();
        });
    shell.addListener(SWT.Close, e -> transformName = null);
    shell.setSize(1040, 720);
    shell.open();
    Display display = shell.getDisplay();
    while (!shell.isDisposed()) if (!display.readAndDispatch()) display.sleep();
    return transformName;
  }

  private TextVar text(String value) {
    TextVar text = new TextVar(variables, shell, SWT.BORDER);
    text.setText(value);
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return text;
  }

  private void button(Composite parent, String label, Runnable action) {
    Button b = new Button(parent, SWT.PUSH);
    b.setText(label);
    b.addListener(SWT.Selection, e -> action.run());
  }

  private void add(FileGdbWriterMeta.Input value) {
    TextVar dataset = new TextVar(variables, mappings, SWT.BORDER);
    dataset.setText(value.getDataset());
    dataset.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Combo action = new Combo(mappings, SWT.READ_ONLY);
    action.setItems(new String[] {"Dataset anlegen", "Datensätze anhängen"});
    action.select(value.getAction().equals("APPEND_ROWS") ? 1 : 0);
    Combo transform = new Combo(mappings, SWT.READ_ONLY);
    transform.setItems(
        Arrays.stream(pipelineMeta.getPrevTransforms(pipelineMeta.findTransform(transformName)))
            .map(org.apache.hop.pipeline.transform.TransformMeta::getName)
            .toArray(String[]::new));
    transform.setText(value.getTransform());
    TextVar geometry = new TextVar(variables, mappings, SWT.BORDER);
    geometry.setText(value.getGeometryField());
    geometry.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button index = new Button(mappings, SWT.CHECK);
    index.setSelection(value.isSpatialIndex());
    index.setData("configured", value.isIndexConfigured());
    index.addListener(SWT.Selection, e -> index.setData("configured", true));
    if (!value.isIndexConfigured()
        && value.getAction().equals("CREATE_DATASET")
        && !wSchema.getText().isBlank()) {
      try {
        var definition =
            FileGdbExportSchema.read(path(wSchema.getText()), wMode.getSelectionIndex() == 1)
                .dataset(variables.resolve(value.getDataset()));
        index.setSelection(
            definition.geometry() != null
                && !Boolean.FALSE.equals(definition.geometry().spatialIndex()));
        geometry.setText(definition.geometry() == null ? "" : definition.geometry().source());
      } catch (Exception ignored) {
        // Unresolved design-time paths must not change the persisted schema defaults.
      }
    }
    Button remove = new Button(mappings, SWT.PUSH);
    remove.setText("Entfernen");
    InputRow row = new InputRow(dataset, action, transform, geometry, index, remove);
    rows.add(row);
    remove.addListener(
        SWT.Selection,
        e -> {
          rows.remove(row);
          for (Control c : new Control[] {dataset, action, transform, geometry, index, remove})
            c.dispose();
          layoutRows();
        });
    action.addListener(
        SWT.Selection,
        e -> {
          updateMode();
          showPreview(false);
        });
    dataset.addModifyListener(
        e -> preview.setText("Eingangsschema prüfen, um die Vorschau zu aktualisieren."));
    transform.addListener(SWT.Selection, e -> showPreview(false));
    updateMode();
    layoutRows();
  }

  private void updateMode() {
    for (var row : rows) {
      row.action().setEnabled(wMode.getSelectionIndex() == 1);
      if (wMode.getSelectionIndex() == 0) row.action().select(0);
      row.geometry().setEnabled(row.action().getSelectionIndex() == 1);
    }
  }

  private void layoutRows() {
    mappings.layout(true, true);
    ((org.eclipse.swt.custom.ScrolledComposite) mappings.getParent())
        .setMinSize(mappings.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    shell.layout(true, true);
  }

  private Path path(String value) {
    String resolved = variables.resolve(value);
    if (resolved.contains("${"))
      throw new IllegalArgumentException(
          "Vorschau nicht verfügbar: ungelöste Variable in " + value);
    return Path.of(resolved);
  }

  private void load() {
    try {
      var names = new HashSet<String>();
      for (var row : rows) names.add(row.dataset().getText().toLowerCase(Locale.ROOT));
      if (!wSchema.getText().isBlank())
        for (var d :
            FileGdbExportSchema.read(path(wSchema.getText()), wMode.getSelectionIndex() == 1)
                .datasets()) {
          if (names.add(d.name().toLowerCase(Locale.ROOT))) {
            var mapping = new FileGdbWriterMeta.Input(d.name(), "");
            mapping.setGeometryField(d.geometry() == null ? "" : d.geometry().source());
            mapping.setSpatialIndex(
                d.geometry() != null && !Boolean.FALSE.equals(d.geometry().spatialIndex()));
            add(mapping);
          }
        }
      if (wMode.getSelectionIndex() == 1)
        try (var db = ch.so.agi.filegdb.FileGeodatabase.open(path(wFile.getText()))) {
          for (var d : db.datasets())
            if (names.add(d.name().toLowerCase(Locale.ROOT))) {
              var mapping = new FileGdbWriterMeta.Input(d.name(), "");
              mapping.setAction("APPEND_ROWS");
              mapping.setGeometryField(d.isFeatureClass() ? "geometry" : "");
              add(mapping);
            }
        }
      showPreview(false);
    } catch (Exception e) {
      preview.setText(e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }

  private void showPreview(boolean check) {
    try {
      StringBuilder text = new StringBuilder();
      FileGdbExportSchema schema =
          wSchema.getText().isBlank()
              ? null
              : FileGdbExportSchema.read(path(wSchema.getText()), wMode.getSelectionIndex() == 1);
      for (var row : rows) {
        String dataset = variables.resolve(row.dataset().getText());
        if (dataset.contains("${"))
          throw new IllegalArgumentException("Ungelöste Dataset-Variable");
        var fields =
            check ? pipelineMeta.getTransformFields(variables, row.transform().getText()) : null;
        if (check && fields == null)
          throw new IllegalArgumentException("Eingangsschema fehlt: " + row.transform().getText());
        if (row.action().getSelectionIndex() == 1)
          text.append(
              FileGdbExportSession.preview(
                  path(wFile.getText()),
                  dataset,
                  fields,
                  variables.resolve(row.geometry().getText())));
        else {
          if (schema == null)
            throw new IllegalArgumentException(
                "Neue Datasets benötigen ein JSON-Schema: " + dataset);
          var definition = schema.dataset(dataset);
          if (check) FileGdbExportSession.validateCreation(definition, fields);
          text.append("Neues Dataset: ").append(definition).append('\n');
        }
        text.append('\n');
      }
      preview.setText((check ? "Eingangsschemata kompatibel.\n" : "") + text);
    } catch (Exception e) {
      preview.setText(e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }
}

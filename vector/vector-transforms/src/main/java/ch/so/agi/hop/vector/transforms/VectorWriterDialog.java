package ch.so.agi.hop.vector.transforms;

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

public class VectorWriterDialog extends BaseTransformDialog {
  private final VectorWriterMeta input;
  private TextVar wFileName;
  private Combo wFormat;
  private TextVar wLayer;
  private Composite generateOptions, layerOptions, shapeOptions;
  private TextVar wCrs, wCharset, wTimezone;
  private Combo wLayerType, wLayerDimension;
  private Table wFields;
  private org.eclipse.swt.custom.TableEditor fieldEditor;
  private Text fieldEdit;
  private ScrolledComposite scroll;
  private Composite body;
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
  private Composite flatGeobufOptions, parquetOptions, fileGdbOptions;
  private Combo wFileGdbMode;
  private TextVar wFileGdbResolution, wFileGdbTolerance, wFileGdbXOrigin, wFileGdbYOrigin;
  private Button wFileGdbIndex;
  private Button wFlatGeobufIndex, wFlatGeobufSkipEmpty;
  private Combo wParquetType, wParquetAlgorithm, wParquetCompression;
  private TextVar wParquetRowGroup;

  public VectorWriterDialog(
      Shell parent, IVariables variables, VectorWriterMeta meta, PipelineMeta pipeline) {
    super(parent, variables, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("Vector Writer");
    // Hop's shell help button uses FormData; keep the outer shell on FormLayout.
    org.eclipse.swt.layout.FormLayout outer = new org.eclipse.swt.layout.FormLayout();
    outer.marginWidth = 8;
    outer.marginHeight = 8;
    shell.setLayout(outer);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    scroll = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL);
    org.eclipse.swt.layout.FormData scrollData = new org.eclipse.swt.layout.FormData();
    scrollData.left = new org.eclipse.swt.layout.FormAttachment(0, 0);
    scrollData.right = new org.eclipse.swt.layout.FormAttachment(100, 0);
    scrollData.top = new org.eclipse.swt.layout.FormAttachment(0, 0);
    scroll.setLayoutData(scrollData);
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);
    body = new Composite(scroll, SWT.NONE);
    body.setLayout(new GridLayout(2, false));
    PropsUi.setLook(body);
    new Label(body, SWT.NONE).setText("Transform name");
    wTransformName = new Text(body, SWT.BORDER);
    wTransformName.setText(transformName);
    wTransformName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Format");
    wFormat = new Combo(body, SWT.READ_ONLY);
    wFormat.setItems(
        new String[] {
          "AUTO", "SHAPEFILE", "GEOPACKAGE", "ARCINFO_GENERATE", "FLATGEOBUF", "PARQUET"
        });
    wFormat.setText(input.getFormat());
    new Label(body, SWT.NONE).setText("Layer (optional)");
    wLayer = new TextVar(variables, body, SWT.BORDER);
    wLayer.setText(input.getLayerName() == null ? "" : input.getLayerName());
    wLayer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(body, SWT.NONE).setText("Output file");
    wFileName = fileInput(body, true, "*.gpkg;*.shp;*.gen;*.fgb;*.parquet");
    wFileName.setText(String.valueOf(input.getFileName()));
    wFileName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
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
    layerOptions = new Composite(body, SWT.NONE);
    layerOptions.setLayout(new GridLayout(2, false));
    layerOptions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    new Label(layerOptions, SWT.NONE).setText("Layer geometry type");
    wLayerType = new Combo(layerOptions, SWT.READ_ONLY);
    wLayerType.setItems(
        new String[] {
          "AUTO", "POINT", "MULTIPOINT", "LINESTRING", "MULTILINESTRING", "POLYGON", "MULTIPOLYGON"
        });
    wLayerType.setText(input.getLayerGeometryType());
    new Label(layerOptions, SWT.NONE).setText("Layer dimension");
    wLayerDimension = new Combo(layerOptions, SWT.READ_ONLY);
    wLayerDimension.setItems(new String[] {"AUTO", "XY", "XYZ", "XYM", "XYZM"});
    wLayerDimension.setText(input.getLayerDimension());
    wCrs = optionText(layerOptions, "Assign CRS (EPSG or WKT)", input.getCrsOverride());
    shapeOptions = new Composite(body, SWT.NONE);
    shapeOptions.setLayout(new GridLayout(2, false));
    shapeOptions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    wCharset = optionText(shapeOptions, "DBF encoding (empty: UTF-8)", input.getCharset());
    wTimezone = optionText(shapeOptions, "Date timezone", input.getTimezone());
    new Label(shapeOptions, SWT.NONE).setText("DBF fields (double-click to edit)");
    Button getFields = new Button(shapeOptions, SWT.PUSH);
    getFields.setText("Get fields");
    wFields = new Table(shapeOptions, SWT.BORDER | SWT.FULL_SELECTION);
    wFields.setHeaderVisible(true);
    wFields.setLinesVisible(true);
    GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1);
    tableData.heightHint = 150;
    wFields.setLayoutData(tableData);
    for (String name : new String[] {"Source field", "Target name", "Width", "Decimal places"}) {
      TableColumn c = new TableColumn(wFields, SWT.NONE);
      c.setText(name);
      c.setWidth(160);
    }
    for (var f : input.getShapefileFields())
      new TableItem(wFields, SWT.NONE)
          .setText(
              new String[] {
                f.getSource(),
                f.getTarget(),
                String.valueOf(f.getWidth()),
                String.valueOf(f.getScale())
              });
    getFields.addListener(
        SWT.Selection,
        e -> {
          try {
            var rm = pipelineMeta.getPrevTransformFields(variables, transformName);
            wFields.removeAll();
            for (var f : rm.getValueMetaList())
              if (!f.getName().equals(wGeometryField.getText()))
                new TableItem(wFields, SWT.NONE)
                    .setText(
                        new String[] {
                          f.getName(),
                          f.getName(),
                          String.valueOf(f.getLength()),
                          String.valueOf(f.getPrecision())
                        });
          } catch (Exception ex) {
            showError(ex);
          }
        });
    fieldEditor = new org.eclipse.swt.custom.TableEditor(wFields);
    fieldEditor.grabHorizontal = true;
    wFields.addListener(
        SWT.MouseDoubleClick,
        e -> {
          var item = wFields.getItem(new org.eclipse.swt.graphics.Point(e.x, e.y));
          if (item == null) return;
          for (int c = 1; c < 4; c++)
            if (item.getBounds(c).contains(e.x, e.y)) {
              if (fieldEdit != null && !fieldEdit.isDisposed()) fieldEdit.dispose();
              final int column = c;
              fieldEdit = new Text(wFields, SWT.BORDER);
              fieldEdit.setText(item.getText(c));
              fieldEdit.addModifyListener(ev -> item.setText(column, fieldEdit.getText()));
              fieldEditor.setEditor(fieldEdit, item, c);
              fieldEdit.selectAll();
              fieldEdit.setFocus();
              break;
            }
        });
    Button remove = new Button(shapeOptions, SWT.PUSH);
    remove.setText("Remove selected mapping");
    remove.addListener(SWT.Selection, e -> wFields.remove(wFields.getSelectionIndices()));
    Button preview = new Button(shapeOptions, SWT.PUSH);
    preview.setText("Preview DBF schema");
    preview.addListener(
        SWT.Selection,
        e -> {
          try {
            VectorWriterMeta draft = new VectorWriterMeta();
            readAdditional(draft);
            var rm = pipelineMeta.getPrevTransformFields(variables, transformName);
            int gi = rm.indexOfValue(wGeometryField.getText());
            var request =
                new ch.so.agi.hop.vector.core.WriteRequest(
                    java.nio.file.Path.of("preview.shp"),
                    "preview",
                    rm,
                    gi,
                    null,
                    null,
                    draft.options(ch.so.agi.hop.vector.core.VectorFormat.SHAPEFILE, variables),
                    ch.so.agi.hop.vector.core.Diagnostics.NONE);
            MessageBox box = new MessageBox(shell, SWT.OK);
            box.setText("DBF output schema");
            box.setMessage(
                ch.so.agi.hop.vector.formats.shapefile.ShapefileProvider.preview(request));
            box.open();
          } catch (Exception ex) {
            showError(ex);
          }
        });
    generateOptions = new Composite(body, SWT.NONE);
    generateOptions.setLayout(new GridLayout(2, false));
    generateOptions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    new Label(generateOptions, SWT.NONE).setText("Geometry type");
    wGeometryType = new Combo(generateOptions, SWT.READ_ONLY);
    wGeometryType.setItems(new String[] {"POINT", "LINE", "POLYGON"});
    wGeometryType.setText(input.getGeometryType());
    wGeometryType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(generateOptions, SWT.NONE).setText("Dimension");
    wDimension = new Combo(generateOptions, SWT.READ_ONLY);
    wDimension.setItems(new String[] {"XY", "XYZ"});
    wDimension.setText(input.getDimension());
    wDimension.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(generateOptions, SWT.NONE).setText("ID field (empty: sequential IDs)");
    wIdField = new ComboVar(variables, generateOptions, SWT.BORDER);
    try {
      wIdField.setItems(
          pipelineMeta.getPrevTransformFields(variables, transformName).getFieldNames());
    } catch (Exception ignored) {
      /* Upstream metadata may not be available yet. */
    }
    wIdField.setText(String.valueOf(input.getIdField()));
    wIdField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(generateOptions, SWT.NONE).setText("Sequential start ID");
    wStartId = new TextVar(variables, generateOptions, SWT.BORDER);
    wStartId.setText(String.valueOf(input.getStartId()));
    wStartId.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(generateOptions, SWT.NONE).setText("Discard extra Z/M ordinates");
    wDiscardExtraOrdinates = new Button(generateOptions, SWT.CHECK);
    wDiscardExtraOrdinates.setSelection(input.isDiscardExtraOrdinates());
    new Label(generateOptions, SWT.NONE)
        .setText("Decimal places (-1: preserve precision, 0–15: fixed)");
    wDecimals = new TextVar(variables, generateOptions, SWT.BORDER);
    wDecimals.setText(String.valueOf(input.getDecimals()));
    wDecimals.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(generateOptions, SWT.NONE).setText("Comma delimiter (default: space)");
    wComma = new Button(generateOptions, SWT.CHECK);
    wComma.setSelection(input.isComma());
    new Label(generateOptions, SWT.NONE).setText("Skip null / empty geometries");
    wSkipEmpty = new Button(generateOptions, SWT.CHECK);
    wSkipEmpty.setSelection(input.isSkipEmpty());
    new Label(body, SWT.NONE).setText("Overwrite existing file");
    wOverwrite = new Button(body, SWT.CHECK);
    wOverwrite.setSelection(input.isOverwrite());
    fileGdbOptions = optionGroup();
    wFileGdbMode =
        optionCombo(
            fileGdbOptions,
            "FileGDB precision defaults",
            new String[] {"LEGACY", "AUTO"},
            input.getFileGdbPrecisionMode());
    wFileGdbResolution =
        optionText(
            fileGdbOptions,
            "XY resolution (CRS units; empty: default)",
            input.getFileGdbXyResolution());
    wFileGdbTolerance =
        optionText(
            fileGdbOptions, "XY cluster tolerance (CRS units)", input.getFileGdbXyTolerance());
    wFileGdbXOrigin =
        optionText(fileGdbOptions, "X grid origin (optional)", input.getFileGdbXOrigin());
    wFileGdbYOrigin =
        optionText(fileGdbOptions, "Y grid origin (optional)", input.getFileGdbYOrigin());
    new Label(fileGdbOptions, SWT.NONE).setText("Create native spatial index");
    wFileGdbIndex = new Button(fileGdbOptions, SWT.CHECK);
    wFileGdbIndex.setSelection(input.isFileGdbSpatialIndex());
    flatGeobufOptions = optionGroup();
    new Label(flatGeobufOptions, SWT.NONE).setText("Spatial index (reorders features)");
    wFlatGeobufIndex = new Button(flatGeobufOptions, SWT.CHECK);
    wFlatGeobufIndex.setSelection(input.isFlatGeobufIndex());
    new Label(flatGeobufOptions, SWT.NONE).setText("Skip NULL/EMPTY geometries with warning");
    wFlatGeobufSkipEmpty = new Button(flatGeobufOptions, SWT.CHECK);
    wFlatGeobufSkipEmpty.setSelection(input.isFlatGeobufSkipEmpty());
    parquetOptions = optionGroup();
    wParquetType =
        optionCombo(
            parquetOptions,
            "Logical geometry type",
            new String[] {"GEOMETRY", "GEOGRAPHY"},
            input.getParquetLogicalType());
    wParquetAlgorithm =
        optionCombo(
            parquetOptions,
            "Geography interpolation",
            new String[] {"SPHERICAL", "VINCENTY", "THOMAS", "ANDOYER", "KARNEY"},
            input.getParquetAlgorithm());
    wParquetCompression =
        optionCombo(
            parquetOptions,
            "Compression",
            new String[] {"GZIP", "UNCOMPRESSED"},
            input.getParquetCompression());
    wParquetRowGroup =
        optionText(
            parquetOptions,
            "Row group size (bytes)",
            Long.toString(input.getParquetRowGroupSize()));
    wParquetType.addListener(SWT.Selection, e -> updateFormat());
    scroll.setContent(body);
    scroll.setMinSize(body.computeSize(760, SWT.DEFAULT));
    wFormat.addListener(SWT.Selection, e -> updateFormat());
    wFileName.addModifyListener(e -> updateFormat());
    updateFormat();
    Composite buttons = new Composite(shell, SWT.NONE);
    org.eclipse.swt.layout.FormData buttonData = new org.eclipse.swt.layout.FormData();
    buttonData.right = new org.eclipse.swt.layout.FormAttachment(100, 0);
    buttonData.bottom = new org.eclipse.swt.layout.FormAttachment(100, 0);
    buttons.setLayoutData(buttonData);
    scrollData.bottom = new org.eclipse.swt.layout.FormAttachment(buttons, -8);
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

  private void updateFormat() {
    ch.so.agi.hop.vector.core.VectorFormat format = null;
    try {
      format =
          ch.so.agi.hop.vector.core.VectorFormat.resolve(
              wFormat.getText(), java.nio.file.Path.of(wFileName.getText()));
    } catch (Exception ignored) {
    }
    boolean generate = format != null && !format.supportsAttributes();
    boolean shape = format == ch.so.agi.hop.vector.core.VectorFormat.SHAPEFILE;
    layerOptions.setVisible(!generate);
    ((GridData) layerOptions.getLayoutData()).exclude = generate;
    shapeOptions.setVisible(shape);
    ((GridData) shapeOptions.getLayoutData()).exclude = !shape;
    generateOptions.setVisible(generate);
    ((GridData) generateOptions.getLayoutData()).exclude = !generate;
    boolean filegdb = format == ch.so.agi.hop.vector.core.VectorFormat.FILEGEODATABASE;
    fileGdbOptions.setVisible(filegdb);
    ((GridData) fileGdbOptions.getLayoutData()).exclude = !filegdb;
    boolean fgb = format == ch.so.agi.hop.vector.core.VectorFormat.FLATGEOBUF;
    boolean parquet = format == ch.so.agi.hop.vector.core.VectorFormat.PARQUET;
    flatGeobufOptions.setVisible(fgb);
    ((GridData) flatGeobufOptions.getLayoutData()).exclude = !fgb;
    parquetOptions.setVisible(parquet);
    ((GridData) parquetOptions.getLayoutData()).exclude = !parquet;
    wParquetAlgorithm.setEnabled(wParquetType.getText().equals("GEOGRAPHY"));
    wLayer.setEnabled(!generate);
    body.layout(true, true);
    scroll.setMinSize(body.computeSize(760, SWT.DEFAULT));
  }

  private void ok() {
    try {
      if (wTransformName.getText().isBlank())
        throw new IllegalArgumentException("Transform name is required");
      VectorWriterMeta draft = new VectorWriterMeta();
      draft.setDefault();
      draft.setFileName(wFileName.getText());
      draft.setFormat(wFormat.getText());
      draft.setLayerName(wLayer.getText());
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
      readAdditional(draft);
      draft.validateSettings();
      input.setFileName(draft.getFileName());
      input.setFormat(draft.getFormat());
      input.setLayerName(draft.getLayerName());
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
      readAdditional(input);
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

  private Composite optionGroup() {
    Composite group = new Composite(body, SWT.NONE);
    group.setLayout(new GridLayout(2, false));
    group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    return group;
  }

  private Combo optionCombo(Composite group, String label, String[] values, String value) {
    new Label(group, SWT.NONE).setText(label);
    Combo combo = new Combo(group, SWT.READ_ONLY);
    combo.setItems(values);
    combo.setText(value);
    return combo;
  }

  private TextVar optionText(Composite parent, String label, String value) {
    new Label(parent, SWT.NONE).setText(label);
    TextVar t = new TextVar(variables, parent, SWT.BORDER);
    t.setText(value);
    t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return t;
  }

  private void readAdditional(VectorWriterMeta meta) {
    meta.setFileGdbPrecisionMode(wFileGdbMode.getText());
    meta.setFileGdbXyResolution(wFileGdbResolution.getText());
    meta.setFileGdbXyTolerance(wFileGdbTolerance.getText());
    meta.setFileGdbXOrigin(wFileGdbXOrigin.getText());
    meta.setFileGdbYOrigin(wFileGdbYOrigin.getText());
    meta.setFileGdbSpatialIndex(wFileGdbIndex.getSelection());
    meta.setFlatGeobufIndex(wFlatGeobufIndex.getSelection());
    meta.setFlatGeobufSkipEmpty(wFlatGeobufSkipEmpty.getSelection());
    meta.setParquetLogicalType(wParquetType.getText());
    meta.setParquetAlgorithm(wParquetAlgorithm.getText());
    meta.setParquetCompression(wParquetCompression.getText());
    meta.setParquetRowGroupSize(Long.parseLong(wParquetRowGroup.getText().trim()));
    meta.setCrsOverride(wCrs.getText());
    meta.setCharset(wCharset.getText());
    meta.setTimezone(wTimezone.getText());
    meta.setLayerGeometryType(wLayerType.getText());
    meta.setLayerDimension(wLayerDimension.getText());
    java.util.List<ShapefileFieldMeta> fields = new java.util.ArrayList<>();
    for (var item : wFields.getItems())
      fields.add(
          new ShapefileFieldMeta(
              item.getText(0),
              item.getText(1),
              Integer.parseInt(item.getText(2)),
              Integer.parseInt(item.getText(3))));
    meta.setShapefileFields(fields);
  }

  private void showError(Exception ex) {
    MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
    box.setText("Invalid settings");
    box.setMessage(ex.getMessage() == null ? ex.toString() : ex.getMessage());
    box.open();
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

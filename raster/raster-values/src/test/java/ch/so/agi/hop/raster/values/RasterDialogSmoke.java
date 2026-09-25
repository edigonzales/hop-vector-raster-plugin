package ch.so.agi.hop.raster.values;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import ch.so.agi.hop.commons.ui.ValueOrFieldControl;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformDialog;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/** Opens each raster dialog through the constructor contract used by Hop 2.19. */
public final class RasterDialogSmoke {
  private record DialogCase(RasterValueMeta meta, String expectedLabel, boolean reader) {}

  private RasterDialogSmoke() {}

  private static void whenOpened(
      Display display, Shell parent, String title, DialogCase dialogCase) {
    display.timerExec(
        100,
        () -> {
          boolean opening =
              Arrays.stream(Thread.currentThread().getStackTrace())
                  .anyMatch(
                      frame ->
                          frame.getClassName().equals(Shell.class.getName())
                              && frame.getMethodName().equals("open"));
          Shell dialogShell =
              Arrays.stream(parent.getShells())
                  .filter(shell -> !shell.isDisposed() && shell.isVisible())
                  .findFirst()
                  .orElse(null);
          if (opening || dialogShell == null) {
            whenOpened(display, parent, title, dialogCase);
            return;
          }

          try {
            if (!title.equals(dialogShell.getText()))
              throw new AssertionError(
                  "Expected raster dialog title " + title + " but got " + dialogShell.getText());
            if (!containsLabel(dialogShell, dialogCase.expectedLabel()))
              throw new AssertionError(
                  "Raster dialog is missing field: " + dialogCase.expectedLabel());
            Control editor = editorAfterLabel(dialogShell, dialogCase.expectedLabel());
            if (dialogCase.reader()) {
              if (!(editor instanceof TextVar) || editor instanceof ComboVar)
                throw new AssertionError("Reader output field must be a text editor");
              verifySourceControl(dialogShell);
              ((TextVar) editor).setText("reader_raster");
              Button ok = findButtonByText(dialogShell, "OK");
              if (ok == null) throw new AssertionError("Raster dialog is missing its OK button");
              ok.notifyListeners(org.eclipse.swt.SWT.Selection, new Event());
            } else if (dialogCase.meta().operation().equals("CLIP")) {
              verifyClipDialog(dialogShell);
              Button ok = findButtonByText(dialogShell, "OK");
              if (ok == null) throw new AssertionError("Raster dialog is missing its OK button");
              ok.notifyListeners(org.eclipse.swt.SWT.Selection, new Event());
            } else if (dialogCase.meta().operation().equals("WRITER")) {
              verifyWriterDialog(
                  dialogShell, ((RasterWriterMeta) dialogCase.meta()).isOutputField());
              Button ok = findButtonByText(dialogShell, "OK");
              if (ok == null) throw new AssertionError("Raster dialog is missing its OK button");
              ok.notifyListeners(org.eclipse.swt.SWT.Selection, new Event());
            } else if (!(editor instanceof ComboVar)) {
              throw new AssertionError("Input raster field must be a field selector");
            } else {
              dialogShell.close();
            }
            System.out.println("Opened " + title + " through Hop's constructor signature");
          } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
          }
        });
  }

  private static boolean containsLabel(org.eclipse.swt.widgets.Control control, String expected) {
    if (control instanceof Label label && expected.equals(label.getText())) return true;
    if (control instanceof org.eclipse.swt.widgets.Composite composite)
      for (var child : composite.getChildren()) if (containsLabel(child, expected)) return true;
    return false;
  }

  private static Control editorAfterLabel(Control control, String expected) {
    if (!(control instanceof org.eclipse.swt.widgets.Composite composite)) return null;
    Control[] children = composite.getChildren();
    for (int i = 0; i + 1 < children.length; i++)
      if (children[i] instanceof Label label && expected.equals(label.getText()))
        return children[i + 1];
    for (Control child : children) {
      Control editor = editorAfterLabel(child, expected);
      if (editor != null) return editor;
    }
    return null;
  }

  private static Label findLabel(Control control, String expected) {
    if (control instanceof Label label && expected.equals(label.getText())) return label;
    if (control instanceof org.eclipse.swt.widgets.Composite composite)
      for (Control child : composite.getChildren()) {
        Label found = findLabel(child, expected);
        if (found != null) return found;
      }
    return null;
  }

  private static void verifyFieldEnabled(Shell shell, String labelText, boolean expected) {
    Control editor = editorAfterLabel(shell, labelText);
    Label label = findLabel(shell, labelText);
    if (editor == null || label == null)
      throw new AssertionError("Raster Clip dialog is missing field: " + labelText);
    if (!controlTreeEnabled(editor, expected) || label.getEnabled() != expected)
      throw new AssertionError(
          "Expected "
              + labelText
              + " enabled="
              + expected
              + " but editor="
              + controlTreeEnabled(editor, true)
              + ", label="
              + label.getEnabled());
  }

  private static boolean controlTreeEnabled(Control control, boolean expected) {
    if (control instanceof TextVar text && text.getTextWidget().getEnabled() != expected)
      return false;
    if (control instanceof ComboVar combo && combo.getCComboWidget().getEnabled() != expected)
      return false;
    if (!(control instanceof TextVar)
        && !(control instanceof ComboVar)
        && control.getEnabled() != expected) return false;
    if (control instanceof org.eclipse.swt.widgets.Composite composite)
      for (Control child : composite.getChildren())
        if (!controlTreeEnabled(child, expected)) return false;
    return true;
  }

  private static void setTextValue(Shell shell, String labelText, String value) {
    Control editor = editorAfterLabel(shell, labelText);
    if (editor instanceof TextVar text) text.setText(value);
    else if (editor instanceof ComboVar combo) combo.setText(value);
    else throw new AssertionError("Expected a text editor for " + labelText);
  }

  private static String textValue(Shell shell, String labelText) {
    Control editor = editorAfterLabel(shell, labelText);
    if (editor instanceof TextVar text) return text.getText();
    if (editor instanceof ComboVar combo) return combo.getText();
    throw new AssertionError("Expected a text editor for " + labelText);
  }

  private static void selectCombo(ComboVar combo, int index) {
    combo.select(index);
    Event selection = new Event();
    selection.type = org.eclipse.swt.SWT.Selection;
    combo.getCComboWidget().notifyListeners(org.eclipse.swt.SWT.Selection, selection);
  }

  private static void verifyClipDialog(Shell shell) {
    Control methodEditor = editorAfterLabel(shell, "Clip method (POLYGON / BOUNDING_BOX)");
    if (!(methodEditor instanceof ComboVar method))
      throw new AssertionError("Clip method must be a combo box");
    verifyClipCommonFieldsEnabled(shell);
    verifyFieldEnabled(shell, "geometry Field", true);
    verifyFieldEnabled(shell, "explicit Crs", true);
    for (String field :
        List.of(
            "Use input fields for bounding box coordinates", "min X", "min Y", "max X", "max Y"))
      verifyFieldEnabled(shell, field, false);

    setTextValue(shell, "geometry Field", "mask_geom");
    setTextValue(shell, "explicit Crs", "EPSG:2056");
    setTextValue(shell, "min X", "1");
    setTextValue(shell, "min Y", "2");
    setTextValue(shell, "max X", "3");
    setTextValue(shell, "max Y", "4");
    String bboxFieldsLabel = "Use input fields for bounding box coordinates";
    Control bboxFieldsEditor = editorAfterLabel(shell, bboxFieldsLabel);
    if (!(bboxFieldsEditor instanceof Button bboxFields))
      throw new AssertionError("Bounding box field mode must be a checkbox");
    Control minXEditor = editorAfterLabel(shell, "min X");
    if (minXEditor == null || bboxFieldsEditor.getBounds().y >= minXEditor.getBounds().y)
      throw new AssertionError("Bounding box field mode must appear before the coordinate inputs");
    bboxFields.setSelection(true);

    selectCombo(method, 1);
    if (!"BOUNDING_BOX".equals(method.getText()))
      throw new AssertionError("Clip method selection did not change to BOUNDING_BOX");
    verifyClipCommonFieldsEnabled(shell);
    verifyFieldEnabled(shell, "geometry Field", false);
    verifyFieldEnabled(shell, "explicit Crs", true);
    for (String field :
        List.of(
            "Use input fields for bounding box coordinates", "min X", "min Y", "max X", "max Y"))
      verifyFieldEnabled(shell, field, true);

    selectCombo(method, 0);
    if (!"POLYGON".equals(method.getText()))
      throw new AssertionError("Clip method selection did not change back to POLYGON");
    verifyFieldEnabled(shell, "geometry Field", true);
    verifyClipCommonFieldsEnabled(shell);
    for (String field :
        List.of(
            "Use input fields for bounding box coordinates", "min X", "min Y", "max X", "max Y"))
      verifyFieldEnabled(shell, field, false);
    if (!"mask_geom".equals(textValue(shell, "geometry Field"))
        || !"EPSG:2056".equals(textValue(shell, "explicit Crs"))
        || !"1".equals(textValue(shell, "min X"))
        || !"2".equals(textValue(shell, "min Y"))
        || !"3".equals(textValue(shell, "max X"))
        || !"4".equals(textValue(shell, "max Y"))
        || !bboxFields.getSelection())
      throw new AssertionError("Clip field values were lost during method changes");

    selectCombo(method, 1);
  }

  private static void verifyClipCommonFieldsEnabled(Shell shell) {
    for (String field :
        List.of(
            "Input raster field",
            "Output raster field (empty: replace input)",
            "Bands (ALL or 1-based list)",
            "no Data")) verifyFieldEnabled(shell, field, true);
  }

  private static void verifySourceControl(Control control) {
    ValueOrFieldControl source = findControl(control, ValueOrFieldControl.class);
    if (source == null) throw new AssertionError("Reader source widget is missing");
    Button browse = findButton(source);
    if (browse == null || !browse.isEnabled())
      throw new AssertionError("Reader source widget must expose an enabled Browse button");
    Control[] children = source.getChildren();
    if (children.length == 0 || !(children[0] instanceof Combo mode))
      throw new AssertionError("Reader source widget must expose its configured/field selector");
    mode.select(1);
    Event selection = new Event();
    selection.type = org.eclipse.swt.SWT.Selection;
    mode.notifyListeners(org.eclipse.swt.SWT.Selection, selection);
    if (source.getValue().mode() != SourceMode.FIELD)
      throw new AssertionError("Reader source field mode could not be selected");
    if (!browse.isVisible() || browse.isEnabled())
      throw new AssertionError("Browse must remain visible but disabled in field mode");
    Combo fieldSelector = findOtherCombo(source, mode);
    if (fieldSelector == null || !fieldSelector.isVisible() || !fieldSelector.isEnabled())
      throw new AssertionError("Field selector must stay visible and enabled in field mode");
    Button refresh = findButtonByText(source, "Refresh");
    if (refresh == null) refresh = findButtonByText(source, "Aktualisieren");
    if (refresh == null || !refresh.isVisible() || !refresh.isEnabled())
      throw new AssertionError("Refresh must stay visible and enabled in field mode");
    source.setValue(new ValueOrField(SourceMode.FIELD, "", "source_path"));
    if (!"source_path".equals(source.getValue().fieldName()))
      throw new AssertionError("Reader source field value was not retained by the widget");
  }

  private static void verifyWriterDialog(Shell dialog, boolean saveByField) {
    ValueOrFieldControl output = findControl(dialog, ValueOrFieldControl.class);
    if (output == null) throw new AssertionError("Writer output widget is missing");
    Button browse = findButton(output);
    if (browse == null || !browse.isVisible())
      throw new AssertionError("Writer output widget must expose a visible Browse button");
    Control[] children = output.getChildren();
    if (children.length == 0 || !(children[0] instanceof Combo mode))
      throw new AssertionError("Writer output widget must expose its value/field selector");
    Combo fieldSelector = findOtherCombo(output, mode);
    Button refresh = findButtonByText(output, "Refresh");
    if (refresh == null) refresh = findButtonByText(output, "Aktualisieren");
    TextVar configuredEditor = findControl(output, TextVar.class);
    if (fieldSelector == null || refresh == null || configuredEditor == null)
      throw new AssertionError("Writer output widget is missing one of its editors");

    var savedOutput = output.getValue();
    for (int width : new int[] {760, 1100, 760}) {
      dialog.setSize(width, 720);
      output.setValue(
          new ValueOrField(
              SourceMode.CONFIGURED, "/very/long/path".repeat(80), "field_".repeat(80)));
      for (int sourceMode : new int[] {0, 1}) {
        selectValueMode(mode, sourceMode);
        dialog.layout(true, true);
        for (Button button : new Button[] {browse, refresh}) {
          if (!button.isVisible()) continue;
          var position = dialog.getDisplay().map(button, dialog, 0, 0);
          if (position.x < 0 || position.x + button.getSize().x > dialog.getClientArea().width)
            throw new AssertionError("Writer button is outside the dialog: " + button.getText());
        }
      }
    }
    output.setValue(savedOutput);

    if (saveByField) {
      if (output.getValue().mode() != SourceMode.FIELD)
        throw new AssertionError("Writer field mode was not restored from outputField metadata");
      if (browse.isEnabled() || !fieldSelector.isEnabled() || !refresh.isEnabled())
        throw new AssertionError("Writer controls are incorrect in field mode");
      selectValueMode(mode, 0);
      if (!browse.isEnabled())
        throw new AssertionError("Writer Browse must be enabled in configured mode");
      configuredEditor.setText("/tmp/unused-raster-output.tif");
      selectValueMode(mode, 1);
      fieldSelector.setText("destination_path");
      if (browse.isEnabled() || !fieldSelector.isEnabled() || !refresh.isEnabled())
        throw new AssertionError(
            "Writer Browse must be disabled while field selection stays active");
      if (!"/tmp/unused-raster-output.tif".equals(output.getValue().configuredValue()))
        throw new AssertionError("Writer configured path was lost after switching modes");
    } else {
      if (output.getValue().mode() != SourceMode.CONFIGURED)
        throw new AssertionError("Writer should initially use a configured output path");
      if (!browse.isEnabled())
        throw new AssertionError("Writer Browse must be enabled in configured mode");
      configuredEditor.setText("/tmp/raster-output.tif");
      selectValueMode(mode, 1);
      fieldSelector.setText("destination_path");
      if (browse.isEnabled() || !fieldSelector.isEnabled() || !refresh.isEnabled())
        throw new AssertionError(
            "Writer Browse must be disabled while field selection stays active");
      selectValueMode(mode, 0);
      if (!browse.isEnabled())
        throw new AssertionError("Writer Browse must be re-enabled in configured mode");
      if (!"destination_path".equals(output.getValue().fieldName()))
        throw new AssertionError("Writer output field value was lost after switching modes");
    }

    Control overwriteEditor = editorAfterLabel(dialog, "Overwrite existing files");
    if (!(overwriteEditor instanceof Button overwrite))
      throw new AssertionError("Writer overwrite setting must be a checkbox");
    overwrite.setSelection(true);
    setTextValue(dialog, "Result field prefix", "written_");

    Control compressionEditor = editorAfterLabel(dialog, "Compression");
    if (!(compressionEditor instanceof Combo compression))
      throw new AssertionError("Writer compression must be a non-editable selection box");
    var expectedCodecs = new java.util.ArrayList<String>();
    expectedCodecs.add("None");
    expectedCodecs.addAll(ch.so.agi.hop.raster.geotools.GeoToolsRasterBackend.compressionTypes());
    if (!Arrays.equals(compression.getItems(), expectedCodecs.toArray(String[]::new)))
      throw new AssertionError("Writer compression choices do not match the TIFF writer");
    if (!"Deflate".equals(compression.getText()))
      throw new AssertionError("Writer compression must default to Deflate");
    int lzw = compression.indexOf("LZW");
    if (lzw < 0) throw new AssertionError("Writer compression is missing LZW");

    Control formatEditor = editorAfterLabel(dialog, "Output format (GEOTIFF / COG)");
    if (!(formatEditor instanceof ComboVar cogFormat))
      throw new AssertionError("Writer format must be a selection box");
    cogFormat.setText("COG");
    cogFormat.getCComboWidget().notifyListeners(org.eclipse.swt.SWT.Modify, new Event());
    var expectedCogCodecs = new java.util.ArrayList<String>();
    expectedCogCodecs.add("None");
    expectedCogCodecs.addAll(
        ch.so.agi.hop.raster.geotools.GeoToolsRasterBackend.cogCompressionTypes());
    if (!Arrays.equals(compression.getItems(), expectedCogCodecs.toArray(String[]::new)))
      throw new AssertionError("COG compression choices do not match the COG writer");
    Button addOverviews = (Button) editorAfterLabel(dialog, "Add overviews");
    if (addOverviews == null || addOverviews.isEnabled() || addOverviews.getSelection())
      throw new AssertionError("COG must disable Add overviews, defaulting to false");
    String overviewsLabel = "Internal overviews (AUTO / NONE)";
    String resamplingLabel = "Overview resampling (AVERAGE / NEAREST)";
    String qualityLabel = "JPEG quality (1-100)";
    Control overviews = editorAfterLabel(dialog, overviewsLabel);
    if (overviews == null || !fieldEnabled(dialog, overviewsLabel, overviews))
      throw new AssertionError("COG must enable internal overviews");
    Control resampling = editorAfterLabel(dialog, resamplingLabel);
    if (resampling == null || !fieldEnabled(dialog, resamplingLabel, resampling))
      throw new AssertionError("COG must enable overview resampling");
    Control quality = editorAfterLabel(dialog, qualityLabel);
    if (quality == null) throw new AssertionError("Writer quality control is missing");
    if (fieldEnabled(dialog, qualityLabel, quality))
      throw new AssertionError("JPEG quality must be disabled for lossless COG codecs");
    int jpeg = compression.indexOf("JPEG");
    if (jpeg < 0) throw new AssertionError("COG compression choices are missing JPEG");
    compression.select(jpeg);
    compression.notifyListeners(org.eclipse.swt.SWT.Selection, new Event());
    if (!fieldEnabled(dialog, qualityLabel, quality))
      throw new AssertionError("JPEG must enable the quality control");
    setTextValue(dialog, qualityLabel, "85");
    compression.select(lzw);
    compression.notifyListeners(org.eclipse.swt.SWT.Selection, new Event());
    if (fieldEnabled(dialog, qualityLabel, quality))
      throw new AssertionError("Quality must be disabled for non-JPEG codecs");
    cogFormat.setText("GEOTIFF");
    cogFormat.getCComboWidget().notifyListeners(org.eclipse.swt.SWT.Modify, new Event());
    if (!Arrays.equals(compression.getItems(), expectedCodecs.toArray(String[]::new)))
      throw new AssertionError("GeoTIFF compression choices were not restored");
    if (fieldEnabled(dialog, overviewsLabel, overviews)
        || fieldEnabled(dialog, resamplingLabel, resampling)
        || fieldEnabled(dialog, qualityLabel, quality))
      throw new AssertionError("GeoTIFF output must disable overview and quality controls");

    if (!addOverviews.isEnabled()) throw new AssertionError("GeoTIFF must enable Add overviews");
    addOverviews.setSelection(true);
    addOverviews.notifyListeners(org.eclipse.swt.SWT.Selection, new Event());
    if (!fieldEnabled(dialog, overviewsLabel, overviews)
        || !fieldEnabled(dialog, resamplingLabel, resampling))
      throw new AssertionError("Add overviews must enable both overview controls");
    compression.select(compression.indexOf("JPEG"));
    compression.notifyListeners(org.eclipse.swt.SWT.Selection, new Event());
    if (!fieldEnabled(dialog, qualityLabel, quality))
      throw new AssertionError("GeoTIFF JPEG must enable quality");
    cogFormat.setText("COG");
    cogFormat.setText("GEOTIFF");
    if (!addOverviews.getSelection() || !"85".equals(((TextVar) quality).getText()))
      throw new AssertionError("Format switches must retain settings");
    compression.select(compression.indexOf("LZW"));
    compression.notifyListeners(org.eclipse.swt.SWT.Selection, new Event());
    if (fieldEnabled(dialog, qualityLabel, quality))
      throw new AssertionError("GeoTIFF LZW must disable quality");
  }

  /**
   * Hop's ComboVar and TextVar only forward setEnabled to their inner widgets, so the label state
   * mirrors the field's enabled flag.
   */
  private static boolean fieldEnabled(Shell dialog, String labelText, Control control) {
    Label label = findLabel(dialog, labelText);
    if (label != null) return label.isEnabled();
    if (control instanceof ComboVar combo) return combo.getCComboWidget().isEnabled();
    return control.isEnabled();
  }

  private static void selectValueMode(Combo mode, int index) {
    mode.select(index);
    Event selection = new Event();
    selection.type = org.eclipse.swt.SWT.Selection;
    mode.notifyListeners(org.eclipse.swt.SWT.Selection, selection);
  }

  private static Combo findOtherCombo(Control control, Combo excluded) {
    if (control instanceof Combo combo && combo != excluded) return combo;
    if (control instanceof org.eclipse.swt.widgets.Composite composite)
      for (Control child : composite.getChildren()) {
        Combo found = findOtherCombo(child, excluded);
        if (found != null) return found;
      }
    return null;
  }

  private static Button findButton(Control control) {
    if (control instanceof Button button
        && (button.getText().startsWith("Browse") || button.getText().startsWith("Durchsuchen")))
      return button;
    if (control instanceof org.eclipse.swt.widgets.Composite composite)
      for (Control child : composite.getChildren()) {
        Button found = findButton(child);
        if (found != null) return found;
      }
    return null;
  }

  private static Button findButtonByText(Control control, String text) {
    if (control instanceof Button button && text.equals(button.getText())) return button;
    if (control instanceof org.eclipse.swt.widgets.Composite composite)
      for (Control child : composite.getChildren()) {
        Button found = findButtonByText(child, text);
        if (found != null) return found;
      }
    return null;
  }

  private static <T> T findControl(Control control, Class<T> type) {
    if (type.isInstance(control)) return type.cast(control);
    if (control instanceof org.eclipse.swt.widgets.Composite composite)
      for (Control child : composite.getChildren()) {
        T found = findControl(child, type);
        if (found != null) return found;
      }
    return null;
  }

  private static ITransformDialog dialog(Shell parent, IVariables variables, DialogCase dialogCase)
      throws Exception {
    var meta = dialogCase.meta();
    var pipeline = new PipelineMeta();
    pipeline.addTransform(new TransformMeta("Raster " + meta.operation(), meta));
    var dialogClass = Class.forName(meta.getDialogClassName());
    var constructor =
        dialogClass.getConstructor(
            Shell.class, IVariables.class, meta.getClass(), PipelineMeta.class);
    return (ITransformDialog) constructor.newInstance(parent, variables, meta, pipeline);
  }

  private static void cancelWriterWhenOpened(Display display, Shell parent) {
    display.timerExec(
        100,
        () -> {
          Shell shell =
              Arrays.stream(parent.getShells())
                  .filter(s -> !s.isDisposed() && s.isVisible())
                  .findFirst()
                  .orElse(null);
          if (shell == null) {
            cancelWriterWhenOpened(display, parent);
            return;
          }
          ((Button) editorAfterLabel(shell, "Add overviews")).setSelection(true);
          setTextValue(shell, "JPEG quality (1-100)", "33");
          findButtonByText(shell, "Cancel")
              .notifyListeners(org.eclipse.swt.SWT.Selection, new Event());
        });
  }

  public static void main(String[] args) throws Exception {
    HopEnvironment.init();
    Display display = new Display();
    Shell parent = new Shell(display);
    try {
      var variables = new Variables();
      List<DialogCase> cases =
          List.of(
              new DialogCase(new RasterReaderMeta(), "Reader output field", true),
              new DialogCase(new RasterClipMeta(), "Input raster field", false),
              new DialogCase(new RasterReprojectMeta(), "Input raster field", false),
              new DialogCase(new RasterZonalStatsMeta(), "Input raster field", false),
              new DialogCase(new RasterInfoMeta(), "Input raster field", false),
              new DialogCase(new RasterWriterMeta(), "Input raster field", false),
              writerFieldCase());

      for (var dialogCase : cases) {
        String title = "Raster " + dialogCase.meta().operation() + " (GeoTools)";
        whenOpened(display, parent, title, dialogCase);
        dialog(parent, variables, dialogCase).open();
        if (dialogCase.reader()) {
          var reader = (RasterReaderMeta) dialogCase.meta();
          if (!reader.isSourceField() || !"source_path".equals(reader.getSource()))
            throw new AssertionError("Reader source selection was not saved to source/sourceField");
          if (!"reader_raster".equals(reader.getRasterField()))
            throw new AssertionError("Reader output field was not saved");
        } else if (dialogCase.meta().operation().equals("CLIP")) {
          var clip = (RasterClipMeta) dialogCase.meta();
          if (!"BOUNDING_BOX".equals(clip.getClipMethod())
              || !"mask_geom".equals(clip.getGeometryField())
              || !"EPSG:2056".equals(clip.getExplicitCrs())
              || !"1".equals(clip.getMinX())
              || !"2".equals(clip.getMinY())
              || !"3".equals(clip.getMaxX())
              || !"4".equals(clip.getMaxY())
              || !clip.isBboxFields())
            throw new AssertionError("Clip method or field values were not saved correctly");
        } else if (dialogCase.meta().operation().equals("WRITER")) {
          var writer = (RasterWriterMeta) dialogCase.meta();
          String expectedOutput =
              writer.isOutputField() ? "destination_path" : "/tmp/raster-output.tif";
          if (!expectedOutput.equals(writer.getOutput())
              || !writer.isOverwrite()
              || !"written_".equals(writer.getPrefix())
              || !"LZW".equals(writer.getCompression())
              || writer.getJpegQuality() != 85
              || !writer.isAddOverviews())
            throw new AssertionError("Writer output mode or settings were not saved correctly");
        }
      }
      var unchanged = new RasterWriterMeta();
      unchanged.setOutput("/tmp/unchanged.tif");
      String originalXml = unchanged.getXml();
      cancelWriterWhenOpened(display, parent);
      dialog(parent, variables, new DialogCase(unchanged, "Input raster field", false)).open();
      if (!originalXml.equals(unchanged.getXml()))
        throw new AssertionError("Cancel changed writer metadata");
    } finally {
      parent.dispose();
      display.dispose();
    }
  }

  private static DialogCase writerFieldCase() {
    var writer = new RasterWriterMeta();
    writer.setOutput("destination_path");
    writer.setOutputField(true);
    return new DialogCase(writer, "Input raster field", false);
  }
}

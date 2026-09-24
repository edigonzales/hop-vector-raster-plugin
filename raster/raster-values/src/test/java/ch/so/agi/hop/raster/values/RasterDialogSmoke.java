package ch.so.agi.hop.raster.values;

import java.util.Arrays;
import java.util.List;
import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import ch.so.agi.hop.commons.ui.ValueOrFieldControl;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformDialog;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;

/** Opens each raster dialog through the constructor contract used by Hop 2.19. */
public final class RasterDialogSmoke {
  private record DialogCase(RasterValueMeta meta, String expectedLabel, boolean reader) {}

  private RasterDialogSmoke() {}

  private static void whenOpened(Display display, Shell parent, String title, DialogCase dialogCase) {
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
    if (control instanceof Label label
        && expected.equals(label.getText())) return true;
    if (control instanceof org.eclipse.swt.widgets.Composite composite)
      for (var child : composite.getChildren())
        if (containsLabel(child, expected)) return true;
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
          "Expected " + labelText + " enabled=" + expected + " but editor="
              + controlTreeEnabled(editor, true) + ", label=" + label.getEnabled());
  }

  private static boolean controlTreeEnabled(Control control, boolean expected) {
    if (control instanceof TextVar text && text.getTextWidget().getEnabled() != expected)
      return false;
    if (control instanceof ComboVar combo && combo.getCComboWidget().getEnabled() != expected)
      return false;
    if (!(control instanceof TextVar) && !(control instanceof ComboVar)
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
    for (String field : List.of("min X", "min Y", "max X", "max Y", "bbox Fields"))
      verifyFieldEnabled(shell, field, false);

    setTextValue(shell, "geometry Field", "mask_geom");
    setTextValue(shell, "explicit Crs", "EPSG:2056");
    setTextValue(shell, "min X", "1");
    setTextValue(shell, "min Y", "2");
    setTextValue(shell, "max X", "3");
    setTextValue(shell, "max Y", "4");
    Control bboxFieldsEditor = editorAfterLabel(shell, "bbox Fields");
    if (!(bboxFieldsEditor instanceof Button bboxFields))
      throw new AssertionError("bbox Fields must be a checkbox");
    bboxFields.setSelection(true);

    selectCombo(method, 1);
    if (!"BOUNDING_BOX".equals(method.getText()))
      throw new AssertionError("Clip method selection did not change to BOUNDING_BOX");
    verifyClipCommonFieldsEnabled(shell);
    verifyFieldEnabled(shell, "geometry Field", false);
    verifyFieldEnabled(shell, "explicit Crs", true);
    for (String field : List.of("min X", "min Y", "max X", "max Y", "bbox Fields"))
      verifyFieldEnabled(shell, field, true);

    selectCombo(method, 0);
    if (!"POLYGON".equals(method.getText()))
      throw new AssertionError("Clip method selection did not change back to POLYGON");
    verifyFieldEnabled(shell, "geometry Field", true);
    verifyClipCommonFieldsEnabled(shell);
    for (String field : List.of("min X", "min Y", "max X", "max Y", "bbox Fields"))
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

  private static ITransformDialog dialog(
      Shell parent, IVariables variables, DialogCase dialogCase) throws Exception {
    var meta = dialogCase.meta();
    var pipeline = new PipelineMeta();
    pipeline.addTransform(new TransformMeta("Raster " + meta.operation(), meta));
    var dialogClass = Class.forName(meta.getDialogClassName());
    var constructor =
        dialogClass.getConstructor(
            Shell.class, IVariables.class, meta.getClass(), PipelineMeta.class);
    return (ITransformDialog) constructor.newInstance(parent, variables, meta, pipeline);
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
              new DialogCase(
                  new RasterReprojectMeta(), "Input raster field", false),
              new DialogCase(
                  new RasterZonalStatsMeta(), "Input raster field", false),
              new DialogCase(new RasterInfoMeta(), "Input raster field", false),
              new DialogCase(new RasterWriterMeta(), "Input raster field", false));

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
        }
      }
    } finally {
      parent.dispose();
      display.dispose();
    }
  }
}

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
    source.setValue(new ValueOrField(SourceMode.FIELD, "", "source_path"));
    if (!"source_path".equals(source.getValue().fieldName()))
      throw new AssertionError("Reader source field value was not retained by the widget");
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
        }
      }
    } finally {
      parent.dispose();
      display.dispose();
    }
  }
}

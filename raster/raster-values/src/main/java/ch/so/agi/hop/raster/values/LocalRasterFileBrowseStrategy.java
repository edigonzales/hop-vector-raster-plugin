package ch.so.agi.hop.raster.values;

import ch.so.agi.hop.commons.ui.BrowseStrategy;
import ch.so.agi.hop.commons.ui.EditorKind;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

/** Uses native local file dialogs for raster input and output paths. */
final class LocalRasterFileBrowseStrategy implements BrowseStrategy {
  interface NativeFileDialog {
    void setFilterExtensions(String[] extensions);

    void setFilterNames(String[] names);

    void setFilterPath(String path);

    void setFileName(String name);

    String open();
  }

  @FunctionalInterface
  interface NativeFileDialogFactory {
    NativeFileDialog create(Shell shell, int style);
  }

  private final NativeFileDialogFactory dialogFactory;

  LocalRasterFileBrowseStrategy() {
    this(
        (shell, style) -> {
          FileDialog dialog = new FileDialog(shell, style);
          return new NativeFileDialog() {
            @Override
            public void setFilterExtensions(String[] extensions) {
              dialog.setFilterExtensions(extensions);
            }

            @Override
            public void setFilterNames(String[] names) {
              dialog.setFilterNames(names);
            }

            @Override
            public void setFilterPath(String path) {
              dialog.setFilterPath(path);
            }

            @Override
            public void setFileName(String name) {
              dialog.setFileName(name);
            }

            @Override
            public String open() {
              return dialog.open();
            }
          };
        });
  }

  LocalRasterFileBrowseStrategy(NativeFileDialogFactory dialogFactory) {
    this.dialogFactory = dialogFactory;
  }

  @Override
  public Optional<String> browse(
      Shell shell,
      IVariables variables,
      String currentValue,
      EditorKind editor,
      String[] filterExtensions,
      String[] filterNames)
      throws HopException {
    int style =
        switch (editor) {
          case FILE_OPEN -> SWT.OPEN;
          case FILE_SAVE -> SWT.SAVE;
          default -> throw new HopException("Raster file browsing requires a file editor");
        };

    NativeFileDialog dialog = dialogFactory.create(shell, style);
    dialog.setFilterExtensions(filterExtensions);
    dialog.setFilterNames(filterNames);
    setInitialPath(dialog, variables, currentValue);
    return Optional.ofNullable(dialog.open());
  }

  private static void setInitialPath(
      NativeFileDialog dialog, IVariables variables, String currentValue) {
    if (currentValue == null || currentValue.isBlank()) return;
    String resolved = variables.resolve(currentValue).trim();
    if (resolved.isEmpty()
        || resolved.contains("${")
        || resolved.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) return;

    try {
      Path path = Path.of(resolved);
      Path fileName = path.getFileName();
      Path parent = path.getParent();
      if (parent != null) dialog.setFilterPath(parent.toString());
      if (fileName != null) dialog.setFileName(fileName.toString());
    } catch (InvalidPathException ignored) {
      // Keep the chooser local and let the user select a valid path.
    }
  }
}

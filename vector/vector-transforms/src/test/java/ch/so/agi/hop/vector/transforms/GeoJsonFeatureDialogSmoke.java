package ch.so.agi.hop.vector.transforms;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

/** Native SWT cancellation/commit checks, launched by the headless dialog test wrapper. */
public final class GeoJsonFeatureDialogSmoke {
  public static void main(String[] args) throws Exception {
    HopEnvironment.init();
    Display display = new Display();
    Shell parent = new Shell(display);
    try {
      for (boolean decoder : new boolean[] {true, false})
        for (boolean accept : new boolean[] {false, true}) check(display, parent, decoder, accept);
    } finally {
      parent.dispose();
      display.dispose();
    }
  }

  private static void check(Display display, Shell parent, boolean decoder, boolean accept)
      throws Exception {
    GeoJsonFeatureMeta<?, ?> meta =
        decoder ? new GeoJsonFeatureDecoderMeta() : new GeoJsonFeatureEncoderMeta();
    meta.setGeometryField("${ORIGINAL}");
    meta.setChanged(false);
    String xml = meta.getXml();
    var pipeline = new PipelineMeta();
    pipeline.addTransform(new TransformMeta("Feature", meta));
    var variables = new Variables();
    variables.setVariable("ORIGINAL", "shape");
    variables.setVariable("EDITED", "reprojected");
    GeoJsonFeatureDialog dialog =
        decoder
            ? new GeoJsonFeatureDecoderDialog(
                parent, variables, (GeoJsonFeatureDecoderMeta) meta, pipeline)
            : new GeoJsonFeatureEncoderDialog(
                parent, variables, (GeoJsonFeatureEncoderMeta) meta, pipeline);
    var failure = new AtomicReference<Throwable>();
    whenOpen(
        display,
        parent,
        () -> {
          try {
            Shell shell =
                java.util.Arrays.stream(parent.getShells())
                    .filter(s -> s.isVisible() && !s.isDisposed())
                    .findFirst()
                    .orElseThrow();
            var children = shell.getChildren();
            boolean edited = false;
            for (int i = 0; i < children.length - 1; i++)
              if (children[i] instanceof Label label
                  && "Native Geometry field".equals(label.getText())
                  && children[i + 1] instanceof TextVar text) {
                text.setText("${EDITED}");
                edited = true;
              }
            if (!edited) throw new AssertionError("Native Geometry control is missing");
            for (Control child : children)
              if (child instanceof Button button
                  && (accept ? "OK" : "Cancel").equals(button.getText())) {
                button.notifyListeners(SWT.Selection, new Event());
                return;
              }
            throw new AssertionError("Dialog button is missing");
          } catch (Throwable e) {
            failure.set(e);
            for (Shell shell : parent.getShells()) if (!shell.isDisposed()) shell.dispose();
          }
        });
    String result = dialog.open();
    if (failure.get() != null)
      throw new AssertionError("Native GeoJSON dialog failed", failure.get());
    if (accept) {
      if (result == null || !meta.getGeometryField().equals("${EDITED}") || !meta.hasChanged())
        throw new AssertionError("OK did not commit the variable expression");
    } else if (result != null || !meta.getXml().equals(xml) || meta.hasChanged())
      throw new AssertionError("Cancel changed metadata");
  }

  private static void whenOpen(Display display, Shell parent, Runnable action) {
    display.timerExec(
        100,
        () -> {
          boolean opening =
              java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                  .anyMatch(
                      frame ->
                          frame.getClassName().equals(Shell.class.getName())
                              && frame.getMethodName().equals("open"));
          if (opening
              || java.util.Arrays.stream(parent.getShells())
                  .noneMatch(s -> !s.isDisposed() && s.isVisible()))
            whenOpen(display, parent, action);
          else action.run();
        });
  }
}

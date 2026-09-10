package ch.so.agi.hop.raster.reproject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

public class RasterReprojectDialog extends BaseTransformDialog {
  private final RasterReprojectMeta input;
  private final List<Consumer<RasterReprojectMeta>> bindings = new ArrayList<>();
  private String[] fields = new String[0];

  public RasterReprojectDialog(
      Shell parent, IVariables variables, RasterReprojectMeta meta, PipelineMeta pipeline) {
    super(parent, variables, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    bindings.clear();
    try {
      fields = pipelineMeta.getPrevTransformFields(variables, transformName).getFieldNames();
    } catch (Exception ignored) {
      fields = new String[0];
    }
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("Raster Reproject / Resample (GeoTools)");
    shell.setLayout(new GridLayout(1, false));
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    var scroll = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL);
    scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);
    var body = new Composite(scroll, SWT.NONE);
    body.setLayout(new GridLayout(1, false));
    PropsUi.setLook(body);
    var name = new Composite(body, SWT.NONE);
    name.setLayout(new GridLayout(2, false));
    name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(name, SWT.NONE).setText("Transform name");
    wTransformName = new Text(name, SWT.BORDER);
    wTransformName.setText(transformName);
    wTransformName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    var source = group(body, "Input");
    file(
        source,
        "Raster (local GeoTIFF or public COG URL)",
        input.getSource(),
        RasterReprojectMeta::setSource,
        false);
    flag(
        source,
        "Raster value is an input field",
        input.isSourceField(),
        RasterReprojectMeta::setSourceField);
    text(
        source,
        "Source NoData override (empty: metadata)",
        input.getSourceNoData(),
        RasterReprojectMeta::setSourceNoData);

    var target = group(body, "Target grid");
    text(
        target,
        "Target CRS (empty: source CRS)",
        input.getTargetCrs(),
        RasterReprojectMeta::setTargetCrs);
    flag(
        target,
        "Target CRS is an input field",
        input.isTargetCrsField(),
        RasterReprojectMeta::setTargetCrsField);
    text(
        target,
        "Pixel size X (target CRS units)",
        input.getResolutionX(),
        RasterReprojectMeta::setResolutionX);
    text(
        target,
        "Pixel size Y (target CRS units)",
        input.getResolutionY(),
        RasterReprojectMeta::setResolutionY);
    flag(
        target,
        "Pixel sizes are input fields",
        input.isResolutionFields(),
        RasterReprojectMeta::setResolutionFields);
    Combo extent =
        choice(
            target,
            "Extent",
            new String[] {"AUTO", "BOUNDING_BOX"},
            input.getExtentMode(),
            RasterReprojectMeta::setExtentMode);
    var bbox = new Composite(target, SWT.NONE);
    bbox.setLayout(new GridLayout(2, false));
    bbox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    text(bbox, "Minimum X", input.getMinX(), RasterReprojectMeta::setMinX);
    text(bbox, "Minimum Y", input.getMinY(), RasterReprojectMeta::setMinY);
    text(bbox, "Maximum X", input.getMaxX(), RasterReprojectMeta::setMaxX);
    text(bbox, "Maximum Y", input.getMaxY(), RasterReprojectMeta::setMaxY);
    flag(
        bbox,
        "Bounding box values are input fields",
        input.isBboxFields(),
        RasterReprojectMeta::setBboxFields);
    Runnable enableBox =
        () -> {
          for (Control c : bbox.getChildren())
            c.setEnabled("BOUNDING_BOX".equals(extent.getText()));
        };
    extent.addListener(SWT.Selection, e -> enableBox.run());
    enableBox.run();
    note(target, "XY / lon-lat order. Extent expands to pixel-size multiples of the CRS origin.");

    var sampling = group(body, "Resampling");
    choice(
        sampling,
        "Interpolation",
        new String[] {"NEAREST", "BILINEAR"},
        input.getInterpolation(),
        RasterReprojectMeta::setInterpolation);
    choice(
        sampling,
        "Numeric output type",
        new String[] {"AUTO", "SOURCE", "FLOAT32", "FLOAT64"},
        input.getOutputType(),
        RasterReprojectMeta::setOutputType);
    note(
        sampling,
        "AUTO: Nearest keeps the sample type; Bilinear uses Float64. Integer ties round to even.\n"
            + "Colors keep their type. Bilinear palettes become UInt16 RGBA; Nearest retains"
            + " indices.");

    var output = group(body, "Output");
    file(output, "Output GeoTIFF", input.getOutput(), RasterReprojectMeta::setOutput, true);
    flag(
        output,
        "Output path is an input field",
        input.isOutputField(),
        RasterReprojectMeta::setOutputField);
    text(
        output,
        "Output NoData (numeric / palette only)",
        input.getOutputNoData(),
        RasterReprojectMeta::setOutputNoData);
    flag(output, "Overwrite existing file", input.isOverwrite(), RasterReprojectMeta::setOverwrite);
    text(output, "Output field prefix", input.getPrefix(), RasterReprojectMeta::setPrefix);
    note(
        output,
        "All bands are processed. One input row writes one file; use unique paths per row/copy.");

    scroll.setContent(body);
    scroll.setMinSize(body.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    var buttons = new Composite(shell, SWT.NONE);
    buttons.setLayout(new GridLayout(2, true));
    buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
    var ok = new Button(buttons, SWT.PUSH);
    ok.setText("OK");
    ok.addListener(SWT.Selection, e -> ok());
    var cancel = new Button(buttons, SWT.PUSH);
    cancel.setText("Cancel");
    cancel.addListener(SWT.Selection, e -> cancel());
    shell.setDefaultButton(ok);
    shell.addListener(
        SWT.Close,
        e -> {
          e.doit = false;
          cancel();
        });
    var area = getParent().getMonitor().getClientArea();
    int width = Math.min(800, area.width - 80), height = Math.min(780, area.height - 80);
    shell.setSize(width, height);
    shell.setLocation(area.x + (area.width - width) / 2, area.y + (area.height - height) / 2);
    shell.open();
    while (!shell.isDisposed())
      if (!shell.getDisplay().readAndDispatch()) shell.getDisplay().sleep();
    return transformName;
  }

  private Group group(Composite parent, String title) {
    var group = new Group(parent, SWT.NONE);
    group.setText(title);
    group.setLayout(new GridLayout(2, false));
    group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    PropsUi.setLook(group);
    return group;
  }

  private ComboVar text(
      Composite parent,
      String label,
      String value,
      BiConsumer<RasterReprojectMeta, String> setter) {
    new Label(parent, SWT.NONE).setText(label);
    var control = new ComboVar(variables, parent, SWT.BORDER);
    control.setItems(fields);
    control.setText(value);
    control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    bindings.add(meta -> setter.accept(meta, control.getText()));
    return control;
  }

  private void file(
      Composite parent,
      String label,
      String value,
      BiConsumer<RasterReprojectMeta, String> setter,
      boolean save) {
    new Label(parent, SWT.NONE).setText(label);
    var composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(2, false));
    composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    var control = new ComboVar(variables, composite, SWT.BORDER);
    control.setItems(fields);
    control.setText(value);
    control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    bindings.add(meta -> setter.accept(meta, control.getText()));
    var browse = new Button(composite, SWT.PUSH);
    browse.setText("Browse…");
    browse.addListener(
        SWT.Selection,
        e -> {
          var dialog = new FileDialog(shell, save ? SWT.SAVE : SWT.OPEN);
          dialog.setFilterExtensions(new String[] {"*.tif;*.tiff", "*.*"});
          String selected = dialog.open();
          if (selected != null) control.setText(selected);
        });
  }

  private void flag(
      Composite parent,
      String label,
      boolean value,
      BiConsumer<RasterReprojectMeta, Boolean> setter) {
    var control = new Button(parent, SWT.CHECK);
    control.setText(label);
    control.setSelection(value);
    control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    bindings.add(meta -> setter.accept(meta, control.getSelection()));
  }

  private Combo choice(
      Composite parent,
      String label,
      String[] choices,
      String value,
      BiConsumer<RasterReprojectMeta, String> setter) {
    new Label(parent, SWT.NONE).setText(label);
    var control = new Combo(parent, SWT.READ_ONLY);
    control.setItems(choices);
    control.setText(value);
    control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    bindings.add(meta -> setter.accept(meta, control.getText()));
    return control;
  }

  private void note(Composite parent, String value) {
    var control = new Label(parent, SWT.WRAP);
    control.setText(value);
    var layout = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
    layout.widthHint = 650;
    control.setLayoutData(layout);
  }

  private void ok() {
    try {
      if (wTransformName.getText().isBlank())
        throw new IllegalArgumentException("Transform name is required");
      var draft = new RasterReprojectMeta();
      for (var binding : bindings) binding.accept(draft);
      draft.validateSettings();
      input.copyFrom(draft);
      input.setChanged();
      transformName = wTransformName.getText();
      dispose();
    } catch (RuntimeException e) {
      var box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      box.setText("Invalid settings");
      box.setMessage(e.getMessage() == null ? "Invalid settings" : e.getMessage());
      box.open();
    }
  }

  private void cancel() {
    transformName = null;
    dispose();
  }
}

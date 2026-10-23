package ch.so.agi.hop.vector.transforms;

import java.util.*;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Edits a private draft. Metadata is changed only by the OK handler. */
public abstract class GeoJsonFeatureDialog extends BaseTransformDialog {
  private final GeoJsonFeatureMeta<?, ?> input;

  protected GeoJsonFeatureDialog(
      Shell parent, IVariables variables, GeoJsonFeatureMeta<?, ?> meta, PipelineMeta pipeline) {
    super(parent, variables, meta, pipeline);
    input = meta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setText("GeoJSON Feature " + (input.decoder() ? "Decoder" : "Encoder"));
    shell.setLayout(new GridLayout(2, false));
    new Label(shell, SWT.NONE).setText("Transform name");
    Text name = new Text(shell, SWT.BORDER);
    name.setText(transformName);
    fill(name);
    Map<String, TextVar> fields = new LinkedHashMap<>();
    field(fields, "input", "Feature JSON input field", input.getInputField(), input.decoder());
    field(fields, "output", "Feature JSON output field", input.getOutputField(), !input.decoder());
    new Label(shell, SWT.NONE).setText("Format profile");
    Combo profile = new Combo(shell, SWT.READ_ONLY);
    profile.setItems("AUTO", "RFC7946", "LEGACY2008", "JSONFG10");
    profile.setText(input.getProfile());
    fill(profile);
    field(fields, "geometry", "Native Geometry field", input.getGeometryField(), true);
    field(
        fields,
        "fallback",
        input.decoder() ? "WGS84 fallback output field" : "WGS84 fallback field (blank disables)",
        input.getFallbackField(),
        true);
    field(fields, "properties", "Properties JSON field", input.getPropertiesField(), true);
    field(fields, "id", "Feature ID JSON field", input.getIdField(), true);
    field(fields, "context", "Context JSON field", input.getContextField(), true);
    new Label(shell, SWT.NONE).setText("Property mappings (JSON)");
    TextVar mappings =
        new TextVar(variables, shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
    mappings.setText(input.getMappingsJson());
    GridData mappingLayout = new GridData(SWT.FILL, SWT.FILL, true, true);
    mappingLayout.heightHint = 100;
    mappings.setLayoutData(mappingLayout);
    new Label(shell, SWT.NONE).setText("On data error");
    Combo errors = new Combo(shell, SWT.READ_ONLY);
    errors.setItems("FAIL", "RETURN_NULL", "ROUTE_TO_ERROR");
    errors.setText(input.getOnDataError());
    fill(errors);
    Button dropZ = input.decoder() ? null : check("Explicitly drop Z", input.isDropZ()),
        dropM = input.decoder() ? null : check("Explicitly drop M", input.isDropM()),
        empty =
            input.decoder()
                ? null
                : check(
                    "Explicitly replace unrepresentable EMPTY with null", input.isEmptyToNull());
    new Label(shell, SWT.WRAP)
        .setText("No automatic reprojection. Use Geometry Reproject before encoding.");
    new Label(shell, SWT.NONE);
    Button ok = new Button(shell, SWT.PUSH);
    ok.setText("OK");
    Button cancel = new Button(shell, SWT.PUSH);
    cancel.setText("Cancel");
    ok.addListener(
        SWT.Selection,
        e -> {
          try {
            GeoJsonFeatureMeta<?, ?> draft = (GeoJsonFeatureMeta<?, ?>) input.clone();
            if (input.decoder()) draft.setInputField(fields.get("input").getText());
            else draft.setOutputField(fields.get("output").getText());
            draft.setProfile(profile.getText());
            draft.setGeometryField(fields.get("geometry").getText());
            draft.setFallbackField(fields.get("fallback").getText());
            draft.setPropertiesField(fields.get("properties").getText());
            draft.setIdField(fields.get("id").getText());
            draft.setContextField(fields.get("context").getText());
            draft.setMappingsJson(mappings.getText());
            draft.setOnDataError(errors.getText());
            if (!input.decoder()) {
              draft.setDropZ(dropZ.getSelection());
              draft.setDropM(dropM.getSelection());
              draft.setEmptyToNull(empty.getSelection());
            }
            // Resolve only for validation; the original variable expressions are committed
            // unchanged.
            draft.options(variables);
            draft.errorMode();
            copySettings(draft, input);
            input.setChanged();
            transformName = name.getText();
            shell.dispose();
          } catch (Exception ex) {
            MessageBox message = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
            message.setText("Invalid GeoJSON settings");
            message.setMessage(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            message.open();
          }
        });
    cancel.addListener(
        SWT.Selection,
        e -> {
          transformName = null;
          shell.dispose();
        });
    shell.addListener(SWT.Close, e -> transformName = null);
    shell.setDefaultButton(ok);
    shell.setSize(800, 780);
    Display display = shell.getDisplay();
    shell.open();
    while (!shell.isDisposed())
      if (!display.readAndDispatch() && !shell.isDisposed()) display.sleep();
    return transformName;
  }

  private void field(
      Map<String, TextVar> fields, String key, String label, String value, boolean enabled) {
    if (!enabled) return;
    new Label(shell, SWT.NONE).setText(label);
    var text = new TextVar(variables, shell, SWT.BORDER);
    text.setText(value);
    fill(text);
    fields.put(key, text);
  }

  private Button check(String label, boolean value) {
    new Label(shell, SWT.NONE);
    Button b = new Button(shell, SWT.CHECK);
    b.setText(label);
    b.setSelection(value);
    return b;
  }

  private static void fill(Control control) {
    control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
  }

  static void copySettings(GeoJsonFeatureMeta<?, ?> source, GeoJsonFeatureMeta<?, ?> target) {
    target.setInputField(source.getInputField());
    target.setOutputField(source.getOutputField());
    target.setProfile(source.getProfile());
    target.setGeometryField(source.getGeometryField());
    target.setFallbackField(source.getFallbackField());
    target.setPropertiesField(source.getPropertiesField());
    target.setIdField(source.getIdField());
    target.setContextField(source.getContextField());
    target.setMappingsJson(source.getMappingsJson());
    target.setOnDataError(source.getOnDataError());
    target.setDropZ(source.isDropZ());
    target.setDropM(source.isDropM());
    target.setEmptyToNull(source.isEmptyToNull());
  }
}

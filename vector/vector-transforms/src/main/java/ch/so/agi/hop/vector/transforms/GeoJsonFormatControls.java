package ch.so.agi.hop.vector.transforms;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

/** Shared GeoJSON settings UI. Writes only to the caller's draft when requested. */
final class GeoJsonFormatControls extends Composite {
  private final Combo profile;
  private final TextVar fallback, properties, id, context, mappings;
  private final Button dropZ, dropM, emptyToNull;

  GeoJsonFormatControls(Composite parent, IVariables variables) {
    super(parent, SWT.NONE);
    setLayout(new GridLayout(2, false));
    new Label(this, SWT.NONE).setText("GeoJSON profile");
    profile = new Combo(this, SWT.READ_ONLY);
    profile.setItems(new String[] {"AUTO", "RFC7946", "LEGACY2008", "JSONFG10"});
    fallback = text(variables, "WGS84 fallback geometry field");
    properties = text(variables, "Properties JSON field");
    id = text(variables, "Feature ID JSON field");
    context = text(variables, "GeoJSON context JSON field");
    mappings = text(variables, "Properties mappings (JSON array)");
    mappings.setToolTipText(
        "[{\"pointer\":\"/name\",\"field\":\"name\",\"type\":\"STRING\",\"writeNull\":false}]");
    dropZ = check("Explicitly discard Z");
    dropM = check("Explicitly discard M");
    emptyToNull = check("Explicitly replace unrepresentable EMPTY with null");
  }

  private TextVar text(IVariables variables, String label) {
    new Label(this, SWT.NONE).setText(label);
    TextVar control = new TextVar(variables, this, SWT.BORDER);
    control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return control;
  }

  private Button check(String label) {
    new Label(this, SWT.NONE).setText(label);
    Button b = new Button(this, SWT.CHECK);
    return b;
  }

  void onChange(Runnable callback) {
    for (TextVar control : new TextVar[] {fallback, properties, id, context, mappings})
      control.addModifyListener(event -> callback.run());
    profile.addListener(SWT.Selection, event -> callback.run());
    for (Button control : new Button[] {dropZ, dropM, emptyToNull})
      control.addListener(SWT.Selection, event -> callback.run());
  }

  void load(VectorReaderMeta meta) {
    dropZ.setEnabled(false);
    dropM.setEnabled(false);
    emptyToNull.setEnabled(false);
    profile.setText(meta.getGeoJsonProfile());
    fallback.setText(meta.getGeoJsonFallbackField());
    properties.setText(meta.getGeoJsonPropertiesField());
    id.setText(meta.getGeoJsonIdField());
    context.setText(meta.getGeoJsonContextField());
    mappings.setText(meta.getGeoJsonMappings());
    dropZ.setSelection(meta.isGeoJsonDropZ());
    dropM.setSelection(meta.isGeoJsonDropM());
    emptyToNull.setSelection(meta.isGeoJsonEmptyToNull());
  }

  void save(VectorReaderMeta meta) {
    meta.setGeoJsonProfile(profile.getText());
    meta.setGeoJsonFallbackField(fallback.getText());
    meta.setGeoJsonPropertiesField(properties.getText());
    meta.setGeoJsonIdField(id.getText());
    meta.setGeoJsonContextField(context.getText());
    meta.setGeoJsonMappings(mappings.getText());
    meta.setGeoJsonDropZ(dropZ.getSelection());
    meta.setGeoJsonDropM(dropM.getSelection());
    meta.setGeoJsonEmptyToNull(emptyToNull.getSelection());
  }

  void load(VectorWriterMeta meta) {
    profile.setText(meta.getGeoJsonProfile());
    fallback.setText(meta.getGeoJsonFallbackField());
    properties.setText(meta.getGeoJsonPropertiesField());
    id.setText(meta.getGeoJsonIdField());
    context.setText(meta.getGeoJsonContextField());
    mappings.setText(meta.getGeoJsonMappings());
    dropZ.setSelection(meta.isGeoJsonDropZ());
    dropM.setSelection(meta.isGeoJsonDropM());
    emptyToNull.setSelection(meta.isGeoJsonEmptyToNull());
  }

  void save(VectorWriterMeta meta) {
    meta.setGeoJsonProfile(profile.getText());
    meta.setGeoJsonFallbackField(fallback.getText());
    meta.setGeoJsonPropertiesField(properties.getText());
    meta.setGeoJsonIdField(id.getText());
    meta.setGeoJsonContextField(context.getText());
    meta.setGeoJsonMappings(mappings.getText());
    meta.setGeoJsonDropZ(dropZ.getSelection());
    meta.setGeoJsonDropM(dropM.getSelection());
    meta.setGeoJsonEmptyToNull(emptyToNull.getSelection());
  }
}

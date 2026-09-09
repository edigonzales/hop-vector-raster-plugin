package ch.so.agi.hop.vector.transforms;

import org.apache.hop.metadata.api.HopMetadataProperty;

public class ShapefileFieldMeta {
  @HopMetadataProperty private String source = "";
  @HopMetadataProperty private String target = "";
  @HopMetadataProperty private int width = -1;
  @HopMetadataProperty private int scale = -1;

  public ShapefileFieldMeta() {}

  public ShapefileFieldMeta(String source, String target, int width, int scale) {
    this.source = source;
    this.target = target;
    this.width = width;
    this.scale = scale;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String v) {
    source = v;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String v) {
    target = v;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int v) {
    width = v;
  }

  public int getScale() {
    return scale;
  }

  public void setScale(int v) {
    scale = v;
  }
}

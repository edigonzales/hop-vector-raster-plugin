package ch.so.agi.hop.vector.transforms;

import java.util.*;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.pipeline.transform.*;
import org.apache.hop.pipeline.transform.stream.*;

@Transform(
    id = "SOGIS_FILEGDB_WRITER",
    name = "FileGDB Writer",
    description = "Export multiple tables, domains and relationships to one new FileGDB",
    image = "ch/so/agi/hop/vector/transforms/icons/vector-writer.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public class FileGdbWriterMeta extends BaseTransformMeta<FileGdbWriter, FileGdbWriterData> {
  @HopMetadataProperty private String fileName = "";
  @HopMetadataProperty private String schemaFile = "";

  @HopMetadataProperty(groupKey = "inputs", key = "input")
  private List<Input> inputs = new ArrayList<>();

  public static class Input {
    @HopMetadataProperty private String dataset = "";
    @HopMetadataProperty private String transform = "";

    public Input() {}

    public Input(String dataset, String transform) {
      this.dataset = dataset;
      this.transform = transform;
    }

    public String getDataset() {
      return dataset;
    }

    public void setDataset(String v) {
      dataset = v;
    }

    public String getTransform() {
      return transform;
    }

    public void setTransform(String v) {
      transform = v;
    }
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String v) {
    fileName = v;
  }

  public String getSchemaFile() {
    return schemaFile;
  }

  public void setSchemaFile(String v) {
    schemaFile = v;
  }

  public List<Input> getInputs() {
    return inputs;
  }

  public void setInputs(List<Input> v) {
    inputs = v;
    resetTransformIoMeta();
  }

  @Override
  public ITransformIOMeta getTransformIOMeta() {
    var io = super.getTransformIOMeta(false);
    if (io == null) {
      io = new TransformIOMeta(true, false, true, false, false, false);
      for (var input : inputs)
        io.addStream(
            new Stream(
                IStream.StreamType.INFO, null, input.dataset, StreamIcon.INFO, input.transform));
      setTransformIOMeta(io);
    }
    return io;
  }

  @Override
  public void searchInfoAndTargetTransforms(List<TransformMeta> transforms) {
    for (var stream : getTransformIOMeta().getInfoStreams())
      stream.setTransformMeta(TransformMeta.findTransform(transforms, stream.getSubject()));
  }

  @Override
  public void convertIOMetaToTransformNames() {
    var streams = getTransformIOMeta().getInfoStreams();
    for (int i = 0; i < inputs.size(); i++) {
      String name = streams.get(i).getTransformName();
      if (name != null) {
        inputs.get(i).transform = name;
        streams.get(i).setSubject(name);
      }
    }
  }

  @Override
  public Object clone() {
    var copy = (FileGdbWriterMeta) super.clone();
    copy.inputs =
        new ArrayList<>(inputs.stream().map(i -> new Input(i.dataset, i.transform)).toList());
    copy.resetTransformIoMeta();
    return copy;
  }
}

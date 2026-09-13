package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbCatalog;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.pipeline.transform.BaseTransformMeta;

@Transform(
    id = "SOGIS_FILEGDB_CATALOG_READER",
    name = "FileGDB Catalog Reader",
    description = "Read FileGDB domains and relationships",
    image = "ch/so/agi/hop/vector/transforms/icons/vector-reader.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public class FileGdbCatalogReaderMeta
    extends BaseTransformMeta<FileGdbCatalogReader, FileGdbCatalogReaderData> {
  @HopMetadataProperty private String fileName = "";
  @HopMetadataProperty private String mode = "DOMAINS";
  @HopMetadataProperty private String nameFilter = "";

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String v) {
    fileName = v;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String v) {
    mode = v;
  }

  public String getNameFilter() {
    return nameFilter;
  }

  public void setNameFilter(String v) {
    nameFilter = v;
  }

  @Override
  public void getFields(
      org.apache.hop.core.row.IRowMeta row,
      String name,
      org.apache.hop.core.row.IRowMeta[] info,
      org.apache.hop.pipeline.transform.TransformMeta next,
      org.apache.hop.core.variables.IVariables vars,
      org.apache.hop.metadata.api.IHopMetadataProvider provider) {
    row.clear();
    row.addRowMeta(FileGdbCatalog.Mode.valueOf(mode).rowMeta());
  }
}

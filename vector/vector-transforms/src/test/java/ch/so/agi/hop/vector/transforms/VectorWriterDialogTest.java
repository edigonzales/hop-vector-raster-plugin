package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.Arrays;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.xml.XmlHandler;
import org.junit.jupiter.api.Test;

class VectorWriterDialogTest {
  private static IRowMeta fields(org.apache.hop.core.row.IValueMeta... values) {
    RowMeta result = new RowMeta();
    Arrays.stream(values).forEach(result::addValueMeta);
    return result;
  }

  @Test
  void selectsOnlyUniqueGeometryField() {
    assertThat(
            VectorWriterDialog.initialGeometryField(
                "", fields(new ValueMetaString("name"), new ValueMetaGeometry("shape"))))
        .isEqualTo("shape");
    assertThat(
            VectorWriterDialog.initialGeometryField(
                "", fields(new ValueMetaGeometry("shape"), new ValueMetaGeometry("outline"))))
        .isEmpty();
    assertThat(VectorWriterDialog.initialGeometryField("", fields(new ValueMetaString("name"))))
        .isEmpty();
  }

  @Test
  void preservesExplicitValuesAndUnknownSchemas() {
    IRowMeta fields = fields(new ValueMetaGeometry("shape"));
    assertThat(VectorWriterDialog.initialGeometryField("shape", fields)).isEqualTo("shape");
    assertThat(VectorWriterDialog.initialGeometryField("${GEOM}", fields)).isEqualTo("${GEOM}");
    assertThat(VectorWriterDialog.initialGeometryField("geometry", fields)).isEqualTo("shape");
    assertThat(VectorWriterDialog.initialGeometryField("geometry", null)).isEqualTo("geometry");
  }

  @Test
  void keepsLegacyGeometryOnlyWhenItIsAnAvailableGeometryField() {
    IRowMeta fields = fields(new ValueMetaGeometry("geometry"), new ValueMetaGeometry("shape"));
    assertThat(VectorWriterDialog.initialGeometryField("geometry", fields))
        .isEqualTo("geometry");
  }

  @Test
  void newMetadataHasNoGeometryDefaultAndOldMissingTagsRemainCompatible() throws Exception {
    assertThat(new VectorWriterMeta().getGeometryField()).isEmpty();

    var old = new VectorWriterMeta();
    old.loadXml(
        XmlHandler.loadXmlString("<transform><fileName>old.shp</fileName></transform>")
            .getDocumentElement(),
        null);
    assertThat(old.getGeometryField()).isEqualTo("geometry");

    var empty = new VectorWriterMeta();
    empty.loadXml(
        XmlHandler.loadXmlString("<transform><geometryField/></transform>")
            .getDocumentElement(),
        null);
    assertThat(empty.getGeometryField()).isEmpty();
  }

  @Test
  void usesFormatSpecificFileDialogFilters() {
    assertThat(VectorWriterDialog.fileDialogFilterExtensions("AUTO")).containsExactly("*.*");
    assertThat(VectorWriterDialog.fileDialogFilterExtensions("SHAPEFILE"))
        .containsExactly("*.shp");
    assertThat(VectorWriterDialog.fileDialogFilterExtensions("GEOPACKAGE"))
        .containsExactly("*.gpkg");
    assertThat(VectorWriterDialog.fileDialogFilterExtensions("ARCINFO_GENERATE"))
        .containsExactly("*.gen");
    assertThat(VectorWriterDialog.fileDialogFilterExtensions("FLATGEOBUF"))
        .containsExactly("*.fgb");
    assertThat(VectorWriterDialog.fileDialogFilterExtensions("PARQUET"))
        .containsExactly("*.parquet");
    assertThat(VectorWriterDialog.fileDialogFilterExtensions("FILEGEODATABASE"))
        .containsExactly("*.*");
    assertThat(VectorWriterDialog.fileDialogFilterNames("SHAPEFILE"))
        .containsExactly("Shapefile (*.shp)");
  }
}

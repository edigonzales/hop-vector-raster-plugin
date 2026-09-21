package ch.so.agi.hop.vector.formats.shapefile;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.vector.formats.shapefile.binary.DbfField;
import ch.so.agi.hop.vector.formats.shapefile.binary.DbfFieldType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class DbfMappingTest {
  private final DbfField date = new DbfField("when", DbfFieldType.DATE, 8, 0);

  @Test
  void nullDateRepresentations() {
    for (String raw : List.of("00000000", "       0", "        ", "?")) {
      assertThat(DbfMapping.read(date, raw, ZoneId.of("UTC"))).as(raw).isNull();
    }
  }

  @Test
  void validDatesRespectTimezone() {
    for (String timezone : List.of("UTC", "Europe/Zurich")) {
      ZoneId zone = ZoneId.of(timezone);
      for (LocalDate value : List.of(LocalDate.of(2026, 9, 21), LocalDate.of(2024, 2, 29))) {
        String raw = value.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(DbfMapping.read(date, raw, zone))
            .isEqualTo(Date.from(value.atStartOfDay(zone).toInstant()));
      }
    }
  }

  @Test
  void invalidDatesStillFail() {
    for (String raw : List.of("20260230", "20260015")) {
      assertThatThrownBy(() -> DbfMapping.read(date, raw, ZoneId.of("UTC")))
          .isInstanceOf(DateTimeParseException.class);
    }
  }

  @Test
  void nullByteFilledTextIsNull() {
    var field = new DbfField("text", DbfFieldType.CHARACTER, 254, 0);
    assertThat(DbfMapping.read(field, "\0".repeat(254), ZoneId.of("UTC"))).isNull();
  }

  @Test
  void ordinaryTextAndLeadingSpacesArePreserved() {
    var field = new DbfField("text", DbfFieldType.CHARACTER, 254, 0);
    for (String value : List.of("Grüezi", "  leading", "?", "00000000", "***")) {
      assertThat(DbfMapping.read(field, value + "   ", ZoneId.of("UTC"))).isEqualTo(value);
    }
    assertThat(DbfMapping.read(field, "   ", ZoneId.of("UTC"))).isEqualTo("");
    assertThat(DbfMapping.read(field, "", ZoneId.of("UTC"))).isEqualTo("");
  }

  @Test
  void numericNullRepresentations() {
    for (DbfFieldType type : List.of(DbfFieldType.NUMERIC, DbfFieldType.FLOAT)) {
      for (int scale : List.of(0, 3)) {
        var field = new DbfField("number", type, 9, scale);
        for (String raw : List.of("*********", "        *", "         ", "?")) {
          assertThat(DbfMapping.read(field, raw, ZoneId.of("UTC"))).as(raw).isNull();
        }
        for (String raw : List.of("*123", "12*3", "NaN", "invalid")) {
          assertThatThrownBy(() -> DbfMapping.read(field, raw, ZoneId.of("UTC")))
              .isInstanceOf(NumberFormatException.class);
        }
        if (scale == 0) {
          assertThat(DbfMapping.read(field, "      -12", ZoneId.of("UTC"))).isEqualTo(-12L);
        } else {
          assertThat(DbfMapping.read(field, "   -12.50", ZoneId.of("UTC")))
              .isEqualTo(new java.math.BigDecimal("-12.50"));
          assertThat(DbfMapping.read(field, "  1.25E+2", ZoneId.of("UTC")))
              .isEqualTo(new java.math.BigDecimal("1.25E+2"));
        }
      }
    }
  }

  @Test
  void zeroValuesInOtherFieldTypesArePreserved() {
    assertThat(
            DbfMapping.read(
                new DbfField("text", DbfFieldType.CHARACTER, 8, 0),
                "********",
                ZoneId.of("UTC")))
        .isEqualTo("********");
    assertThat(
            DbfMapping.read(
                new DbfField("text", DbfFieldType.CHARACTER, 8, 0),
                "00000000",
                ZoneId.of("UTC")))
        .isEqualTo("00000000");
    assertThat(
            DbfMapping.read(
                new DbfField("number", DbfFieldType.NUMERIC, 8, 0),
                "00000000",
                ZoneId.of("UTC")))
        .isEqualTo(0L);
  }
}

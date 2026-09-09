package ch.so.agi.hop.vector.formats.shapefile;

import ch.so.agi.hop.vector.core.*;
import ch.so.agi.hop.vector.formats.shapefile.binary.*;
import java.math.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;

final class DbfMapping {
  static Charset charset(String value) {
    String v = value.trim();
    v =
        switch (v.toUpperCase(Locale.ROOT)) {
          case "65001", "UTF8" -> "UTF-8";
          case "1252" -> "windows-1252";
          case "88591", "28591" -> "ISO-8859-1";
          case "850" -> "IBM850";
          default -> v;
        };
    return Charset.forName(v);
  }

  static Charset inputCharset(ShapefileDataset ds, ReadRequest r) throws Exception {
    if (!r.shapefile().charset().isBlank()) return charset(r.shapefile().charset());
    if (ds.cpg().isPresent()) {
      String name = Files.readString(ds.cpg().get()).strip().replace("\uFEFF", "");
      if (!name.isBlank()) return charset(name);
    }
    int ldid;
    try (var c = java.nio.channels.FileChannel.open(ds.dbf())) {
      var b = java.nio.ByteBuffer.allocate(1);
      c.position(29);
      if (c.read(b) != 1) throw new java.io.IOException("Truncated DBF header");
      ldid = b.array()[0] & 255;
    }
    String code =
        switch (ldid) {
          case 1 -> "IBM437";
          case 2 -> "IBM850";
          case 3, 0x57 -> "windows-1252";
          case 0x64 -> "IBM852";
          case 0x65 -> "IBM866";
          case 0x66 -> "IBM865";
          case 0x67 -> "IBM861";
          case 0x6A -> "IBM737";
          case 0x6B -> "IBM857";
          case 0x7A -> "GBK";
          case 0x7B -> "windows-31j";
          case 0x7C -> "Big5";
          case 0xC8 -> "windows-1250";
          case 0xC9 -> "windows-1251";
          case 0xCA -> "windows-1254";
          case 0xCB -> "windows-1253";
          default -> null;
        };
    if (code != null) return charset(code);
    r.diagnostics().warning("", "encoding-fallback", "No recognized CPG/LDID; using ISO-8859-1");
    return StandardCharsets.ISO_8859_1;
  }

  static IValueMeta meta(DbfField f) {
    IValueMeta m =
        switch (f.type()) {
          case CHARACTER -> new ValueMetaString(f.name());
          case LOGICAL -> new ValueMetaBoolean(f.name());
          case DATE -> new ValueMetaDate(f.name());
          case NUMERIC, FLOAT ->
              f.decimalCount() == 0 && f.length() <= 20
                  ? new ValueMetaInteger(f.name())
                  : new ValueMetaBigNumber(f.name());
        };
    m.setLength(f.length());
    m.setPrecision(f.decimalCount());
    return m;
  }

  static Object read(DbfField f, String raw, ZoneId zone) {
    String s = raw.strip();
    if (f.type() == DbfFieldType.CHARACTER) return raw.stripTrailing();
    if (s.isEmpty() || s.equals("?")) return null;
    return switch (f.type()) {
      case CHARACTER -> s;
      case NUMERIC, FLOAT ->
          f.decimalCount() == 0 && f.length() <= 20
              ? (Object) new BigDecimal(s).longValueExact()
              : new BigDecimal(s);
      case DATE ->
          java.util.Date.from(
              java.time.LocalDate.parse(s, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                  .atStartOfDay(zone)
                  .toInstant());
      case LOGICAL ->
          switch (s.toUpperCase(Locale.ROOT)) {
            case "T", "Y" -> true;
            case "F", "N" -> false;
            default -> throw new IllegalArgumentException("Invalid DBF logical value");
          };
    };
  }

  record Column(int index, DbfField field) {}

  static List<Column> columns(WriteRequest r) {
    List<Column> result = new ArrayList<>();
    Set<String> used = new HashSet<>();
    Map<String, ShapefileOptions.Field> overrides = new HashMap<>();
    for (var o : r.shapefile().fields()) {
      if (r.rowMeta().indexOfValue(o.source()) < 0
          || r.rowMeta().indexOfValue(o.source()) == r.geometryIndex())
        throw new IllegalArgumentException("Unknown attribute: " + o.source());
      if (overrides.put(o.source(), o) != null)
        throw new IllegalArgumentException("Duplicate field mapping: " + o.source());
    }
    for (int i = 0; i < r.rowMeta().size(); i++)
      if (i != r.geometryIndex()) {
        var v = r.rowMeta().getValueMeta(i);
        var o = overrides.get(v.getName());
        String target = o == null || o.target().isBlank() ? v.getName() : o.target();
        String stem =
            java.text.Normalizer.normalize(target, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9_]", "_");
        if (stem.isEmpty() || Character.isDigit(stem.charAt(0))) stem = "_" + stem;
        String name = stem.substring(0, Math.min(10, stem.length()));
        for (int n = 1; !used.add(name.toLowerCase(Locale.ROOT)); n++) {
          String suffix = "_" + n;
          if (suffix.length() >= 10)
            throw new IllegalArgumentException("Too many colliding DBF field names");
          name = stem.substring(0, Math.min(stem.length(), 10 - suffix.length())) + suffix;
        }
        if (!name.equals(v.getName()))
          r.diagnostics().warning(v.getName(), "field-name", "Output field: " + name);
        DbfFieldType type;
        int width, scale = 0;
        switch (v.getType()) {
          case IValueMeta.TYPE_STRING -> {
            type = DbfFieldType.CHARACTER;
            width = v.getLength() > 0 ? (int) Math.min(254, v.getLength()) : 254;
          }
          case IValueMeta.TYPE_INTEGER -> {
            type = DbfFieldType.NUMERIC;
            width = v.getLength() > 0 ? (int) v.getLength() : 20;
          }
          case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER -> {
            type = DbfFieldType.NUMERIC;
            width = v.getLength() > 0 ? (int) v.getLength() : 33;
            scale = v.getPrecision() >= 0 ? v.getPrecision() : 15;
          }
          case IValueMeta.TYPE_BOOLEAN -> {
            type = DbfFieldType.LOGICAL;
            width = 1;
          }
          case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> {
            type = DbfFieldType.DATE;
            width = 8;
          }
          default ->
              throw new IllegalArgumentException(
                  "Convert unsupported attribute before export: " + v.getName());
        }
        if (o != null) {
          if (o.width() > 0) width = o.width();
          if (o.scale() >= 0) scale = o.scale();
        }
        if (type == DbfFieldType.CHARACTER && width > 254) {
          width = 254;
          r.diagnostics().warning(v.getName(), "field-width", "Text width limited to 254 bytes");
        }
        if (width < 1
            || width > 254
            || scale < 0
            || scale >= width
            || type == DbfFieldType.DATE && width != 8
            || type == DbfFieldType.LOGICAL && width != 1
            || type != DbfFieldType.NUMERIC && scale != 0)
          throw new IllegalArgumentException("Invalid DBF width/scale for " + v.getName());
        result.add(new Column(i, new DbfField(name, type, width, scale)));
      }
    if (result.size() > 255 || 1 + result.stream().mapToInt(c -> c.field().length()).sum() > 65535)
      throw new IllegalArgumentException("DBF schema exceeds field/record limits");
    return result;
  }

  static Object write(Object value, Column col, Charset charset, ZoneId zone, Diagnostics d) {
    DbfField f = col.field();
    if (value == null) {
      d.warning(f.name(), "null-attribute", "NULL mapped to DBF blank/unknown representation");
      return null;
    }
    switch (f.type()) {
      case CHARACTER -> {
        String text = value.toString();
        var encoder = charset.newEncoder();
        StringBuilder safe = new StringBuilder();
        boolean replaced = false;
        for (int i = 0; i < text.length(); ) {
          int cp = text.codePointAt(i);
          String ch = new String(Character.toChars(cp));
          if (!encoder.canEncode(ch)) {
            ch = "?";
            replaced = true;
          }
          safe.append(ch);
          i += Character.charCount(cp);
        }
        if (replaced)
          d.warning(f.name(), "encoding-replacement", "Unrepresentable characters replaced by ?");
        text = safe.toString();
        if (text.getBytes(charset).length > f.length())
          d.warning(f.name(), "text-truncation", "Text shortened to " + f.length() + " bytes");
        return text;
      }
      case NUMERIC, FLOAT -> {
        BigDecimal n = value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
        BigDecimal rounded = n.setScale(f.decimalCount(), RoundingMode.HALF_UP);
        if (n.compareTo(rounded) != 0)
          d.warning(
              f.name(), "rounding", "Number rounded to " + f.decimalCount() + " decimal places");
        return rounded;
      }
      case DATE -> {
        Instant instant =
            value instanceof java.sql.Date date
                ? date.toLocalDate().atStartOfDay(zone).toInstant()
                : ((java.util.Date) value).toInstant();
        var local = instant.atZone(zone);
        if (!local.toLocalTime().equals(LocalTime.MIDNIGHT))
          d.warning(f.name(), "date-time-loss", "Time discarded using timezone " + zone);
        return local.toLocalDate();
      }
      default -> {
        return value;
      }
    }
  }
}

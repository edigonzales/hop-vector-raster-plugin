package ch.so.agi.hop.vector.formats.filegeodatabase;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.*;
import java.nio.file.Path;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.ValueMetaString;

/** Catalog metadata is deliberately separate from feature row metadata. */
public final class FileGdbCatalog {
  public enum Mode {
    DOMAINS("domain,type,field_type,description,min_value,max_value,split_policy,merge_policy"),
    DOMAIN_VALUES("domain,code,description,field_type"),
    FIELD_DOMAINS("dataset,field,domain,field_type"),
    RELATIONSHIPS(
        "relationship,origin,destination,cardinality,forward_label,backward_label,composite,attributed,attachment"),
    RELATIONSHIP_KEYS("relationship,side,role,field");
    private final String columns;

    Mode(String columns) {
      this.columns = columns;
    }

    public IRowMeta rowMeta() {
      var meta = new RowMeta();
      for (String name : columns.split(",")) meta.addValueMeta(new ValueMetaString(name));
      return meta;
    }
  }

  private FileGdbCatalog() {}

  public static List<Object[]> read(Path path, Mode mode, String filter) throws Exception {
    var result = new ArrayList<Object[]>();
    try (var db = FileGeodatabase.open(path)) {
      switch (mode) {
        case DOMAINS, DOMAIN_VALUES -> {
          for (var d : db.domains())
            if (matches(d.name(), filter)) {
              if (mode == Mode.DOMAINS)
                result.add(
                    new Object[] {
                      d.name(),
                      d instanceof RangeDomain ? "RANGE" : "CODED",
                      d.fieldType().name(),
                      d.description(),
                      d instanceof RangeDomain r ? r.minValue() : null,
                      d instanceof RangeDomain r ? r.maxValue() : null,
                      d.splitPolicy().name(),
                      d.mergePolicy().name()
                    });
              else
                for (var v : d.values())
                  result.add(new Object[] {d.name(), v.code(), v.name(), d.fieldType().name()});
            }
        }
        case FIELD_DOMAINS -> {
          for (var d : db.datasets())
            if (matches(d.name(), filter))
              try (var table = db.table(d.name())) {
                for (var f : table.fields())
                  if (f.domain() != null)
                    result.add(new Object[] {d.name(), f.name(), f.domain(), f.type().name()});
              }
        }
        case RELATIONSHIPS, RELATIONSHIP_KEYS -> {
          for (var r : db.relationships())
            if (matches(r.name(), filter)) {
              if (mode == Mode.RELATIONSHIPS)
                result.add(
                    new Object[] {
                      r.name(),
                      r.originClassName(),
                      r.destinationClassName(),
                      r.cardinality().name(),
                      r.forwardLabel(),
                      r.backwardLabel(),
                      Boolean.toString(r.composite()),
                      Boolean.toString(r.attributed()),
                      Boolean.toString(r.attachment())
                    });
              else {
                for (var key : r.originKeys())
                  result.add(
                      new Object[] {r.name(), "ORIGIN", key.role().name(), key.objectKeyName()});
                for (var key : r.destinationKeys())
                  result.add(
                      new Object[] {
                        r.name(), "DESTINATION", key.role().name(), key.objectKeyName()
                      });
              }
            }
        }
      }
    }
    return result;
  }

  private static boolean matches(String name, String filter) {
    return filter == null || filter.isBlank() || name.equals(filter);
  }

  public static String preview(Path path) throws Exception {
    var text = new StringBuilder();
    for (var mode : Mode.values()) {
      text.append('\n').append(mode).append('\n');
      text.append(mode.columns).append('\n');
      for (var row : read(path, mode, "")) text.append(Arrays.toString(row)).append('\n');
    }
    return text.toString();
  }
}

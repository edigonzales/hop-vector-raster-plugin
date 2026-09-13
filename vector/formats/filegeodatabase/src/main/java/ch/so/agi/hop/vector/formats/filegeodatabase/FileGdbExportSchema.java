package ch.so.agi.hop.vector.formats.filegeodatabase;

import ch.so.agi.filegdb.catalog.*;
import ch.so.agi.filegdb.table.*;
import ch.so.agi.filegdb.write.RelationshipDefinition;
import com.fasterxml.jackson.databind.*;
import java.nio.file.Path;
import java.util.*;

/** Explicit, versioned export contract. No pipeline variables are expanded inside JSON. */
public record FileGdbExportSchema(
    int schemaVersion,
    List<DomainSpec> domains,
    List<DatasetSpec> datasets,
    List<RelationshipSpec> relationships) {
  public record Code(String code, String label) {}

  public record DomainSpec(
      String name,
      String description,
      String type,
      FileGdbFieldType fieldType,
      List<Code> values,
      String minValue,
      String maxValue,
      DomainSplitPolicy splitPolicy,
      DomainMergePolicy mergePolicy) {
    public Domain domain() {
      var split = splitPolicy == null ? DomainSplitPolicy.DEFAULT_VALUE : splitPolicy;
      var merge = mergePolicy == null ? DomainMergePolicy.DEFAULT_VALUE : mergePolicy;
      if ("CODED".equals(type)) {
        if (values == null || minValue != null || maxValue != null)
          throw new IllegalArgumentException("Coded domain requires values only: " + name);
        return new CodedValueDomain(
            name,
            fieldType,
            description == null ? "" : description,
            values.stream()
                .map(
                    v ->
                        new CodedValue(
                            Objects.requireNonNull(v.label()), Objects.requireNonNull(v.code())))
                .toList(),
            split,
            merge);
      }
      if ("RANGE".equals(type)) {
        if (values != null)
          throw new IllegalArgumentException("Range domain cannot contain codes: " + name);
        return new RangeDomain(
            name,
            fieldType,
            description == null ? "" : description,
            minValue,
            maxValue,
            split,
            merge);
      }
      throw new IllegalArgumentException("Unknown domain type: " + type);
    }
  }

  public record FieldSpec(
      String source,
      String name,
      FileGdbFieldType type,
      Boolean nullable,
      Integer length,
      String domain) {
    public String sourceName() {
      return source == null ? name : source;
    }

    public FileGdbField field() {
      if (type == null
          || Set.of(
                  FileGdbFieldType.OBJECTID,
                  FileGdbFieldType.GEOMETRY,
                  FileGdbFieldType.UNDEFINED,
                  FileGdbFieldType.RASTER,
                  FileGdbFieldType.GLOBALID)
              .contains(type))
        throw new IllegalArgumentException("Unsupported explicit field type: " + type);
      return new FileGdbField(
          name,
          "",
          type,
          Boolean.TRUE.equals(nullable),
          false,
          true,
          type == FileGdbFieldType.STRING ? (length == null ? 255 : length) : 0,
          type == FileGdbFieldType.DATETIME,
          domain);
    }
  }

  public record GeometrySpec(
      String source,
      String name,
      String type,
      String dimension,
      String crs,
      String precisionMode,
      Double xyResolution,
      Double xyTolerance,
      Double xOrigin,
      Double yOrigin,
      Boolean spatialIndex) {}

  public record DatasetSpec(
      String name, String kind, List<FieldSpec> fields, GeometrySpec geometry) {}

  public record RelationshipSpec(
      String name,
      String origin,
      String destination,
      String originKey,
      String foreignKey,
      RelationshipCardinality cardinality,
      String forwardLabel,
      String backwardLabel) {
    public RelationshipDefinition definition() {
      return RelationshipDefinition.builder(name)
          .originClass(origin)
          .destinationClass(destination)
          .originPrimaryKey(originKey)
          .originForeignKey(foreignKey)
          .cardinality(cardinality)
          .labels(forwardLabel, backwardLabel)
          .build();
    }
  }

  public static FileGdbExportSchema read(Path path) throws Exception {
    var mapper = new ObjectMapper();
    mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    mapper.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    try {
      var schema = mapper.readValue(path.toFile(), FileGdbExportSchema.class);
      schema.validate();
      return schema;
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Invalid FileGDB schema " + path + ": " + e.getMessage(), e);
    }
  }

  public FileGdbExportSchema {
    domains = domains == null ? List.of() : List.copyOf(domains);
    datasets = datasets == null ? List.of() : List.copyOf(datasets);
    relationships = relationships == null ? List.of() : List.copyOf(relationships);
  }

  public DatasetSpec dataset(String name) {
    return datasets.stream()
        .filter(d -> d.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown schema dataset: " + name));
  }

  public void validate() {
    if (schemaVersion != 1) throw new IllegalArgumentException("Expected schemaVersion 1");
    if (datasets.isEmpty()) throw new IllegalArgumentException("At least one dataset is required");
    var names = new HashSet<String>();
    var domainMap = new HashMap<String, Domain>();
    for (var spec : domains) {
      name(spec.name(), names);
      var domain = spec.domain();
      DomainValues.validate(domain);
      domainMap.put(domain.name(), domain);
    }
    for (var d : datasets) {
      name(d.name(), names);
      if (!Set.of("TABLE", "FEATURE_CLASS").contains(d.kind()))
        throw new IllegalArgumentException("Unknown dataset kind: " + d.kind());
      if (d.kind().equals("FEATURE_CLASS") != (d.geometry() != null))
        throw new IllegalArgumentException(
            "Geometry definition does not match dataset kind: " + d.name());
      if (d.fields() == null)
        throw new IllegalArgumentException("fields array required: " + d.name());
      var fields = new HashSet<String>();
      fields.add("objectid");
      if (d.geometry() != null) {
        var g = d.geometry();
        fieldName(g.name(), fields);
        if (g.source() == null
            || g.source().isBlank()
            || g.type() == null
            || g.dimension() == null
            || g.crs() == null)
          throw new IllegalArgumentException(
              "Explicit geometry source, type, dimension and CRS required: " + d.name());
      }
      for (var f : d.fields()) {
        fieldName(f.name(), fields);
        f.field();
        if (f.sourceName().isBlank()) throw new IllegalArgumentException("Empty source field");
        if (f.length() != null && (f.length() < 1 || f.length() > 65535))
          throw new IllegalArgumentException("Invalid field length");
        if (f.domain() != null
            && (!domainMap.containsKey(f.domain())
                || domainMap.get(f.domain()).fieldType() != f.type()))
          throw new IllegalArgumentException(
              "Unknown or incompatible domain: " + d.name() + "." + f.name());
      }
    }
    for (var r : relationships) {
      name(r.name(), names);
      if (r.cardinality() != RelationshipCardinality.ONE_TO_ONE
          && r.cardinality() != RelationshipCardinality.ONE_TO_MANY)
        throw new IllegalArgumentException("Only simple 1:1 and 1:n relationships are supported");
      var a = field(dataset(r.origin()), r.originKey());
      var b = field(dataset(r.destination()), r.foreignKey());
      if (a.type() != b.type()
          || !Set.of(
                  FileGdbFieldType.INT16,
                  FileGdbFieldType.INT32,
                  FileGdbFieldType.INT64,
                  FileGdbFieldType.STRING,
                  FileGdbFieldType.GUID)
              .contains(a.type()))
        throw new IllegalArgumentException("Incompatible relationship keys: " + r.name());
      r.definition();
    }
  }

  private static FieldSpec field(DatasetSpec d, String name) {
    if ("OBJECTID".equalsIgnoreCase(name))
      throw new IllegalArgumentException(
          "Relationships require explicit stable keys; map source OBJECTID to SOURCE_OBJECTID");
    return d.fields().stream()
        .filter(f -> f.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown key: " + d.name() + "." + name));
  }

  private static void fieldName(String name, Set<String> names) {
    name(name, names);
    if (name.length() > 64)
      throw new IllegalArgumentException("Field name exceeds 64 characters: " + name);
  }

  private static void name(String name, Set<String> names) {
    if (name == null
        || !name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")
        || name.length() > 160
        || name.toUpperCase(Locale.ROOT).startsWith("GDB_")
        || !names.add(name.toLowerCase(Locale.ROOT)))
      throw new IllegalArgumentException("Invalid or duplicate name: " + name);
  }
}

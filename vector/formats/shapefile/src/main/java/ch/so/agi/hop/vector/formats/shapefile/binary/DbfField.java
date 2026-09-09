// Adapted from ilitransformer, Copyright (c) 2026 Stefan Ziegler, MIT. See THIRD-PARTY-NOTICES.md.
package ch.so.agi.hop.vector.formats.shapefile.binary;

public record DbfField(String name, DbfFieldType type, int length, int decimalCount) {}

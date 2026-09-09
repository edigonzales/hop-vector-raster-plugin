// Adapted from ilitransformer, Copyright (c) 2026 Stefan Ziegler, MIT. See THIRD-PARTY-NOTICES.md.
package ch.so.agi.hop.vector.formats.shapefile.binary;

import ch.so.agi.hop.vector.formats.shapefile.ShapefileMappingException;

public enum DbfFieldType {
  CHARACTER('C'),
  NUMERIC('N'),
  FLOAT('F'),
  LOGICAL('L'),
  DATE('D');

  private final char code;

  DbfFieldType(char code) {
    this.code = code;
  }

  public char code() {
    return code;
  }

  public static DbfFieldType fromCode(char code) throws ShapefileMappingException {
    for (DbfFieldType t : values()) {
      if (t.code == code) {
        return t;
      }
    }
    throw new ShapefileMappingException(
        "Unsupported DBF field type '"
            + code
            + "'. Supported: C (Character), N (Numeric), F (Float), L (Logical), D (Date).");
  }
}

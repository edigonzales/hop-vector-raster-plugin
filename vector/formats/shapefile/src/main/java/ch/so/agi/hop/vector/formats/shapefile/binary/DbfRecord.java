// Adapted from ilitransformer, Copyright (c) 2026 Stefan Ziegler, MIT. See THIRD-PARTY-NOTICES.md.
package ch.so.agi.hop.vector.formats.shapefile.binary;

import java.util.List;

public record DbfRecord(boolean deleted, List<String> values) {}

package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.core.Diagnostics;
import java.util.*;
import java.util.function.Consumer;

final class VectorDiagnostics implements Diagnostics {
  private final Consumer<String> log;
  private final Map<String, Long> counts = new LinkedHashMap<>();
  private final Set<String> seen = new HashSet<>();

  VectorDiagnostics(Consumer<String> log) {
    this.log = log;
  }

  public void nextRecord() {
    seen.clear();
  }

  void nextRow() {
    nextRecord();
  }

  public void warning(String field, String cause, String message) {
    String key = field + " / " + cause;
    if (!seen.add(key)) return;
    long count = counts.merge(key, 1L, Long::sum);
    if (count == 1) log.accept("Warning: " + key + ": " + message);
  }

  void finish() {
    counts.forEach(
        (key, count) ->
            log.accept("Warning summary: " + key + ": " + count + " affected records/events"));
    counts.clear();
    seen.clear();
  }
}

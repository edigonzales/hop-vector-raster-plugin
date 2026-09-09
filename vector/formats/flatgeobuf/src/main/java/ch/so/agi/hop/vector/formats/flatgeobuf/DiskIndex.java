package ch.so.agi.hop.vector.formats.flatgeobuf;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.locationtech.jts.geom.Envelope;
import org.wololo.flatgeobuf.NodeItem;
import org.wololo.flatgeobuf.PackedRTree;

/** External merge sort and disk-backed packed R-tree. Feature data never resides in the index. */
final class DiskIndex {
  @FunctionalInterface
  interface Check {
    void run() throws IOException;
  }

  record Entry(
      long key, long offset, long size, double minX, double minY, double maxX, double maxY) {
    Entry keyed(Envelope extent) {
      return new Entry(
          PackedRTree.hibert(
              new NodeItem(minX, minY, maxX, maxY),
              65535,
              extent.getMinX(),
              extent.getMinY(),
              extent.getWidth(),
              extent.getHeight()),
          offset,
          size,
          minX,
          minY,
          maxX,
          maxY);
    }

    void write(DataOutput out) throws IOException {
      out.writeLong(key);
      out.writeLong(offset);
      out.writeLong(size);
      out.writeDouble(minX);
      out.writeDouble(minY);
      out.writeDouble(maxX);
      out.writeDouble(maxY);
    }

    static Entry read(DataInputStream in) throws IOException {
      int first = in.read();
      if (first < 0) return null;
      long key = ((long) first) << 56;
      for (int i = 6; i >= 0; i--) key |= (long) in.readUnsignedByte() << (i * 8);
      return new Entry(
          key,
          in.readLong(),
          in.readLong(),
          in.readDouble(),
          in.readDouble(),
          in.readDouble(),
          in.readDouble());
    }

    void node(RandomAccessFile out, long position, long pointer) throws IOException {
      out.seek(position * 40);
      le(out, minX);
      le(out, minY);
      le(out, maxX);
      le(out, maxY);
      out.writeLong(Long.reverseBytes(pointer));
    }
  }

  static final Comparator<Entry> ORDER =
      Comparator.comparingLong(Entry::key).thenComparingLong(Entry::offset);

  static Path sort(Path source, Path dir, Envelope extent, long budget, Check check)
      throws IOException {
    int capacity = (int) Math.max(1, Math.min(Integer.MAX_VALUE - 8, budget / 192));
    List<Path> runs = new ArrayList<>();
    try (var in = input(source)) {
      boolean done = false;
      while (!done) {
        check.run();
        var chunk = new ArrayList<Entry>();
        for (int i = 0; i < capacity; i++) {
          var e = Entry.read(in);
          if (e == null) {
            done = true;
            break;
          }
          chunk.add(e.keyed(extent));
        }
        if (chunk.isEmpty()) break;
        chunk.sort(ORDER);
        Path run = Files.createTempFile(dir, "sort-", ".run");
        try (var out = output(run)) {
          for (var e : chunk) e.write(out);
        }
        runs.add(run);
      }
    }
    while (runs.size() > 1) {
      List<Path> next = new ArrayList<>();
      for (int start = 0; start < runs.size(); start += 64) {
        var group = runs.subList(start, Math.min(start + 64, runs.size()));
        Path merged = Files.createTempFile(dir, "merge-", ".run");
        merge(group, merged, check);
        next.add(merged);
        for (Path p : group) Files.delete(p);
      }
      runs = next;
    }
    return runs.isEmpty() ? Files.createTempFile(dir, "empty-", ".run") : runs.get(0);
  }

  private static void merge(List<Path> files, Path target, Check check) throws IOException {
    record Cursor(DataInputStream input, Entry entry) {}
    var streams = new ArrayList<DataInputStream>();
    var queue = new PriorityQueue<Cursor>(Comparator.comparing(Cursor::entry, ORDER));
    try (var out = output(target)) {
      for (Path p : files) {
        var in = input(p);
        streams.add(in);
        var e = Entry.read(in);
        if (e != null) queue.add(new Cursor(in, e));
      }
      while (!queue.isEmpty()) {
        check.run();
        var c = queue.remove();
        c.entry.write(out);
        var e = Entry.read(c.input);
        if (e != null) queue.add(new Cursor(c.input, e));
      }
    } finally {
      for (var in : streams) in.close();
    }
  }

  static Path tree(Path sorted, Path dir, long count, Check check) throws IOException {
    var counts = new ArrayList<Long>();
    long n = count;
    do {
      counts.add(n);
      n = (n + 15) / 16;
    } while (n > 1);
    // FlatGeobuf includes a parent for a one-feature tree too.
    counts.add(1L);
    long[] starts = new long[counts.size()];
    long total = 0;
    for (int i = counts.size() - 1; i >= 0; i--) {
      starts[i] = total;
      total = Math.addExact(total, counts.get(i));
    }
    Path file = Files.createTempFile(dir, "tree-", ".bin");
    try (var out = new RandomAccessFile(file.toFile(), "rw");
        var in = input(sorted)) {
      out.setLength(Math.multiplyExact(total, 40));
      long position = starts[0], offset = 0;
      Entry e;
      while ((e = Entry.read(in)) != null) {
        check.run();
        e.node(out, position++, offset);
        offset = Math.addExact(offset, e.size);
      }
      for (int level = 1; level < counts.size(); level++) {
        long child = starts[level - 1], end = child + counts.get(level - 1), parent = starts[level];
        while (child < end) {
          check.run();
          long first = child;
          Envelope env = new Envelope();
          for (int j = 0; j < 16 && child < end; j++, child++) {
            out.seek(child * 40);
            double minX = readLe(out), minY = readLe(out), maxX = readLe(out), maxY = readLe(out);
            env.expandToInclude(new Envelope(minX, maxX, minY, maxY));
          }
          new Entry(0, 0, 0, env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY())
              .node(out, parent++, first);
        }
      }
    }
    return file;
  }

  static DataInputStream input(Path p) throws IOException {
    return new DataInputStream(new BufferedInputStream(Files.newInputStream(p)));
  }

  static DataOutputStream output(Path p) throws IOException {
    return new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(p)));
  }

  private static void le(RandomAccessFile o, double d) throws IOException {
    o.writeLong(Long.reverseBytes(Double.doubleToLongBits(d)));
  }

  private static double readLe(RandomAccessFile in) throws IOException {
    return Double.longBitsToDouble(Long.reverseBytes(in.readLong()));
  }

  static void copy(Path path, OutputStream out, Check check) throws IOException {
    try (var in = Files.newInputStream(path)) {
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) >= 0) {
        check.run();
        out.write(b, 0, n);
      }
    }
  }
}

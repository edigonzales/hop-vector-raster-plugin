// Adapted from ilitransformer, Copyright (c) 2026 Stefan Ziegler, MIT. See THIRD-PARTY-NOTICES.md.
package ch.so.agi.hop.vector.formats.shapefile.binary;

import ch.so.agi.hop.vector.formats.shapefile.ShapefileMappingException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DbfReader implements AutoCloseable {

  private final FileChannel channel;
  private final DbfHeader header;
  private final Charset charset;
  private long currentRecord;

  private DbfReader(FileChannel channel, DbfHeader header, Charset charset) {
    this.channel = channel;
    this.header = header;
    this.charset = charset;
    this.currentRecord = 0;
  }

  public static DbfReader open(Path dbfPath, Charset charset)
      throws IOException, ShapefileMappingException {
    FileChannel channel = FileChannel.open(dbfPath, StandardOpenOption.READ);
    try {
      DbfHeader header = readHeader(channel);
      return new DbfReader(channel, header, charset);
    } catch (Exception e) {
      channel.close();
      throw e;
    }
  }

  public DbfHeader header() {
    return header;
  }

  public List<DbfField> fields() {
    return header.fields();
  }

  public Optional<DbfRecord> readNext() throws IOException {
    if (currentRecord >= header.recordCount()) {
      return Optional.empty();
    }

    ByteBuffer recordBuf = ByteBuffer.allocate(header.recordLength());
    readFully(channel, recordBuf);
    recordBuf.flip();

    byte flag = recordBuf.get();
    if (flag != 0x20 && flag != 0x2A) throw new IOException("Invalid DBF deletion flag");
    boolean deleted = (flag == (byte) 0x2A);

    List<String> values = new ArrayList<>(header.fields().size());
    for (DbfField field : header.fields()) {
      byte[] fieldBytes = new byte[field.length()];
      recordBuf.get(fieldBytes);
      String value =
          (field.type() == DbfFieldType.CHARACTER ? charset : StandardCharsets.US_ASCII)
              .newDecoder()
              .decode(ByteBuffer.wrap(fieldBytes))
              .toString();
      values.add(value);
    }

    currentRecord++;
    return Optional.of(new DbfRecord(deleted, values));
  }

  public long currentRecordNumber() {
    return currentRecord;
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }

  private static DbfHeader readHeader(FileChannel channel)
      throws IOException, ShapefileMappingException {
    ByteBuffer fixedBuf = ByteBuffer.allocate(32);
    readFully(channel, fixedBuf);
    fixedBuf.flip();

    byte version = fixedBuf.get();
    int year = Byte.toUnsignedInt(fixedBuf.get());
    int month = Byte.toUnsignedInt(fixedBuf.get());
    int day = Byte.toUnsignedInt(fixedBuf.get());

    fixedBuf.order(ByteOrder.LITTLE_ENDIAN);
    int recordCount = fixedBuf.getInt();
    int headerLength = fixedBuf.getShort() & 0xFFFF;
    int recordLength = fixedBuf.getShort() & 0xFFFF;

    if (headerLength < 33) {
      throw new IOException("DBF header length too small: " + headerLength);
    }

    int fieldDescriptorBytes = headerLength - 32;
    int numFields = (fieldDescriptorBytes - 1) / 32;

    ByteBuffer fieldBuf = ByteBuffer.allocate(fieldDescriptorBytes);
    readFully(channel, fieldBuf);
    fieldBuf.flip();

    List<DbfField> fields = new ArrayList<>(numFields);
    for (int i = 0; i < numFields; i++) {
      byte[] nameBytes = new byte[11];
      fieldBuf.get(nameBytes);
      String name = new String(nameBytes, StandardCharsets.US_ASCII).replace("\0", "").trim();

      char typeCode = (char) fieldBuf.get();
      DbfFieldType type = DbfFieldType.fromCode(typeCode);

      fieldBuf.position(fieldBuf.position() + 4);

      int length = Byte.toUnsignedInt(fieldBuf.get());
      int decimalCount = Byte.toUnsignedInt(fieldBuf.get());

      fieldBuf.position(fieldBuf.position() + 14);

      fields.add(new DbfField(name, type, length, decimalCount));
    }

    if (recordCount < 0
        || (headerLength - 33) % 32 != 0
        || fieldBuf.get() != 0x0D
        || recordLength != 1 + fields.stream().mapToInt(DbfField::length).sum())
      throw new IOException("Invalid DBF header");
    long end = (long) headerLength + (long) recordLength * recordCount;
    if (channel.size() < end || channel.size() > end + 1)
      throw new IOException("DBF length does not match header");
    if (channel.size() == end + 1) {
      ByteBuffer eof = ByteBuffer.allocate(1);
      channel.read(eof, end);
      if (eof.array()[0] != 0x1A) throw new IOException("Invalid DBF EOF marker");
    }
    for (var f : fields)
      if (f.length() < 1 || f.decimalCount() >= f.length())
        throw new IOException("Invalid DBF field width");
    return new DbfHeader(
        version, year, month, day, recordCount, headerLength, recordLength, fields);
  }

  static void readFully(FileChannel channel, ByteBuffer buf) throws IOException {
    while (buf.hasRemaining()) {
      int n = channel.read(buf);
      if (n < 0) {
        throw new IOException("Unexpected end of DBF file at byte " + channel.position());
      }
    }
  }
}

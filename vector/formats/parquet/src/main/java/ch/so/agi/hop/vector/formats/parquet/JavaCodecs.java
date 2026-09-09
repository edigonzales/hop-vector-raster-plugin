package ch.so.agi.hop.vector.formats.parquet;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.*;
import org.apache.parquet.bytes.BytesInput;
import org.apache.parquet.compression.CompressionCodecFactory;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

/** The output path never loads Hadoop codecs or JNI compressors. */
final class JavaCodecs implements CompressionCodecFactory {
  public BytesInputCompressor getCompressor(CompressionCodecName codec) {
    check(codec);
    return new BytesInputCompressor() {
      public BytesInput compress(BytesInput input) throws IOException {
        if (codec == CompressionCodecName.UNCOMPRESSED) return input;
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
          input.writeAllTo(gzip);
        }
        return BytesInput.from(output.toByteArray());
      }

      public CompressionCodecName getCodecName() {
        return codec;
      }

      public void release() {}
    };
  }

  public BytesInputDecompressor getDecompressor(CompressionCodecName codec) {
    check(codec);
    return new BytesInputDecompressor() {
      public BytesInput decompress(BytesInput input, int size) throws IOException {
        if (codec == CompressionCodecName.UNCOMPRESSED) return input;
        try (var gzip = new GZIPInputStream(input.toInputStream())) {
          return BytesInput.from(gzip.readAllBytes());
        }
      }

      public void decompress(ByteBuffer input, int compressed, ByteBuffer output, int size)
          throws IOException {
        byte[] bytes = new byte[compressed];
        input.get(bytes);
        output.put(decompress(BytesInput.from(bytes), size).toByteArray());
      }

      public void release() {}
    };
  }

  private static void check(CompressionCodecName codec) {
    if (codec != CompressionCodecName.GZIP && codec != CompressionCodecName.UNCOMPRESSED)
      throw new IllegalArgumentException("Unsupported codec: " + codec);
  }

  public void release() {}
}

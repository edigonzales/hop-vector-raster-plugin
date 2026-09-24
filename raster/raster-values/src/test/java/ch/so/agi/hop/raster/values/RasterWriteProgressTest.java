package ch.so.agi.hop.raster.values;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class RasterWriteProgressTest {
  @Test
  void emitsBasicMessagesAtMostOncePerTenPercentMilestone() {
    var messages = new ArrayList<String>();
    var progress = new RasterWriteProgress(messages::add);
    var output = Path.of("/tmp/output.tif");
    progress.started(output, "Deflate");
    for (int value : new int[] {0, 9, 10, 10, 19, 20, 33, 99, 100, 100, 150})
      progress.report(value);
    progress.completed();

    assertThat(messages).hasSize(7);
    assertThat(messages.subList(1, 6))
        .extracting(message -> message.substring(message.lastIndexOf(' ') + 1))
        .containsExactly("10%", "20%", "30%", "90%", "100%");
    assertThat(messages.get(0))
        .contains("writing " + output + " with Deflate compression");
    assertThat(messages.get(6))
        .contains("wrote " + output + " with Deflate compression in ");
  }
}

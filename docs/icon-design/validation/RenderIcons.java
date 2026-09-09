import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.hop.core.SwingUniversalImageSvg;
import org.apache.hop.core.svg.SvgSupport;

/** Render the actual assets with Hop's SVG loader and Swing/Batik renderer. */
public class RenderIcons {
  public static void main(String[] args) throws Exception {
    Path source = Path.of(args[0]);
    Path output = Path.of(args[1]);
    Files.createDirectories(output);
    List<Path> files;
    try (var stream = Files.list(source)) {
      files = stream.filter(p -> p.toString().endsWith(".svg")).sorted().toList();
    }
    int[] sizes = {16, 24, 32, 64};
    int rowHeight = 110;
    BufferedImage sheet = new BufferedImage(1080, 90 + files.size() * rowHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D page = sheet.createGraphics();
    page.setColor(new Color(0xF6F7F8));
    page.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
    page.setColor(new Color(0x0E3A5A));
    page.setFont(new Font("SansSerif", Font.BOLD, 20));
    page.drawString("Hop 2.17 · SVG render check", 24, 32);
    page.setFont(new Font("SansSerif", Font.PLAIN, 13));
    page.drawString("Original sizes: 16 / 24 / 32 / 64 px · light / dark / grayscale", 24, 57);
    int row = 0;
    for (Path file : files) {
      SwingUniversalImageSvg svg;
      try (InputStream in = Files.newInputStream(file)) {
        svg = new SwingUniversalImageSvg(SvgSupport.loadSvgImage(in));
      }
      int top = 80 + row++ * rowHeight;
      page.setColor(new Color(0x0E3A5A));
      page.setFont(new Font("SansSerif", Font.PLAIN, 12));
      page.drawString(file.getFileName().toString(), 24, top + 17);
      for (int sizeIndex = 0; sizeIndex < sizes.length; sizeIndex++) {
        int size = sizes[sizeIndex];
        BufferedImage raster = svg.getAsBitmapForSize(size, size);
        if (raster.getWidth() != size || raster.getHeight() != size) {
          throw new AssertionError("Unexpected raster dimensions: " + file);
        }
        int visible = 0;
        for (int y = 0; y < size; y++) {
          for (int x = 0; x < size; x++) {
            int alpha = raster.getRGB(x, y) >>> 24;
            if (alpha > 0) visible++;
            int margin = size / 16; // Two SVG units, rounded down at fractional pixel sizes.
            if (alpha > 0 && (x < margin || y < margin || x >= size - margin || y >= size - margin)) {
              throw new AssertionError("Paint exceeds the two-unit margin: " + file + " at " + size);
            }
          }
        }
        if (visible == 0 || visible == size * size) {
          throw new AssertionError("Empty image or opaque background: " + file);
        }
        ImageIO.write(raster, "png", output.resolve(file.getFileName() + "-" + size + ".png").toFile());
        for (int theme = 0; theme < 3; theme++) {
          int x = 295 + theme * 254 + sizeIndex * 60;
          page.setColor(new Color(theme == 1 ? 0x182832 : 0xFFFFFF));
          page.fillRect(x, top + 8, 68, 90);
          BufferedImage image = raster;
          if (theme == 2) {
            image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < size; py++) {
              for (int px = 0; px < size; px++) {
                int rgba = raster.getRGB(px, py);
                int gray = (int) Math.round(.2126 * (rgba >> 16 & 255) + .7152 * (rgba >> 8 & 255) + .0722 * (rgba & 255));
                image.setRGB(px, py, (rgba & 0xff000000) | gray << 16 | gray << 8 | gray);
              }
            }
          }
          page.drawImage(image, x + (68 - size) / 2, top + 16 + (64 - size) / 2, null);
          page.setColor(new Color(theme == 1 ? 0xD8E4EC : 0x536776));
          page.drawString(Integer.toString(size), x + 27, top + 92);
        }
      }
      System.out.println("PASS " + file.getFileName() + " · 16/24/32/64 px");
    }
    page.dispose();
    ImageIO.write(sheet, "png", output.resolve("contact-sheet.png").toFile());
    System.out.println("Rendered " + files.size() * sizes.length + " transparent images with Hop; contact sheet: " + output.resolve("contact-sheet.png"));
  }
}

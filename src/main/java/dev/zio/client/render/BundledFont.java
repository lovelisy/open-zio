/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.ZioClient;
import java.awt.Font;
import java.io.InputStream;

public final class BundledFont {
   private static final String RESOURCE = "/assets/zio/fonts/misans-medium.ttf";
   private static volatile Font base;
   private static volatile boolean attempted;

   private BundledFont() {
   }

   public static synchronized Font awtFont(int style, int size) {
      Font font = load();
      if (font == null) {
         throw new IllegalStateException("Bundled font is unavailable: " + RESOURCE);
      }
      return font.deriveFont(style, (float)size);
   }

   private static Font load() {
      if (base != null) {
         return base;
      }
      if (attempted) {
         return null;
      }
      attempted = true;
      try (InputStream stream = BundledFont.class.getResourceAsStream(RESOURCE)) {
         if (stream == null) {
            ZioClient.LOGGER.warn("Bundled font {} is missing", RESOURCE);
            return null;
         }
         base = Font.createFont(Font.TRUETYPE_FONT, stream);
         return base;
      } catch (Throwable error) {
         ZioClient.LOGGER.warn("Failed to load bundled font {}", RESOURCE, error);
         return null;
      }
   }
}

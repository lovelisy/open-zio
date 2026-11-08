package dev.zio.client.render;

import dev.zio.client.ZioClient;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;

public final class BundledFont {
   public static final String NAME = "MiSans Medium";
   private static final String RESOURCE = "/assets/zio/fonts/misans-medium.ttf";
   private static volatile Font base;
   private static volatile ByteBuffer buffer;
   private static volatile boolean attempted;

   private BundledFont() {
   }

   public static synchronized boolean initialize() {
      if (buffer != null) {
         return true;
      }
      if (attempted) {
         return false;
      }
      attempted = true;
      try (InputStream stream = ZioClient.class.getResourceAsStream(RESOURCE)) {
         if (stream == null) {
            ZioClient.LOGGER.warn("Bundled font {} is missing", RESOURCE);
            return false;
         }
         byte[] bytes = stream.readAllBytes();
         ByteBuffer direct = BufferUtils.createByteBuffer(bytes.length);
         direct.put(bytes).flip();
         buffer = direct;
         try {
            Font loaded = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes));
            base = loaded;
            try {
               GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
            } catch (Throwable ignoredFailure) {
            }
         } catch (Throwable ignoredFailure) {
            base = null;
         }
         return true;
      } catch (Throwable error) {
         ZioClient.LOGGER.warn("Failed to load bundled font {}", RESOURCE, error);
         return false;
      }
   }

   public static ByteBuffer buffer() {
      return initialize() ? buffer : null;
   }

   public static Font awtFont(int style, int size) {
      initialize();
      if (base != null) {
         return base.deriveFont(style, (float)size);
      }
      return new Font(Font.SANS_SERIF, style, size);
   }
}

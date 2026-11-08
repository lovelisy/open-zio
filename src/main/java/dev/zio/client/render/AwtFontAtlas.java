/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.zio.mixin.NativeImageAccessor;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryUtil;

final class AwtFontAtlas {
   static final int WIDTH = 2048;
   static final int HEIGHT = 1024;
   private static final int PADDING = 2;
   private static final int ADVANCE_INSET = 8;

   private final Font font;
   private final Font cjkFont;
   private final FontRenderContext context = new FontRenderContext(new AffineTransform(), true, true);
   private final FontMetrics metrics;
   private final FontMetrics cjkMetrics;
   private final int rowHeight;
   private final int baseline;
   private final int cjkWidth;
   private final BufferedImage canvas;
   private final Graphics2D graphics;
   private final int[] pixels;
   private int pendingCount;

   private int cursorX;
   private int cursorY;

   AwtFontAtlas(Font font) {
      this.font = font;
      this.cjkFont = font.canDisplay('\u4e2d') ? font : new Font(Font.SANS_SERIF, font.getStyle(), font.getSize());
      this.metrics = metrics(font);
      this.cjkMetrics = this.cjkFont == font ? this.metrics : metrics(this.cjkFont);
      this.rowHeight = align(Math.max(this.metrics.getHeight(), this.cjkMetrics.getHeight()), 2);
      this.baseline = Math.max(this.metrics.getAscent(), this.cjkMetrics.getAscent());
      this.cjkWidth = align(font.getSize() + ADVANCE_INSET, 2);
      this.canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
      this.graphics = this.canvas.createGraphics();
      this.graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      this.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      this.graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      this.graphics.setColor(Color.WHITE);
      this.pixels = ((DataBufferInt)this.canvas.getRaster().getDataBuffer()).getData();
   }

   int getRowHeight() {
      return this.rowHeight;
   }

   synchronized boolean hasPending() {
      return this.pendingCount > 0;
   }

   void flushInto(NativeImage image) {
      synchronized (this) {
         this.pendingCount = 0;
         IntBuffer target = MemoryUtil.memIntBuffer(
            ((NativeImageAccessor)(Object)image).zio$getPointer(), WIDTH * HEIGHT
         );
         for (int index = 0; index < this.pixels.length; index++) {
            int alpha = this.pixels[index] >>> 24;
            target.put(index, alpha << 24 | 0x00FFFFFF);
         }
      }
   }

   synchronized FontAtlas.Glyph createGlyph(char character) {
      Font selected = this.font.canDisplay(character) ? this.font : this.cjkFont;
      if (!selected.canDisplay(character)) {
         return null;
      }
      int advance = this.advance(selected, character);
      if (advance <= 0) {
         return null;
      }
      int width = align(advance + ADVANCE_INSET, 2);
      if (this.cursorX > 0 && this.cursorX + width > WIDTH) {
         this.cursorX = 0;
         this.cursorY += this.rowHeight + PADDING;
      }
      if (this.cursorY + this.rowHeight > HEIGHT) {
         return null;
      }
      int x = this.cursorX;
      int y = this.cursorY;
      this.graphics.setFont(selected);
      this.graphics.drawString(String.valueOf(character), x, y + this.baseline);
      this.cursorX += width + PADDING;
      this.pendingCount++;
      return new FontAtlas.Glyph((float)x, (float)y, (float)width, (float)this.rowHeight, (float)advance);
   }

   private int advance(Font selected, char character) {
      if (isCjk(character)) {
         return this.cjkWidth - ADVANCE_INSET;
      }
      try {
         FontMetrics selectedMetrics = selected == this.cjkFont ? this.cjkMetrics : this.metrics;
         int advance = selectedMetrics.charWidth(character);
         if (advance > 0) {
            return advance;
         }
         Rectangle2D bounds = selected.getStringBounds(String.valueOf(character), this.context);
         if (bounds.getWidth() <= 0.0D) {
            return 0;
         }
         return (int)Math.ceil(bounds.getWidth());
      } catch (Throwable error) {
         return 0;
      }
   }

   synchronized void dispose() {
      this.graphics.dispose();
      this.canvas.flush();
   }

   private static FontMetrics metrics(Font font) {
      BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = image.createGraphics();
      try {
         graphics.setFont(font);
         return graphics.getFontMetrics();
      } finally {
         graphics.dispose();
         image.flush();
      }
   }

   private static boolean isCjk(char character) {
      return character >= 0x2E80 && character <= 0x9FFF
         || character >= 0xF900 && character <= 0xFAFF
         || character >= 0xFE30 && character <= 0xFE4F
         || character >= 0xFF00 && character <= 0xFFEF;
   }

   private static int align(int value, int alignment) {
      return (value + alignment - 1) / alignment * alignment;
   }
}

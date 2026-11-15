/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Font;
import net.minecraft.util.FormattedCharSequence;

public final class AtlasTextRenderer {
   private float scale = FontAtlas.LAYOUT_SCALE;

   private final AtlasStringSplitter splitter = new AtlasStringSplitter(this);
   private final FontAtlas atlas;

   public AtlasTextRenderer(FontAtlas atlas) {
      this.atlas = atlas;
   }

   public void flush() {
      this.atlas.flush();
   }

   public void release() {
      this.atlas.release();
   }

   public AtlasStringSplitter getSplitter() {
      return this.splitter;
   }

   public int getHeight() {
      return (int)(this.atlas.getHeight() * this.scale);
   }

   public int draw(PoseStack poses, FormattedCharSequence sequence, float x, float y, int argb, boolean shadow) {
      int endX;
      if (shadow) {
         endX = new FormattedTextRenderer(poses, argb, 0.25F, this.atlas, this.scale)
            .draw(sequence, x + FontRenderer.getShadowOffset(), y + FontRenderer.getShadowOffset());
         endX = Math.max(endX, new FormattedTextRenderer(poses, argb, 1.0F, this.atlas, this.scale).draw(sequence, x, y));
      } else {
         endX = new FormattedTextRenderer(poses, argb, 1.0F, this.atlas, this.scale).draw(sequence, x, y);
      }
      return endX;
   }

   public static AtlasTextRenderer create(int atlasPixelHeight, float renderScale) {
      Font awtFont = BundledFont.awtFont(Font.PLAIN, atlasPixelHeight);
      FontAtlas atlas = new FontAtlas(awtFont);
      AtlasTextRenderer renderer = new AtlasTextRenderer(atlas);
      renderer.setScale(renderScale);
      return renderer;
   }

   public void setScale(float scale) {
      if (scale > 0.0F) {
         this.scale = scale;
      }
   }

   public float getCharacterWidth(char character) {
      return this.atlas.getGlyphWidth(character) * this.scale;
   }

   public int getWidth(String text) {
      if (text == null) {
         return 0;
      }
      float width = 0.0F;
      boolean formattingCode = false;
      for (char character : text.toCharArray()) {
         if (formattingCode) {
            formattingCode = false;
         } else if (character == '\u00a7') {
            formattingCode = true;
         } else {
            width += this.atlas.getGlyphWidth(character);
         }
      }
      return (int)(width * this.scale + 1.0F);
   }
}

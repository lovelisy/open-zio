/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import java.awt.Font;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;

public final class AtlasTextRenderer {
   private static final Map<Character, ChatFormatting> FORMATTING_CODES = new Char2ObjectOpenHashMap<>();
   private static final ByteBufferBuilder byteBuffer = new ByteBufferBuilder(2048);
   private static final BufferSource immediateBuffers = MultiBufferSource.immediate(byteBuffer);
   private static final MultiBufferSource bufferSource = new MultiBufferSource() {
      @Override
      public VertexConsumer getBuffer(RenderType renderType) {
         return AtlasTextRenderer.getBuffer(renderType);
      }
   };
   private static RenderType lastRenderType;
   private float scale = FontAtlas.LAYOUT_SCALE;

   static {
      for (ChatFormatting format : ChatFormatting.values()) {
         FORMATTING_CODES.put(format.getChar(), format);
      }
   }

   private final AtlasStringSplitter splitter = new AtlasStringSplitter(this);
   private final FontAtlas atlas;
   private float cursorX;
   private float cursorY;

   public AtlasTextRenderer(FontAtlas atlas) {
      this.atlas = atlas;
   }

   public void flush() {
      this.atlas.flush();
      finishBuffers();
   }

   public void release() {
      this.atlas.release();
   }

   public static MultiBufferSource getBufferSource() {
      return bufferSource;
   }

   public AtlasStringSplitter getSplitter() {
      return this.splitter;
   }

   public int drawCentered(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x - this.getWidth(text) / 2.0F, (float)y, argb, true);
   }

   public int getHeight() {
      return (int)(this.atlas.getHeight() * this.scale);
   }

   public static void finishBuffers() {
      immediateBuffers.endBatch();
      lastRenderType = null;
   }

   public int draw(PoseStack poses, String text, float x, float y, int argb) {
      return this.draw(poses, text, x, y, argb, true);
   }

   public int draw(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x, (float)y, argb, true);
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

   public int draw(PoseStack poses, String text, float x, float y, int argb, boolean shadow) {
      if (!shadow) {
         return this.drawPass(poses, text, x, y, argb, false);
      }
      int shadowEnd = this.drawPass(
         poses, text, x + FontRenderer.getShadowOffset(), y + FontRenderer.getShadowOffset(), argb, true
      );
      return Math.max(shadowEnd, this.drawPass(poses, text, x, y, argb, false));
   }

   public static AtlasTextRenderer create(int atlasPixelHeight, float renderScale, FontEntry fontEntry) {
      Font awtFont = SystemFonts.awtFont(fontEntry, Font.PLAIN, atlasPixelHeight);
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

   public float getScale() {
      return this.scale;
   }

   private void renderString(PoseStack poses, String text, int baseColor) {
      int color = baseColor;
      poses.pushPose();
      try {
         poses.translate(FontRenderer.getTranslate(), FontRenderer.getShift(), 0.0F);
         poses.scale(this.scale, this.scale, 1.0F);
         boolean formattingCode = false;
         for (char character : text.toCharArray()) {
            if (formattingCode) {
               formattingCode = false;
               ChatFormatting format = FORMATTING_CODES.get(Character.toLowerCase(character));
               if (format == null || format == ChatFormatting.RESET) {
                  color = baseColor;
               } else if (format.getColor() != null) {
                  color = format.getColor();
               }
            } else if (character == '\u00a7') {
               formattingCode = true;
            } else {
               this.cursorX += this.atlas.drawGlyph(poses, character, this.cursorX, this.cursorY, color);
            }
         }
      } finally {
         poses.popPose();
      }
   }

   public float getCharacterWidth(char character) {
      return this.atlas.getGlyphWidth(character) * this.scale;
   }

   private int drawPass(PoseStack poses, String text, float x, float y, int argb, boolean shadowPass) {
      if (text == null) {
         return 0;
      }
      if ((argb >>> 24) == 0) {
         argb |= 0xFF000000;
      }
      if (shadowPass) {
         int alpha = argb & 0xFF000000;
         argb = alpha | ((argb & 0x00FCFCFC) >>> 2);
      }
      this.cursorX = x / this.scale;
      this.cursorY = y / this.scale;
      this.renderString(poses, text, argb);
      return (int)(this.cursorX * this.scale);
   }

   public int drawWithoutShadow(PoseStack poses, String text, float x, float y, int argb) {
      return this.draw(poses, text, x, y, argb, false);
   }

   public int drawWithoutShadow(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x, (float)y, argb, false);
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

   public int drawCenteredWithoutShadow(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x - this.getWidth(text) / 2.0F, (float)y, argb, false);
   }

   private static VertexConsumer getBuffer(RenderType renderType) {
      if (lastRenderType != null && lastRenderType != renderType) {
         GuiDrawUtils.flush();
      }
      lastRenderType = renderType;
      return immediateBuffers.getBuffer(renderType);
   }
}

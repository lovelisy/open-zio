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
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

public final class FormattedTextRenderer implements FormattedCharSink {
   private final int brightness;
   private final FontAtlas atlas;
   private final float scale;
   private final int baseColor;
   private final PoseStack poses;
   private float cursorX;
   private float cursorY;

   public FormattedTextRenderer(PoseStack poses, int baseColor, float brightness, FontAtlas atlas, float scale) {
      this.poses = poses;
      this.baseColor = baseColor;
      this.brightness = (int)(brightness * 255.0F);
      this.atlas = atlas;
      this.scale = scale;
   }

   @Override
   public boolean accept(int index, Style style, int codePoint) {
      TextColor textColor = style.getColor();
      int color = textColor == null
         ? this.baseColor
         : ARGB.color(ARGB.alpha(this.baseColor), textColor.getValue());
      if (this.brightness != 255) {
         color = ColorUtils.modulateRgb(color, this.brightness, this.brightness, this.brightness);
      }
      this.cursorX += this.atlas.drawGlyph(this.poses, (char)codePoint, this.cursorX, this.cursorY, color);
      return true;
   }

   int draw(FormattedCharSequence sequence, float x, float y) {
      if (sequence == null) {
         return 0;
      }
      this.cursorX = x / this.scale;
      this.cursorY = y / this.scale;
      this.poses.pushPose();
      try {
         this.poses.translate(FontRenderer.getTranslate(), FontRenderer.getShift(), 0.0F);
         this.poses.scale(this.scale, this.scale, 1.0F);
         sequence.accept(this);
      } finally {
         this.poses.popPose();
      }
      return (int)(this.cursorX * this.scale);
   }
}

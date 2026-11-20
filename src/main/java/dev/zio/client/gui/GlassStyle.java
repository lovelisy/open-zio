/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui;

import dev.zio.client.ClientContext;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.audio.SoundClip;
import dev.zio.client.modules.client.UIModule;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.GuiShapes;
import dev.zio.client.render.GuiDrawUtils;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.network.chat.Component;

public final class GlassStyle {
   public static final float RADIUS = 9.0F;
   public static final int HEADER_HEIGHT = 18;
   public static final float ROW_RADIUS = 5.5F;
   private static final int BODY_TOP = 0x6A23272F;
   private static final int BODY_BOTTOM = 0x8615181D;
   private static final int OUTLINE_TOP = 0x50FFFFFF;
   private static final int OUTLINE_BOTTOM = 0x14FFFFFF;
   private static final int DIVIDER = 0x1FFFFFFF;
   private static final int TITLE = 0xFFF3F4F7;

   private GlassStyle() {
   }

   public static boolean isActive() {
      return UIModule.INSTANCE != null
         && UIModule.INSTANCE.guiStyleSetting != null
         && UIModule.INSTANCE.guiStyleSetting.getValue() == UIModule.GuiStyle.GLASS;
   }

   public static void play(BuiltinSound sound, float volume) {
      if (!isActive()) {
         return;
      }

      UIModule ui = UIModule.INSTANCE;
      if (ui == null || ui.guiSoundSetting == null || !ui.guiSoundSetting.getValue()) {
         return;
      }

      float level = volume * ui.guiVolumeSetting.getValue();
      if (level <= 0.01F || ClientContext.soundManager == null) {
         return;
      }

      SoundClip clip = ClientContext.soundManager.getSound(sound.getIdentifier());
      if (clip != null) {
         clip.play(level);
      }
   }

   public static int accent(float alpha) {
      Color color = UIModule.INSTANCE.colorSetting.getValue();
      return ColorUtils.toArgb(color, alpha);
   }

   public static void panel(PoseStack poses, float x, float y, float w, float h, float headerHeight) {
      if (w <= 0.0F || h <= 0.0F) {
         return;
      }

      GuiShapes.fillGradient(poses, x, y, w, h, RADIUS, BODY_TOP, BODY_BOTTOM);
      GuiShapes.fill(poses, x + RADIUS, y + 0.5F, w - RADIUS * 2.0F, 0.5F, 0.0F, 0x38FFFFFF);
      float tintedHeader = Math.min(headerHeight, h);
      GuiShapes.fillGradient(poses, x, y, w, tintedHeader, RADIUS, RADIUS, 0.0F, 0.0F, accent(0.16F), accent(0.0F));
      GuiShapes.fillGradient(
         poses, x, y, w, tintedHeader * 0.55F, RADIUS, RADIUS, 0.0F, 0.0F, 0x14FFFFFF, 0x00FFFFFF
      );

      if (h > headerHeight + 1.0F) {
         GuiShapes.fill(poses, x + 8.0F, y + headerHeight - 0.5F, w - 16.0F, 0.5F, 0.0F, DIVIDER);
      }
   }

   public static void title(
      PoseStack poses,
      float x,
      float y,
      float w,
      float headerHeight,
      String text,
      float alpha,
      String status,
      boolean expanded
   ) {
      float centerY = y + headerHeight / 2.0F;
      GuiShapes.glow(poses, x + 7.0F - 2.5F, centerY - 2.5F, 5.0F, 5.0F, 2.5F, 3.0F, accent(0.4F * alpha));
      GuiShapes.fill(poses, x + 7.0F - 2.0F, centerY - 2.0F, 4.0F, 4.0F, 2.0F, accent(alpha));
      int titleColor = (int)(0xFF * alpha) << 24 | TITLE & 0xFFFFFF;
      FontRenderer.INSTANCE
         .drawWithShadow(
            poses,
            Component.nullToEmpty(text),
            x + 14.0F,
            centerY - FontRenderer.INSTANCE.getHeight() / 2.0F,
            titleColor
         );

      float arrowX = x + w - 9.0F;
      int arrowColor = (int)(0xB0 * alpha) << 24 | 0xFFFFFF;
      if (expanded) {
         GuiShapes.triangle(
            poses, arrowX - 2.5F, centerY - 1.5F, arrowX + 2.5F, centerY - 1.5F, arrowX, centerY + 1.5F, arrowColor
         );
      } else {
         GuiShapes.triangle(
            poses, arrowX - 1.5F, centerY - 2.5F, arrowX - 1.5F, centerY + 2.5F, arrowX + 1.5F, centerY, arrowColor
         );
      }

      if (status != null && !status.isEmpty()) {
         float statusRight = arrowX - 6.0F;
         float statusLeft = statusRight - FontRenderer.INSTANCE.getWidth(status);
         if (statusLeft > x + 14.0F + FontRenderer.INSTANCE.getWidth(text) + 4.0F) {
            FontRenderer.INSTANCE
               .drawWithShadow(
                  poses,
                  Component.nullToEmpty(status),
                  statusLeft,
                  centerY - FontRenderer.INSTANCE.getHeight() / 2.0F,
                  (int)(0x8C * alpha) << 24 | 0xA7ADBA
               );
         }
      }
   }

   public static void outline(PoseStack poses, float x, float y, float w, float h) {
      GuiShapes.outline(poses, x, y, w, h, RADIUS, 0.75F, OUTLINE_TOP, OUTLINE_BOTTOM);
   }

   public static void scrollbar(
      PoseStack poses, float x, float y, float w, float height, float headerHeight, int scrollOffset, int contentHeight
   ) {
      int maxScroll = Math.min(-contentHeight + (int)height, 0);
      if (maxScroll >= 0) {
         return;
      }

      float trackTop = y + headerHeight + 4.0F;
      float trackHeight = height - headerHeight - 8.0F;
      if (trackHeight <= 4.0F) {
         return;
      }

      float progress = Math.max(0.0F, Math.min(1.0F, scrollOffset / (float)maxScroll));
      float thumbHeight = Math.max(12.0F, trackHeight * height / contentHeight);
      float thumbY = trackTop + (trackHeight - thumbHeight) * progress;
      GuiShapes.fill(poses, x + w - 4.5F, thumbY, 2.0F, thumbHeight, 1.0F, 0x55FFFFFF);
   }

   public static void moduleRow(
      PoseStack poses, float x, float y, float w, float h, float enabledProgress, float hoverProgress
   ) {
      float rowX = x + 3.0F;
      float rowWidth = w - 6.0F;
      float rowY = y + 1.0F;
      float rowHeight = h - 2.0F;
      if (enabledProgress > 0.01F) {
         GuiShapes.fillGradient(
            poses, rowX, rowY, rowWidth, rowHeight, ROW_RADIUS, accent(0.72F * enabledProgress), accent(0.5F * enabledProgress)
         );
         int sheen = (int)(0x1F * enabledProgress) << 24 | 0xFFFFFF;
         GuiShapes.fillGradient(poses, rowX, rowY, rowWidth, rowHeight * 0.5F, ROW_RADIUS, sheen, 0x00FFFFFF);
      }

      if (hoverProgress > 0.01F) {
         int wash = (int)(0x26 * hoverProgress) << 24 | 0xFFFFFF;
         GuiShapes.fill(poses, rowX, rowY, rowWidth, rowHeight, ROW_RADIUS, wash);
         GuiShapes.outline(
            poses, rowX, rowY, rowWidth, rowHeight, ROW_RADIUS, 0.6F,
            accent(0.12F * hoverProgress), accent(0.03F * hoverProgress)
         );
      }
   }

   public static void settingProgress(
      PoseStack poses, float x, float y, float w, float h, float progress
   ) {
      float width = Math.max(0.0F, Math.min(1.0F, progress)) * (w - 8.0F);
      if (width < 3.0F || h <= 2.0F) {
         return;
      }

      GuiShapes.fillGradient(
         poses, x + 4.0F, y + 1.0F, width, h - 2.0F, ROW_RADIUS - 1.0F, accent(0.78F), accent(0.52F)
      );
   }

   public static void highlight(
      PoseStack poses, float x, float y, float w, float h, int color
   ) {
      GuiShapes.fill(poses, x + 3.0F, y + 1.0F, w - 6.0F, h - 2.0F, ROW_RADIUS, color);
   }

   public static void settingRow(
      PoseStack poses, float x, float y, float w, float h, float enabledProgress, float hoverProgress
   ) {
      float rowX = x + 3.0F;
      float rowWidth = w - 6.0F;
      float rowY = y + 1.0F;
      float rowHeight = h - 2.0F;
      if (enabledProgress > 0.01F) {
         GuiShapes.fillGradient(
            poses,
            rowX,
            rowY,
            rowWidth,
            rowHeight,
            ROW_RADIUS - 1.0F,
            accent(0.13F * enabledProgress),
            accent(0.07F * enabledProgress)
         );
      }

      if (hoverProgress > 0.01F) {
         int wash = (int)(0x1A * hoverProgress) << 24 | 0xFFFFFF;
         GuiShapes.fill(poses, rowX, rowY, rowWidth, rowHeight, ROW_RADIUS - 1.0F, wash);
      }
   }

   public static void enumList(
      PoseStack poses, float x, float y, float w, float h
   ) {
      if (w <= 0.0F || h <= 0.0F) {
         return;
      }

      GuiShapes.fill(poses, x + 4.0F, y + 1.0F, w - 8.0F, h - 2.0F, 6.0F, 0x30000000);
      GuiShapes.outline(
         poses, x + 4.0F, y + 1.0F, w - 8.0F, h - 2.0F, 6.0F, 0.6F,
         0x06FFFFFF, 0x1CFFFFFF
      );
   }

   public static void searchBar(
      PoseStack poses, float x, float y, float w, float h, float glow
   ) {
      if (w <= 0.0F || h <= 0.0F) {
         return;
      }

      GuiShapes.fillGradient(poses, x, y, w, h, h / 2.0F, BODY_TOP, BODY_BOTTOM);
      GuiShapes.fill(poses, x + h / 2.0F, y + 0.5F, w - h, 0.5F, 0.0F, 0x30FFFFFF);
      if (glow > 0.01F) {
         GuiShapes.glow(
            poses, x - 2.0F, y - 2.0F, w + 4.0F, h + 4.0F, (h + 4.0F) / 2.0F, 8.0F,
            accent(0.28F * glow)
         );
      }

      GuiShapes.outline(
         poses, x, y, w, h, h / 2.0F, 0.75F,
         glow > 0.01F ? accent(0.85F * glow) : OUTLINE_TOP,
         glow > 0.01F ? accent(0.35F * glow) : OUTLINE_BOTTOM
      );

      float iconCenterX = x + 11.0F;
      float iconCenterY = y + h / 2.0F;
      int iconColor = glow > 0.01F ? 0xEEFFFFFF : 0x99A7ADBA;
      float radius = 3.0F;
      GuiShapes.outline(
         poses, iconCenterX - radius - 2.0F, iconCenterY - radius - 1.5F,
         radius * 2.0F, radius * 2.0F, radius, 0.8F, iconColor, iconColor
      );
      segment(poses, iconCenterX + 1.2F, iconCenterY + 1.8F, iconCenterX + 3.8F, iconCenterY + 4.2F, 1.0F, iconColor);
   }

   public static void sheen(
      PoseStack poses, float x, float y, float w, float h, float progress
   ) {
      if (progress <= 0.0F || progress >= 1.0F || w <= 0.0F || h <= 0.0F) {
         return;
      }

      float span = w + h * 0.6F;
      float bandWidth = Math.max(10.0F, span * 0.18F);
      float position = -span * 0.5F - bandWidth + (span + bandWidth * 2.0F) * progress;
      float left = x + span * 0.5F + position;
      float right = left + bandWidth;
      if (right <= x || left >= x + w) {
         return;
      }

      int fade = (int)(0x1E * (1.0F - Math.abs(progress - 0.5F) * 2.0F)) << 24;
      if ((fade >>> 24) <= 0) {
         return;
      }

      float top = y + 1.0F;
      float bottom = y + h - 1.0F;
      int clear = 0x00FFFFFF;
      int soft = fade | 0xFFFFFF;
      GuiDrawUtils.fillQuad(
         poses, left, top, left + bandWidth * 0.45F, top, left + bandWidth * 0.45F, bottom, left, bottom,
         clear, soft, soft, clear
      );
      GuiDrawUtils.fillQuad(
         poses, left + bandWidth * 0.45F, top, right, top, right, bottom, left + bandWidth * 0.45F, bottom,
         soft, clear, clear, soft
      );
   }

   public static void optionHover(
      PoseStack poses, float x, float y, float w, float h, float progress
   ) {
      if (progress <= 0.01F) {
         return;
      }

      int wash = (int)(0x1E * progress) << 24 | 0xFFFFFF;
      GuiShapes.fill(poses, x + 5.0F, y + 0.5F, w - 10.0F, h - 1.0F, 4.0F, wash);
   }

   public static void optionDot(PoseStack poses, float x, float y, float alpha) {
      GuiShapes.glow(poses, x - 2.2F, y - 2.2F, 4.4F, 4.4F, 2.2F, 2.5F, accent(0.5F * alpha));
      GuiShapes.fill(poses, x - 1.8F, y - 1.8F, 3.6F, 3.6F, 1.8F, accent(alpha));
   }

   public static void chevron(
      PoseStack poses, float centerX, float centerY, float size, float rotationDegrees, float thickness, int color
   ) {
      double angle = Math.toRadians(rotationDegrees);
      float cos = (float)Math.cos(angle);
      float sin = (float)Math.sin(angle);
      segment(
         poses,
         centerX + (-size * cos - -size * 0.5F * sin),
         centerY + (-size * sin + -size * 0.5F * cos),
         centerX + (0.0F * cos - size * 0.5F * sin),
         centerY + (0.0F * sin + size * 0.5F * cos),
         thickness,
         color
      );
      segment(
         poses,
         centerX + (0.0F * cos - size * 0.5F * sin),
         centerY + (0.0F * sin + size * 0.5F * cos),
         centerX + (size * cos - -size * 0.5F * sin),
         centerY + (size * sin + -size * 0.5F * cos),
         thickness,
         color
      );
   }

   private static void segment(
      PoseStack poses, float x0, float y0, float x1, float y1, float thickness, int color
   ) {
      float dx = x1 - x0;
      float dy = y1 - y0;
      float length = (float)Math.sqrt(dx * dx + dy * dy);
      if (length <= 0.0F) {
         return;
      }

      float nx = -dy / length * thickness / 2.0F;
      float ny = dx / length * thickness / 2.0F;
      GuiDrawUtils.fillQuad(
         poses, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny,
         color, color, color, color
      );
   }
}

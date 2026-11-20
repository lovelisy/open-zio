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

public final class GuiShapes {
   private static final int SEGMENTS = 8;
   private static final float STEP = 1.0F;

   private GuiShapes() {
   }

   public static void fill(PoseStack poses, float x, float y, float w, float h, float radius, int color) {
      fillGradient(poses, x, y, w, h, radius, radius, radius, radius, color, color);
   }

   public static void fillGradient(
      PoseStack poses, float x, float y, float w, float h, float radius, int topColor, int bottomColor
   ) {
      fillGradient(poses, x, y, w, h, radius, radius, radius, radius, topColor, bottomColor);
   }

   public static void fillGradient(
      PoseStack poses,
      float x,
      float y,
      float w,
      float h,
      float topLeft,
      float topRight,
      float bottomRight,
      float bottomLeft,
      int topColor,
      int bottomColor
   ) {
      if (w <= 0.0F || h <= 0.0F) {
         return;
      }

      float limit = Math.min(w, h) / 2.0F;
      topLeft = Math.max(0.0F, Math.min(topLeft, limit));
      topRight = Math.max(0.0F, Math.min(topRight, limit));
      bottomRight = Math.max(0.0F, Math.min(bottomRight, limit));
      bottomLeft = Math.max(0.0F, Math.min(bottomLeft, limit));
      if (topLeft <= 0.0F && topRight <= 0.0F && bottomRight <= 0.0F && bottomLeft <= 0.0F) {
         GuiDrawUtils.fillQuad(
            poses, x, y, x + w, y, x + w, y + h, x, y + h, topColor, topColor, bottomColor, bottomColor
         );
         return;
      }

      float step = STEP;
      float topBand = Math.min(Math.max(topLeft, topRight), h / 2.0F);
      float bottomBand = Math.min(Math.max(bottomLeft, bottomRight), h - topBand);

      for (float row = 0.0F; row < topBand; row += step) {
         float y0 = row;
         float y1 = Math.min(topBand, row + step);
         float left0 = x + leftInset(y0, h, topLeft, bottomLeft);
         float left1 = x + leftInset(y1, h, topLeft, bottomLeft);
         float right0 = x + w - rightInset(y0, h, topRight, bottomRight);
         float right1 = x + w - rightInset(y1, h, topRight, bottomRight);
         int color0 = lerpColor(topColor, bottomColor, y0 / h);
         int color1 = lerpColor(topColor, bottomColor, y1 / h);
         GuiDrawUtils.fillQuad(
            poses, left0, y + y0, right0, y + y0, right1, y + y1, left1, y + y1, color0, color0, color1, color1
         );
      }

      float bodyTop = topBand;
      float bodyBottom = h - bottomBand;
      if (bodyBottom > bodyTop) {
         int color0 = lerpColor(topColor, bottomColor, bodyTop / h);
         int color1 = lerpColor(topColor, bottomColor, bodyBottom / h);
         GuiDrawUtils.fillQuad(
            poses, x, y + bodyTop, x + w, y + bodyTop, x + w, y + bodyBottom, x, y + bodyBottom, color0, color0, color1, color1
         );
      }

      for (float row = h - bottomBand; row < h; row += step) {
         float y0 = row;
         float y1 = Math.min(h, row + step);
         float left0 = x + leftInset(y0, h, topLeft, bottomLeft);
         float left1 = x + leftInset(y1, h, topLeft, bottomLeft);
         float right0 = x + w - rightInset(y0, h, topRight, bottomRight);
         float right1 = x + w - rightInset(y1, h, topRight, bottomRight);
         int color0 = lerpColor(topColor, bottomColor, y0 / h);
         int color1 = lerpColor(topColor, bottomColor, y1 / h);
         GuiDrawUtils.fillQuad(
            poses, left0, y + y0, right0, y + y0, right1, y + y1, left1, y + y1, color0, color0, color1, color1
         );
      }
   }

   public static void outline(
      PoseStack poses, float x, float y, float w, float h, float radius, float thickness, int topColor, int bottomColor
   ) {
      if (w <= 0.0F || h <= 0.0F || thickness <= 0.0F) {
         return;
      }

      float limit = Math.min(w, h) / 2.0F;
      radius = Math.min(radius, limit);
      thickness = Math.min(thickness, limit);
      float innerRadius = Math.max(0.0F, radius - thickness);
      float[] outer = perimeter(x, y, w, h, radius);
      float[] inner = perimeter(x + thickness, y + thickness, w - thickness * 2.0F, h - thickness * 2.0F, innerRadius);
      int points = outer.length / 2;

      for (int index = 0; index < points; index++) {
         int next = (index + 1) % points;
         float ox0 = outer[index * 2];
         float oy0 = outer[index * 2 + 1];
         float ox1 = outer[next * 2];
         float oy1 = outer[next * 2 + 1];
         float ix0 = inner[index * 2];
         float iy0 = inner[index * 2 + 1];
         float ix1 = inner[next * 2];
         float iy1 = inner[next * 2 + 1];
         int color0 = lerpColor(topColor, bottomColor, Math.max(0.0F, Math.min(1.0F, (oy0 - y) / h)));
         int color1 = lerpColor(topColor, bottomColor, Math.max(0.0F, Math.min(1.0F, (oy1 - y) / h)));
         GuiDrawUtils.fillQuad(poses, ox0, oy0, ox1, oy1, ix1, iy1, ix0, iy0, color0, color1, color1, color0);
      }
   }

   public static void triangle(
      PoseStack poses, float x0, float y0, float x1, float y1, float x2, float y2, int color
   ) {
      GuiDrawUtils.fillQuad(poses, x0, y0, x1, y1, x2, y2, x2, y2, color, color, color, color);
   }

   public static void glow(
      PoseStack poses, float x, float y, float w, float h, float radius, float spread, int color
   ) {
      if (w <= 0.0F || h <= 0.0F || spread <= 0.0F || (color >>> 24) == 0) {
         return;
      }

      int alpha = (color >>> 24) / 4;
      if (alpha <= 0) {
         return;
      }

      int tint = alpha << 24 | color & 0xFFFFFF;

      for (int layer = 3; layer >= 1; layer--) {
         float expand = spread * layer / 3.0F;
         fill(poses, x - expand, y - expand, w + expand * 2.0F, h + expand * 2.0F, radius + expand, tint);
      }
   }

   private static float leftInset(float y, float h, float top, float bottom) {
      if (top > 0.0F && y < top) {
         return arcInset(top, y);
      }

      if (bottom > 0.0F && y > h - bottom) {
         return arcInset(bottom, h - y);
      }

      return 0.0F;
   }

   private static float rightInset(float y, float h, float top, float bottom) {
      return leftInset(y, h, top, bottom);
   }

   private static float arcInset(float radius, float distance) {
      float delta = radius - distance;
      return radius - (float)Math.sqrt(Math.max(0.0F, radius * radius - delta * delta));
   }

   private static float[] perimeter(float x, float y, float w, float h, float radius) {
      radius = Math.max(0.0F, Math.min(radius, Math.min(w, h) / 2.0F));
      float[] points = new float[(SEGMENTS + 1) * 4 * 2];
      int cursor = 0;
      cursor = arc(points, cursor, x + radius, y + radius, radius, Math.PI, Math.PI * 1.5);
      cursor = arc(points, cursor, x + w - radius, y + radius, radius, Math.PI * 1.5, Math.PI * 2.0);
      cursor = arc(points, cursor, x + w - radius, y + h - radius, radius, 0.0, Math.PI * 0.5);
      arc(points, cursor, x + radius, y + h - radius, radius, Math.PI * 0.5, Math.PI);
      return points;
   }

   private static int arc(float[] points, int cursor, float cx, float cy, float radius, double from, double to) {
      for (int step = 0; step <= SEGMENTS; step++) {
         double angle = from + (to - from) * step / SEGMENTS;
         points[cursor++] = cx + (float)(Math.cos(angle) * radius);
         points[cursor++] = cy + (float)(Math.sin(angle) * radius);
      }

      return cursor;
   }

   private static int lerpColor(int from, int to, float progress) {
      int fromAlpha = from >>> 24;
      int fromRed = from >> 16 & 0xFF;
      int fromGreen = from >> 8 & 0xFF;
      int fromBlue = from & 0xFF;
      int toAlpha = to >>> 24;
      int toRed = to >> 16 & 0xFF;
      int toGreen = to >> 8 & 0xFF;
      int toBlue = to & 0xFF;
      int alpha = (int)(fromAlpha + (toAlpha - fromAlpha) * progress);
      int red = (int)(fromRed + (toRed - fromRed) * progress);
      int green = (int)(fromGreen + (toGreen - fromGreen) * progress);
      int blue = (int)(fromBlue + (toBlue - fromBlue) * progress);
      return alpha << 24 | red << 16 | green << 8 | blue;
   }
}

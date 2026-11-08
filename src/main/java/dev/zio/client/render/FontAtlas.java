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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.zio.client.ClientAccess;
import dev.zio.client.modules.client.FontsModule;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import java.awt.Font;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

public final class FontAtlas {
   public static final Identifier ATLAS_TEXTURE = Identifier.fromNamespaceAndPath("zio", "textures/font.png");
   public static final String ATLAS_LABEL = ATLAS_TEXTURE.toLanguageKey();
   public static final int LAYOUT_VALUE = 4;
   public static final int SECONDARY_LAYOUT_VALUE = 1;
   public static final float LAYOUT_SCALE = 0.25F;
   public static final int PIXEL_HEIGHT = 32;
   private static final Supplier<String> ATLAS_LABEL_SUPPLIER = new Supplier<String>() {
      @Override
      public String get() {
         return ATLAS_LABEL;
      }
   };
   private static final Glyph MISSING = new Glyph(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   private final Char2ObjectOpenHashMap<Glyph> glyphs = new Char2ObjectOpenHashMap<>();
   private final Char2ObjectOpenHashMap<Glyph> missing = new Char2ObjectOpenHashMap<>();
   private final MeshBuffer mesh = MeshBuffer.create(4194304);
   private final Font font;
   private AwtFontAtlas atlas;
   private MipmapFontTexture texture;
   private NativeImage pixels;
   private int fontHeight;

   public FontAtlas(Font font) {
      this.font = font;
      this.fontHeight = Math.max(1, Math.round(font.getSize2D()));
   }

   private AwtFontAtlas atlas() {
      if (this.atlas == null) {
         this.atlas = new AwtFontAtlas(this.font);
         this.fontHeight = Math.max(1, this.atlas.getRowHeight());
      }
      return this.atlas;
   }

   private void ensureTexture() {
      if (this.texture != null) {
         return;
      }
      this.pixels = new NativeImage(NativeImage.Format.RGBA, AwtFontAtlas.WIDTH, AwtFontAtlas.HEIGHT, false);
      this.texture = new MipmapFontTexture(ATLAS_LABEL_SUPPLIER, AwtFontAtlas.WIDTH, AwtFontAtlas.HEIGHT);
      ClientAccess.zioMinecraft.getTextureManager().register(ATLAS_TEXTURE, this.texture);
   }

   private void uploadPending() {
      MipmapFontTexture target = this.texture;
      if (target == null || this.pixels == null) {
         return;
      }
      AwtFontAtlas source = this.atlas();
      if (!source.hasPending()) {
         return;
      }
      source.flushInto(this.pixels);
      target.upload(this.pixels);
   }

   private synchronized Glyph resolve(char character) {
      Glyph glyph = this.glyphs.get(character);
      if (glyph != null) {
         return glyph;
      }
      if (this.missing.containsKey(character)) {
         return null;
      }
      Glyph created = this.atlas().createGlyph(character);
      if (created == null) {
         this.missing.put(character, MISSING);
         return null;
      }
      this.glyphs.put(character, created);
      return created;
   }

   private Glyph fallback(char character) {
      if (character == '?') {
         return null;
      }
      Glyph glyph = this.glyphs.get('?');
      return glyph != null ? glyph : this.resolve('?');
   }

   public synchronized void flush() {
      boolean needsUpload = this.atlas != null && this.atlas.hasPending();
      if (!needsUpload && !this.mesh.isBuilding()) {
         return;
      }
      this.ensureTexture();
      this.uploadPending();
      if (this.mesh.isBuilding()) {
         boolean antiAlias = FontsModule.INSTANCE == null || FontsModule.INSTANCE.antiAliasSetting.getValue();
         this.mesh.draw(ClientRenderTypes.font(ATLAS_TEXTURE, antiAlias));
      }
   }

   public void release() {
      if (this.mesh.isBuilding()) {
         this.mesh.draw();
      }
      if (this.atlas != null) {
         this.atlas.dispose();
         this.atlas = null;
      }
      if (this.pixels != null) {
         this.pixels.close();
         this.pixels = null;
      }
      MipmapFontTexture owned = this.texture;
      this.texture = null;
      if (owned != null) {
         AbstractTexture current = ClientAccess.zioMinecraft.getTextureManager().getTexture(ATLAS_TEXTURE);
         if (current == owned) {
            ClientAccess.zioMinecraft.getTextureManager().release(ATLAS_TEXTURE);
         } else {
            owned.close();
         }
      }
      this.glyphs.clear();
      this.missing.clear();
   }

   public float drawGlyph(PoseStack poses, char character, float x, float y, int argb) {
      Glyph glyph = this.glyphs.get(character);
      if (glyph == null) {
         glyph = this.resolve(character);
      }
      if (glyph == null || glyph.advance() <= 0.0F) {
         Glyph fallback = this.fallback(character);
         return fallback == null ? 0.0F : fallback.advance();
      }
      if (!this.mesh.isBuilding()) {
         this.mesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
      }
      float z = (float)FontRenderer.getZOffset();
      float top = y;
      float bottom = y + glyph.height();
      float left = x;
      float right = x + glyph.width();
      float u0 = glyph.x() / (float)AwtFontAtlas.WIDTH;
      float u1 = (glyph.x() + glyph.width()) / (float)AwtFontAtlas.WIDTH;
      float v0 = glyph.y() / (float)AwtFontAtlas.HEIGHT;
      float v1 = (glyph.y() + glyph.height()) / (float)AwtFontAtlas.HEIGHT;
      this.mesh.addVertex(poses.last(), left, bottom, z).setColor(argb).setUv(u0, v1);
      this.mesh.addVertex(poses.last(), right, bottom, z).setColor(argb).setUv(u1, v1);
      this.mesh.addVertex(poses.last(), right, top, z).setColor(argb).setUv(u1, v0);
      this.mesh.addVertex(poses.last(), left, top, z).setColor(argb).setUv(u0, v0);
      return glyph.advance();
   }

   public float getGlyphWidth(char character) {
      Glyph glyph = this.glyphs.get(character);
      if (glyph == null) {
         glyph = this.resolve(character);
      }
      if (glyph == null) {
         Glyph fallback = this.fallback(character);
         return fallback == null ? 0.0F : fallback.advance();
      }
      return glyph.advance();
   }

   public int getHeight() {
      this.atlas();
      return this.fontHeight;
   }

   record Glyph(float x, float y, float width, float height, float advance) {
   }
}

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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

public final class FontAtlas {
   public static final Identifier ATLAS_TEXTURE = Identifier.fromNamespaceAndPath("zio", "textures/font.png");
   public static final String ATLAS_LABEL = ATLAS_TEXTURE.toLanguageKey();
   public static final int LAYOUT_VALUE = 4;
   public static final int SECONDARY_LAYOUT_VALUE = 1;
   public static final float LAYOUT_SCALE = 0.25F;
   public static final int PIXEL_HEIGHT = 32;
   private static final int MAX_PAGES = 64;
   private static final int PAGE_BUFFER_SIZE = 1048576;
   private static final Glyph MISSING = new Glyph(0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   private final Char2ObjectOpenHashMap<Glyph> glyphs = new Char2ObjectOpenHashMap<>();
   private final Char2ObjectOpenHashMap<Glyph> missing = new Char2ObjectOpenHashMap<>();
   private final List<Page> pages = new ArrayList<>();
   private final Font font;

   public FontAtlas(Font font) {
      this.font = font;
   }

   private Page createPage() {
      Page page = new Page(this.pages.size());
      this.pages.add(page);
      return page;
   }

   private Page currentPage() {
      if (this.pages.isEmpty()) {
         return this.createPage();
      }
      return this.pages.get(this.pages.size() - 1);
   }

   private void ensureTexture(Page page) {
      if (page.texture != null) {
         return;
      }
      page.pixels = new NativeImage(NativeImage.Format.RGBA, AwtFontAtlas.WIDTH, AwtFontAtlas.HEIGHT, false);
      page.texture = new MipmapFontTexture(() -> page.label, AwtFontAtlas.WIDTH, AwtFontAtlas.HEIGHT);
      ClientAccess.zioMinecraft.getTextureManager().register(page.textureId, page.texture);
   }

   private synchronized Glyph resolve(char character) {
      Glyph glyph = this.glyphs.get(character);
      if (glyph != null) {
         return glyph;
      }
      if (this.missing.containsKey(character)) {
         return null;
      }
      Page page = this.currentPage();
      Glyph created = page.atlas.createGlyph(character);
      if (created == null
         && page.atlas.isFull()
         && page.atlas.canDisplay(character)
         && this.pages.size() < MAX_PAGES) {
         page = this.createPage();
         created = page.atlas.createGlyph(character);
      }
      if (created == null) {
         this.missing.put(character, MISSING);
         return null;
      }
      Glyph wrapped = new Glyph(
         page.index,
         created.x(),
         created.y(),
         created.width(),
         created.height(),
         created.advance()
      );
      this.glyphs.put(character, wrapped);
      return wrapped;
   }

   private Glyph fallback(char character) {
      if (character == '?') {
         return null;
      }
      Glyph glyph = this.glyphs.get('?');
      return glyph != null ? glyph : this.resolve('?');
   }

   public synchronized void flush() {
      if (this.pages.isEmpty()) {
         return;
      }
      boolean antiAlias = FontsModule.INSTANCE == null || FontsModule.INSTANCE.antiAliasSetting.getValue();
      for (Page page : this.pages) {
         if (page.atlas.hasPending()) {
            this.ensureTexture(page);
            page.atlas.flushInto(page.pixels);
            page.texture.upload(page.pixels);
         }
         if (page.mesh.isBuilding()) {
            page.mesh.draw(ClientRenderTypes.font(page.textureId, antiAlias));
         }
      }
   }

   public synchronized void release() {
      for (Page page : this.pages) {
         if (page.mesh.isBuilding()) {
            page.mesh.draw();
         }
         page.atlas.dispose();
         if (page.pixels != null) {
            page.pixels.close();
            page.pixels = null;
         }
         MipmapFontTexture owned = page.texture;
         page.texture = null;
         if (owned != null) {
            AbstractTexture current = ClientAccess.zioMinecraft.getTextureManager().getTexture(page.textureId);
            if (current == owned) {
               ClientAccess.zioMinecraft.getTextureManager().release(page.textureId);
            } else {
               owned.close();
            }
         }
      }
      this.pages.clear();
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
      Page page = this.pages.get(glyph.page());
      if (!page.mesh.isBuilding()) {
         page.mesh.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
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
      page.mesh.addVertex(poses.last(), left, bottom, z).setColor(argb).setUv(u0, v1);
      page.mesh.addVertex(poses.last(), right, bottom, z).setColor(argb).setUv(u1, v1);
      page.mesh.addVertex(poses.last(), right, top, z).setColor(argb).setUv(u1, v0);
      page.mesh.addVertex(poses.last(), left, top, z).setColor(argb).setUv(u0, v0);
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
      return Math.max(1, this.currentPage().atlas.getRowHeight());
   }

   private final class Page {
      private final int index;
      private final Identifier textureId;
      private final String label;
      private final AwtFontAtlas atlas;
      private final MeshBuffer mesh;
      private MipmapFontTexture texture;
      private NativeImage pixels;

      private Page(int index) {
         this.index = index;
         this.textureId = index == 0
            ? ATLAS_TEXTURE
            : Identifier.fromNamespaceAndPath("zio", "textures/font_" + index + ".png");
         this.label = this.textureId.toLanguageKey();
         this.atlas = new AwtFontAtlas(FontAtlas.this.font);
         this.mesh = MeshBuffer.create(PAGE_BUFFER_SIZE);
      }
   }

   record Glyph(int page, float x, float y, float width, float height, float advance) {
   }
}

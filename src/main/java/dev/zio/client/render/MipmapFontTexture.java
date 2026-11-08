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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.AbstractTexture;

public final class MipmapFontTexture extends AbstractTexture {

   public MipmapFontTexture(Supplier<String> label, int width, int height) {
      this.texture = RenderSystem.getDevice().createTexture(label, 5, TextureFormat.RGBA8, width, height, 1, 1);
      this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
         FilterMode.LINEAR, FilterMode.LINEAR, true);
      this.textureView = RenderSystem.getDevice().createTextureView(this.texture, 0, 1);
   }

   public void upload(NativeImage image) {
      if (this.texture == null) {
         return;
      }
      RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, image);
   }
}

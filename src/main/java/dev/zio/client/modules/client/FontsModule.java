/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.client;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.MinecraftTickEvent;
import dev.zio.client.events.HudRenderEvent;
import dev.zio.client.events.ClientInitializationEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.render.GuiProjectionUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.AtlasTextRenderer;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

public class FontsModule extends Module {
   public static FontsModule INSTANCE;

   public final Setting<Integer> sizeSetting;
   public final Setting<Boolean> antiAliasSetting;
   public final Setting<Boolean> chatSetting;
   private final Setting<Boolean> offsetsSetting;
   public final Setting<Integer> shiftSetting;
   public final Setting<Integer> translateSetting;
   public final Setting<Float> shadowSetting;
   private static int reloadCooldownTicks;
   private final Vector2f transformedPosition;
   private final List<QueuedDraw> queuedDraws;

   public boolean hasReloadCooldown() {
      return reloadCooldownTicks > 0;
   }

   @Override
   public void onToggle() {
      reloadCooldownTicks = 3;
      if (!this.isToggled()) {
         FontRenderer.INSTANCE.flush();
         this.queuedDraws.clear();
      }
   }

   private void flushQueuedDraws(PoseStack poseStack) {
      if (!this.queuedDraws.isEmpty()) {
         GuiDrawUtils.flush(true);
         for (QueuedDraw draw : this.queuedDraws) {
            draw.render(poseStack);
         }

         this.queuedDraws.clear();
         GuiDrawUtils.flush(true);
      }
   }

   public void flushFocusedChat() {
      if (this.queuedDraws.isEmpty()) {
         return;
      }

      GuiProjectionUtils.beginGuiProjection();
      try {
         this.flushQueuedDraws(new PoseStack());
      } finally {
         GuiProjectionUtils.restoreProjection();
      }
   }

   public void enqueueText(
      Matrix3x2fc transform, FormattedCharSequence text, float x, float y, int color, boolean shadow
   ) {
      this.transformedPosition.set(0.0F);
      transform.transformPosition(x, y, this.transformedPosition);
      this.queuedDraws.add(new FontsModule.QueuedText(text, this.transformedPosition.x(), this.transformedPosition.y(), color, shadow));
   }

   @EventHandler
   private static void onClientInitialized(ClientInitializationEvent event) {
      INSTANCE.setToggled(true);
      reloadCooldownTicks = 3;
   }

   public void enqueueRectangle(
      Matrix3x2fc transform, float left, float top, float right, float bottom, int color
   ) {
      this.transformedPosition.set(0.0F);
      transform.transformPosition(left, top, this.transformedPosition);
      left = this.transformedPosition.x();
      top = this.transformedPosition.y();
      transform.transformPosition(right, bottom, this.transformedPosition);
      this.queuedDraws.add(new FontsModule.QueuedRectangle(left, top, this.transformedPosition.x(), this.transformedPosition.y(), color));
   }

   @EventHandler
   private static void onMinecraftTick(MinecraftTickEvent event) {
      if (reloadCooldownTicks > 0) {
         reloadCooldownTicks--;
      }
   }

   @EventHandler
   private void onHudRender(HudRenderEvent.Unconditional event) {
      this.flushQueuedDraws(event.getPoseStack());
   }

   public FontsModule() {
      super("Fonts", "Manages the client's font renderer.", ModuleCategory.CLIENT, new String[0]);
      this.sizeSetting = this.add(new NumberSetting<>("Size", 16, 12, 20));
      this.antiAliasSetting = this.add(new BooleanSetting("AntiAlias", true));
      this.chatSetting = this.add(new BooleanSetting("Chat", false));
      BooleanSetting offsets = new BooleanSetting("Offsets", false);
      offsets.markAsGroup();
      offsets.markVisibilityDependency();
      this.offsetsSetting = this.add(offsets);
      this.shiftSetting = this.add(new NumberSetting<>("Shift", -2, -4, 4).dependsOn(this.offsetsSetting));
      this.translateSetting = this.add(new NumberSetting<>("Translate", -1, -4, 4).dependsOn(this.offsetsSetting));
      NumberSetting<Float> shadow = new NumberSetting<>("Shadow", 0.7F, 0.5F, 1.0F);
      this.shadowSetting = this.add(shadow.dependsOn(this.offsetsSetting));
      this.transformedPosition = new Vector2f();
      this.queuedDraws = new ArrayList<>();
      this.sizeSetting.setChangeCallback(new Runnable() {
         @Override
         public void run() {
            FontRenderer.INSTANCE.updateScale();
         }
      });
      INSTANCE = this;
      ClientAccess.eventBus.subscribeStatic(FontsModule.class);
      this.setDrawn(false);
   }

   private record QueuedText(
      FormattedCharSequence text,
      float x,
      float y,
      int color,
      boolean shadow
   ) implements FontsModule.QueuedDraw {

      @Override
      public void render(PoseStack poseStack) {
         if (!FontsModule.INSTANCE.isToggled()) {
            return;
         }
         float chatScale = ((Double)ClientAccess.zioMinecraft.options.chatScale().get()).floatValue();
         poseStack.pushPose();

         poseStack.scale(chatScale, chatScale, 1.0F);
         AtlasTextRenderer renderer = FontRenderer.INSTANCE
            .getRenderer();
         renderer.draw(poseStack, this.text, this.x / chatScale, this.y / chatScale, this.color, this.shadow);
         poseStack.popPose();
      }

   }

   interface QueuedDraw {
      void render(PoseStack poseStack);
   }

   private record QueuedRectangle(
      float left,
      float top,
      float right,
      float bottom,
      int color
   ) implements FontsModule.QueuedDraw {
      @Override
      public void render(PoseStack poseStack) {
         GuiDrawUtils.fillRect(
            poseStack,
            this.left,
            this.top,
            this.right,
            this.bottom,
            this.color
         );
      }
   }
}

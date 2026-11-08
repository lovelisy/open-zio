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
import dev.zio.client.ClientContext;
import dev.zio.client.events.MinecraftTickEvent;
import dev.zio.client.events.HudRenderEvent;
import dev.zio.client.events.ClientInitializationEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.gui.screens.ClientScreen;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.render.GuiProjectionUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.AtlasTextRenderer;
import dev.zio.client.render.SystemFonts;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.FontSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

public class FontsModule extends Module {
   public static FontsModule INSTANCE;

   private static final Runnable RELOAD_RENDERER = new Runnable() {
      @Override
      public void run() {
         if (ClientAccess.zioMinecraft.player != null
            || ClientAccess.zioMinecraft.screen instanceof ClientScreen) {
            FontRenderer.INSTANCE.setRenderer(FontRenderer.INSTANCE.createRenderer());
         }
      }
   };
   public final Setting<String> fontSetting;
   public final Setting<Integer> sizeSetting;
   public final Setting<Boolean> antiAliasSetting;
   public final Setting<Boolean> chineseSetting;
   public final Setting<Boolean> chatSetting;
   private final Setting<Boolean> offsetsSetting;
   public final Setting<Integer> shiftSetting;
   public final Setting<Integer> translateSetting;
   public final Setting<Float> shadowSetting;
   private static final AtomicBoolean reloadRequested = new AtomicBoolean(false);
   private static int reloadCooldownTicks;
   private final Vector2f transformedPosition;
   private final List<QueuedDraw> queuedDraws;

   public boolean hasReloadCooldown() {
      return reloadCooldownTicks > 0;
   }

   @Override
   public void onToggle() {

      reloadCooldownTicks = 3;
      this.validateFont(false);
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
      INSTANCE.applyChineseDisplay();
      INSTANCE.validateFont(true);
      reloadRequested.set(true);
   }

   private void applyChineseDisplay() {
      boolean enabled = this.chineseSetting.getValue();
      if (ClientContext.languageTable != null) {
         ClientContext.languageTable.setChineseEnabled(enabled);
      }
      UIModule uiModule = UIModule.INSTANCE;
      if (uiModule != null) {
         UIModule.LanguageChoice target = enabled ? UIModule.LanguageChoice.CHINESE : UIModule.LanguageChoice.ENGLISH;
         if (uiModule.languageSetting.getValue() != target) {
            uiModule.languageSetting.setValue(target);
         }
      }
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
      Minecraft minecraft = ClientAccess.zioMinecraft;
      Window window = minecraft.getWindow();
      long windowHandle = (long)(window.handle());
      if (GLFW.glfwGetMouseButton(windowHandle, 0) != 1) {
         AtomicBoolean requestedReloadFlag = reloadRequested;
         if (requestedReloadFlag.get()) {
            FontRenderer.INSTANCE.setRenderer(FontRenderer.INSTANCE.createRenderer());
            boolean clearRequest = false;
            AtomicBoolean reloadFlag = reloadRequested;
            reloadFlag.set(clearRequest);

            reloadCooldownTicks = 3;
         }
      }

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
      this.fontSetting = this.add(new FontSetting("Font", SystemFonts.BUILTIN_ID));
      this.sizeSetting = this.add(new NumberSetting<>("Size", 16, 12, 20));
      this.antiAliasSetting = this.add(new BooleanSetting("AntiAlias", true));
      this.chineseSetting = this.add(new BooleanSetting("Chinese", true));
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
      this.chineseSetting.setChangeCallback(new Runnable() {
         @Override
         public void run() {
            FontsModule.this.applyChineseDisplay();
         }
      });
      this.sizeSetting.setChangeCallback(new Runnable() {
         @Override
         public void run() {
            FontRenderer.INSTANCE.updateScale();
         }
      });
      this.fontSetting.setChangeCallback(new Runnable() {
         @Override
         public void run() {
            reloadRequested.set(true);
         }
      });
      INSTANCE = this;
      ClientAccess.eventBus.subscribeStatic(FontsModule.class);
      this.setDrawn(false);
   }

   public void validateFont(boolean reload) {
      if (reload) {
         RELOAD_RENDERER.run();
      }
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

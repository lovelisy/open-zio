/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.utils.InterpolatedFloat;
import dev.zio.client.gui.CloseState;
import dev.zio.client.gui.components.RegistrySetSettingComponent;
import dev.zio.client.gui.components.ComponentFlag;
import dev.zio.client.gui.components.SettingComponent;
import dev.zio.client.config.ModulePresetCodec;
import dev.zio.client.gui.components.EnumSettingComponent;
import dev.zio.client.utils.FloatAnimation;
import dev.zio.client.utils.ClampedInterpolatedFloat;
import dev.zio.client.gui.components.ColorSettingComponent;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.gui.GuiElement;
import dev.zio.client.gui.GlassStyle;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.gui.components.AbstractComponent;
import dev.zio.client.gui.ScissorStack;
import dev.zio.client.gui.components.NumericSettingComponent;
import dev.zio.client.gui.components.StringSettingComponent;
import dev.zio.client.utils.collections.NamedIdentifier;
import dev.zio.client.utils.KeyUtils;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.gui.panels.Panel;
import dev.zio.client.modules.HudWidgetModule;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.PermanentModule;
import dev.zio.client.modules.client.FontsModule;
import dev.zio.client.modules.client.UIModule;
import dev.zio.client.settings.RegistrySetSetting;
import dev.zio.client.settings.Setting;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

public class ModuleComponent extends AbstractComponent implements CloseState {
   public final ArrayList<GuiElement> children = new ArrayList<>();
   public final InterpolatedFloat heightAnimation = new InterpolatedFloat(
      () -> this.getUiModule().animSpeedSetting.getValue()
         * 2.0F,
      true
   );
   private final ClampedInterpolatedFloat enabledAnimation = new ClampedInterpolatedFloat(
      () -> 2.0F
         * UIModule.INSTANCE.animSpeedSetting.getValue(),
      true
   );
   private final ClampedInterpolatedFloat expandAnimation = new ClampedInterpolatedFloat(
      () -> 2.0F
         * UIModule.INSTANCE.animSpeedSetting.getValue(),
      true
   );
   private final FloatAnimation searchHighlightAnimation = new FloatAnimation();
   protected final Module module;
   private int searchHighlightCycle = 0;
   private boolean copyShortcutHovered;
   private boolean requestedOpen;
   private long pressStartedAt = -1L;
   private boolean pressPushed;
   public float animatedHeight;

   private Color getEnabledGlassTextColor() {
      return new Color(GlassStyle.highlightText(), true);
   }

   private float getPressScale() {
      if (!GlassStyle.isActive() || this.pressStartedAt <= 0L) {
         return 1.0F;
      }

      float progress = (System.currentTimeMillis() - this.pressStartedAt) / 260.0F;
      if (progress >= 1.0F) {
         return 1.0F;
      }

      float squeeze = progress < 0.3F ? 1.0F : 1.0F - (progress - 0.3F) / 0.7F;
      return 1.0F - 0.035F * squeeze;
   }

   @Override
   public void setYOffset(int yOffset) {
      this.yOffset = yOffset;
      int childY = this.yOffset
         + this.getBaseHeight();
      if (this.requestedOpen) {
         for (GuiElement child : this.children) {
            child.setYOffset(childY);
            childY += child.getHeight();
         }
      }
   }

   @Override
   public int getHeight() {
      return (int)this.animatedHeight;
   }

   public void setOpen(boolean open) {
      this.requestedOpen = open;
   }

   public void startSearchHighlight() {
      this.searchHighlightAnimation
         .setValue(
            0.0F
         );
      this.searchHighlightAnimation
         .setTarget(
            1.0F,
            150L
         );
      this.searchHighlightCycle = 1;
   }

   public Module getModule() {
      return this.module;
   }

   @Override
   public void keyPressed(int keyCode) {
      this.handleCopyShortcut(keyCode);
      this.children
         .forEach(child -> child.keyPressed(keyCode));
   }

   public void toggleOpen() {
      this.setOpen(
         !this.requestedOpen
      );
      if (this.requestedOpen
         && !ClientContext.getClickGui()
            .openModuleComponents
            .contains(this)) {
         ClientContext.getClickGui()
            .openModuleComponents
            .add(this);
      }

      this.panel
         .updateLayout();
   }

   private int calculateTargetHeight() {
      int height = this.getBaseHeight();
      if (this.requestedOpen) {
         for (GuiElement child : this.children) {
            height += child.getHeight();
         }
      }

      return height;
   }

   private void handleCopyShortcut(int keyCode) {
      if (this.copyShortcutHovered
         && KeyUtils.isControlDown()
         && keyCode
            == 67) {
         ModulePresetCodec codec = ClientContext.configurationManager
            .getModulePresetCodec();
         String encodedModule = codec.encode(this.module);
         ClientAccess.zioMinecraft.keyboardHandler.setClipboard(encodedModule);
      }
   }

   public ModuleComponent(Module module, Panel panel, int yOffset) {
      super(panel, yOffset);
      this.module = module;
      int childY = this.yOffset
         + this.getBaseHeight();

      for (Object entry : module.getSettings()) {
         Setting<?> setting = (Setting<?>)entry;
         SettingComponent<?> component = this.createSettingComponent(panel, setting);
         if (component != null) {
            component.setYOffset(childY);
            component.addFlags(new ComponentFlag[]{ComponentFlag.PADDING_SHIFT});
            this.children.add(component);
         }

         childY += 11;
      }

      if (!(module instanceof HudWidgetModule) && !(module instanceof PermanentModule)) {
         this.children.add(new KeybindComponent(childY, this));
      }

      this.heightAnimation
         .setValue(
            this.calculateTargetHeight()
         );
   }

   @Override
   public boolean equals(Object other) {
      if (this == other) {
         return true;
      }
      if (other == null || this.getClass() != other.getClass()) {
         return false;
      }
      ModuleComponent component = (ModuleComponent)other;
      return Objects.equals(this.module, component.module);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(this.module);
   }

   @Override
   public void init() {
      for (GuiElement child : this.children) {
         child.init();
      }
   }

   @Override
   public boolean isClosed() {
      return !(this.animatedHeight > this.getBaseHeight() + 1) && !this.requestedOpen;
   }

   private void renderButtonBackground(PoseStack poseStack, float enabledProgress) {
      if (this.getUiModule().elementGradientSetting.getValue()
         )
       {
         Color buttonColor = this.getUiModule()
            .buttonSetting
            .getValue();
         buttonColor = ColorUtils.withAlpha(
            buttonColor,
            buttonColor.getAlpha()
               / 255.0F
               * 0.1F
         );
         GuiDrawUtils.fillVerticalGradient(
            poseStack,
            this.panel
                  .getX()
               + 1,
            this.panel
                     .getY()
                  + this.yOffset
               + 0.5F,
            (
                  this.panel
                        .getWidth()
                     - (2)
               )
               * enabledProgress,
            this.getBaseHeight()
               - 0.5F,
            buttonColor.hashCode(),
            ColorUtils.toArgb(buttonColor, 0)
         );
      } else {
         float right = this.panel
                  .getX()
               + this.panel
                  .getWidth()
            - 1.0F;
         GuiDrawUtils.fillRect(
            poseStack,
            Math.min(
               this.panel
                        .getX()
                     + 1
                  + this.panel
                        .getWidth()
                     * enabledProgress,
               right
            ),
            this.panel
                     .getY()
                  + this.yOffset
               + 0.5F,
            right,
            this.panel
                     .getY()
                  + this.yOffset
                  + this.getBaseHeight()
               - 0.5F,
            this.getUiModule().buttonSetting.getValue()
         );
      }
   }

   @Nullable
   private SettingComponent<?> createSettingComponent(
      Panel panel, Setting<?> setting
   ) {
      return switch (setting) {
         case RegistrySetSetting<?> value -> new RegistrySetSettingComponent<>(panel, this, value);
         case dev.zio.client.settings.BooleanSetting value -> new BooleanSettingComponent(panel, this, value);
         case dev.zio.client.settings.NumberSetting<?> value -> new NumericSettingComponent<>(panel, this, value);
         case dev.zio.client.settings.ColorSetting value -> new ColorSettingComponent(panel, this, value);
         case dev.zio.client.settings.StringSetting value -> new StringSettingComponent(panel, this, value);
         case dev.zio.client.settings.EnumSetting<?> value -> new EnumSettingComponent<>(panel, this, value);
         case dev.zio.client.settings.IdentifierSetting value -> new SoundSettingComponent(panel, this, value);
         default -> null;
      };
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);
      this.enabledAnimation
         .setTarget(this.module.isToggled());
      if (this.module instanceof UIModule) {
         this.enabledAnimation
            .setValue(true);
      }

      boolean gradientEnabled = this.getUiModule()
         .elementGradientSetting
         .getValue();
      float enabledProgress = this.enabledAnimation
         .updateAndGet();
      float hoverOffset = this.getHoverAnimationValue();
      int baseHeight = this.getBaseHeight();
      boolean glass = GlassStyle.isActive();
      this.expandAnimation.setTarget(this.requestedOpen);
      float expandValue = this.expandAnimation.updateAndGet();
      this.pressPushed = false;
      if (glass) {
         float press = this.getPressScale();
         if (press < 0.999F) {
            float rowCenterX = this.panel.getX() + this.panel.getWidth() / 2.0F;
            float rowCenterY = this.panel.getY() + this.yOffset + baseHeight / 2.0F;
            poseStack.pushPose();
            poseStack.translate(rowCenterX, rowCenterY, 0.0F);
            poseStack.scale(press, press, 1.0F);
            poseStack.translate(-rowCenterX, -rowCenterY, 0.0F);
            this.pressPushed = true;
         } else {
            this.pressPushed = false;
         }

         GlassStyle.moduleRow(
            poseStack,
            this.panel.getX(),
            this.panel.getY() + this.yOffset,
            this.panel.getWidth(),
            baseHeight,
            enabledProgress,
            this.getHoverAnimationValue()
         );
      } else {
         if (this.module.isToggled()
            || enabledProgress > 0.01D) {
            GuiDrawUtils.fillRect(
               poseStack,
               this.panel
                     .getX()
                  + 1.0F,
               this.panel
                        .getY()
                     + this.yOffset
                  + 0.5F,
               this.panel
                     .getX()
                  + (
                        this.panel
                              .getWidth()
                           - 1.0F
                     )
                     * enabledProgress,
               this.panel
                        .getY()
                     + this.yOffset
                     + baseHeight
                  - 0.5F,
               this.getUiModule().enabledSetting.getValue()
            );
         }

         if (gradientEnabled) {
            this.renderButtonBackground(poseStack, 1.0F);
         } else if (enabledProgress
            < 1.0F) {
            this.renderButtonBackground(poseStack, enabledProgress);
         }
      }

      if (this.searchHighlightCycle > 0) {
         float highlightProgress = this.searchHighlightAnimation
            .getValue();
         int highlightColor = ColorUtils.packArgb(
            1.0F,
            0.0F,
            0.0F,
            highlightProgress
         );
         if (glass) {
            GlassStyle.highlight(
               poseStack,
               this.panel.getX(),
               this.panel.getY() + this.yOffset,
               this.panel.getWidth(),
               baseHeight,
               highlightColor
            );
         } else {
            GuiDrawUtils.fillRect(
               poseStack,
               this.panel
                     .getX()
                  + 1.0F,
               this.panel
                        .getY()
                     + this.yOffset
                  + 0.5F,
               this.panel
                     .getX()
                  + this.panel
                     .getWidth()
                  - 1.0F,
               this.panel
                        .getY()
                     + this.yOffset
                  + baseHeight
                  - 0.5F,
               highlightColor
            );
         }
         if (highlightProgress
               == 1.0F
            || highlightProgress == 0.0F) {
            this.searchHighlightAnimation
               .setTarget(
                  (
                        highlightProgress
                           + 1.0F
                     )
                     % 2.0F,
                  150L
               );
         }

         if (highlightProgress == 0.0F) {
            this.searchHighlightCycle = (
                  this.searchHighlightCycle + 1
               )
               % 4;
         }
      }

      FontRenderer.INSTANCE
         .drawWithShadow(
            poseStack,
            ClientContext.languageTable.translateModule(this.module.getName()),
            this.panel
                  .getX()
               + (glass ? 9 : 3),
            this.panel
                  .getY()
               + this.getTextYOffset()
               - hoverOffset
               + this.yOffset,
            glass && this.module.isToggled()
               ? getEnabledGlassTextColor()
               : (
                  this.module.isToggled()
                     ? this.getUiModule()
                        .enabledColorSetting
                        .getValue()
                     : this.getUiModule().textColorSetting.getValue()
               )
         );
      if (this.getUiModule().gearSetting.getValue()
         && !this.children.isEmpty()) {
         if (glass) {
            GlassStyle.chevron(
               poseStack,
               this.panel.getX() + this.panel.getWidth() - 8.0F,
               this.panel.getY() + this.yOffset + this.getBaseHeight() / 2.0F,
               2.4F,
               90.0F * expandValue,
               1.0F,
               this.module.isToggled() ? 0xB3FFFFFF : 0x99A7ADBA
            );
         } else {
            String expandIndicator = this.requestedOpen ? "-" : "+";
            FontRenderer.INSTANCE
               .drawWithShadow(
                  poseStack,
                  expandIndicator,
                  this.panel
                           .getX()
                        + this.panel
                           .getWidth()
                        - (4)
                     - FontRenderer.INSTANCE
                        .getWidth(expandIndicator),
                  this.panel
                        .getY()
                     + this.getTextYOffset()
                     + this.yOffset,
                  GlassStyle.isActive()
                     ? new Color(GlassStyle.highlightText(), true)
                     : this.getUiModule().textColorSetting.getValue()
               );
         }
      }

      MutableComponent statusText = Component.empty();
      if (this.module.isWip()) {
         statusText.append(ClientContext.languageTable.getTranslationOrDefault("ui.Beta", "beta") + " ");
      }

      if (this.getUiModule().bindsSetting.getValue()
         && this.module
               .getKeybind()
               .code()
            != -1) {
         String bindingName = this.module
            .getKeybind()
            .getDisplayName();
         statusText.append("[").append(bindingName).append("]");
      }

      if (!statusText.getSiblings().isEmpty()) {
         float statusScale = FontsModule.INSTANCE.isToggled()
            ? 0.65F
            : 0.5F;
         float statusYOffset = FontsModule.INSTANCE.isToggled()
            ? 0.0F
            : 1.0F;
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack,
               statusText,
               this.panel
                     .getX()
                  + FontRenderer.INSTANCE
                     .getWidth(
                        ClientContext.languageTable.translateModule(this.module.getName())
                     )
                  + (glass ? 9.0F : 3.0F),
               this.panel
                     .getY()
                  + this.getTextYOffset()
                  - hoverOffset
                  + this.yOffset
                  + statusYOffset,
               statusScale,
               glass && this.module.isToggled()
                  ? Color.WHITE
                  : this.getUiModule().textColorSetting.getValue()
            );
      }

      poseStack.pushPose();
      if (this.animatedHeight > baseHeight
         || this.requestedOpen) {
         GuiDrawUtils.flush();
         ScissorStack.pushIntersection(
            this.panel
               .getX(),
            this.panel
                  .getY()
               + this.yOffset
               + baseHeight,
            this.panel
                  .getWidth()
               - 1,
            (int)this.animatedHeight - baseHeight
         );

         for (GuiElement child : this.children) {
            child.render(poseStack, mouseX, mouseY);
         }

         GuiDrawUtils.flush();
         ScissorStack.pop();
      }

      poseStack.popPose();
      if (this.pressPushed) {
         poseStack.popPose();
         this.pressPushed = false;
      }
      if (this.getUiModule().descriptionsSetting.getValue()
         && this.isMouseOver(mouseX, mouseY)) {
         ClientContext.getClickGui()
            .setTooltipText(
               this.module.getDescription()
            );
      }
   }

   @Override
   public boolean isOpen() {
      return this.requestedOpen;
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      super.mouseClicked(mouseX, mouseY, button);
      if (this.isMouseOver(mouseX, mouseY) && GlassStyle.isActive()) {
         this.pressStartedAt = System.currentTimeMillis();
      }

      if (this.isMouseOver(mouseX, mouseY)
         && !ClientContext.getClickGui()
            .getSearchBox()
            .getValue()
            .isEmpty()) {
         this.startSearchHighlight();
      }

      if (this.isMouseOver(mouseX, mouseY)
         && button == 0
         && this.isLeftClickEligible()) {
         boolean toggled = this.module.isToggled();
         this.module
            .toggle();
         GlassStyle.play(
            toggled ? BuiltinSound.TOGGLE_OFF : BuiltinSound.TOGGLE_ON,
            0.9F
         );
      }

      if (this.isMouseOver(mouseX, mouseY) && button == 1) {
         this.toggleOpen();
         GlassStyle.play(
            this.requestedOpen ? BuiltinSound.EXPAND : BuiltinSound.COLLAPSE,
            0.9F
         );
      }

      this.children
         .forEach(child -> child.mouseClicked(mouseX, mouseY, button));
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX
            > this.panel
               .getX()
         && mouseX
            < this.panel
                  .getX()
               + this.panel
                  .getWidth()
         && mouseY
            > this.clampYToPanel(
               this.panel
                     .getY()
                  + this.yOffset
            )
         && mouseY
            < this.clampYToPanel(
               this.panel
                     .getY()
                  + this.getBaseHeight()
                  + this.yOffset
            );
   }

   @Override
   public void update(double mouseX, double mouseY) {
      if (FontRenderer.INSTANCE
         .isFontLoading()) {
         this.heightAnimation
            .setValue(
               this.calculateTargetHeight()
            );
      } else {
         this.heightAnimation
            .setTarget(
               this.calculateTargetHeight()
            );
      }

      this.animatedHeight = this.heightAnimation
         .updateAndGet();
      this.copyShortcutHovered = super.isMouseOver(
         mouseX, mouseY
      );
      if (this.requestedOpen) {
         this.children
            .forEach(child -> child.update(mouseX, mouseY));
      }
   }

   @Override
   public void charTyped(char character) {
      this.children
         .forEach(child -> child.charTyped(character));
   }

   @Override
   public void mouseScrolled(double mouseX, double mouseY, double amount) {
      for (GuiElement child : this.children) {
         child.mouseScrolled(mouseX, mouseY, amount);
      }
   }

   @Override
   public void mouseReleased(double mouseX, double mouseY, int button) {
      this.children
         .forEach(child -> child.mouseReleased(mouseX, mouseY, button));
   }

}

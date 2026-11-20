/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.gui.components;

import dev.zio.client.ClientContext;
import dev.zio.client.audio.BuiltinSound;
import dev.zio.client.gui.GlassStyle;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.settings.NamedEntry;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;

public class EnumOptionRow<E extends Enum<E> & NamedEntry> extends AbstractComponent {
   final EnumSettingComponent<E> enumComponent;
   final String label;
   final int selectableIndex;

   @Override
   public int getHeight() {
      return this.enumComponent
            .isUnavailable()
         ? 0
         : super.getHeight();
   }

   public EnumOptionRow(EnumSettingComponent<E> enumComponent, String label, int yOffset, int selectableIndex) {
      super(enumComponent.getPanel(), yOffset);
      this.label = label;
      this.selectableIndex = selectableIndex;
      this.enumComponent = enumComponent;
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);
      String displayLabel = ClientContext.languageTable.translateEnum(
         this.enumComponent.getSetting().getValue().getDeclaringClass().getName(), this.label, this.label);
      if (GlassStyle.isActive()) {
         float rowY = this.enumComponent.getY() + this.yOffset;
         float rowHeight = this.getBaseHeight();
         GlassStyle.optionHover(
            poseStack,
            this.getPanel().getX(),
            rowY,
            this.getPanel().getWidth(),
            rowHeight,
            this.getHoverAnimationValue()
         );
         boolean selected = this.enumComponent.getSetting().getValue().getName().equalsIgnoreCase(this.label);
         if (selected) {
            GlassStyle.optionDot(
               poseStack,
               this.getPanel().getX() + 10.0F,
               rowY + rowHeight / 2.0F,
               1.0F
            );
         }

         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack,
               displayLabel,
               this.getPanel().getX() + 18.0F,
               rowY + this.getTextYOffset() - 1.0F - this.getHoverAnimationValue(),
               selected ? Color.WHITE : new Color(0xFF9AA0AC, true)
            );
         return;
      }

      FontRenderer.INSTANCE
         .drawWithShadow(
            poseStack,
            displayLabel,
            this.getPanel()
                  .getX()
               + this.getPanel()
                     .getWidth()
                  / 2.0F
               - FontRenderer.INSTANCE
                     .getWidth(
                        displayLabel
                     )
                  / 2.0F,
            this.enumComponent
                     .getY()
                  + this.yOffset
               + this.getTextYOffset()
               - 1.0F
               - this.getHoverAnimationValue(),
            GlassStyle.isActive()
               ? new Color(GlassStyle.highlightText(), true)
               : this.getUiModule().textColorSetting.getValue()
         );
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (this.enumComponent.isUnavailable()
         || !this.isMouseOver(mouseX, mouseY)
         || !this.enumComponent.isExpanded()) {
         return;
      }

      super.mouseClicked(mouseX, mouseY, button);
      if (this.enumComponent
            .getParentCloseState()
            .isOpen()
         && button == 0) {
         for (E candidate : this.enumComponent
               .getSetting()
               .getValue()
            .getDeclaringClass()
            .getEnumConstants()) {
            if (candidate.getName()
                  .equalsIgnoreCase(this.label)) {
               this.enumComponent
                  .getSetting()
                  .setValue(candidate);
               this.enumComponent
                  .setSelectedIndex(this.selectableIndex);
               GlassStyle.play(BuiltinSound.SELECT, 0.8F);
            }
         }
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX
            > this.enumComponent
               .getX()
         && mouseX
            < this.enumComponent
                  .getX()
               + this.getPanel()
                  .getWidth()
         && mouseY
            > this.enumComponent
                  .getY()
               + this.yOffset
         && mouseY
            < this.enumComponent
                  .getY()
               + this.getBaseHeight()
               + this.yOffset;
   }

}

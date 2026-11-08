package dev.zio.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zio.client.render.FontEntry;
import dev.zio.client.render.FontRenderer;

public class FontOptionRow extends AbstractComponent {
   private final FontSettingComponent component;
   private final FontEntry entry;
   private final int selectableIndex;

   public FontOptionRow(FontSettingComponent component, FontEntry entry, int yOffset, int selectableIndex) {
      super(component.getPanel(), yOffset);
      this.component = component;
      this.entry = entry;
      this.selectableIndex = selectableIndex;
   }

   @Override
   public int getHeight() {
      return this.component.isUnavailable() ? 0 : super.getHeight();
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);
      String label = this.entry.name();
      FontRenderer.INSTANCE.drawWithShadow(
         poseStack,
         label,
         this.getPanel().getX() + this.getPanel().getWidth() / 2.0F - FontRenderer.INSTANCE.getWidth(label) / 2.0F,
         this.component.getY() + this.yOffset + this.getTextYOffset() - 1.0F - this.getHoverAnimationValue(),
         this.getUiModule().textColorSetting.getValue()
      );
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (this.component.isUnavailable() || !this.isMouseOver(mouseX, mouseY) || !this.component.isExpanded()) {
         return;
      }
      super.mouseClicked(mouseX, mouseY, button);
      if (this.component.getParentCloseState().isOpen() && button == 0) {
         this.component.getSetting().select(this.entry);
         this.component.setSelectedIndex(this.selectableIndex);
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX > this.component.getX()
         && mouseX < this.component.getX() + this.getPanel().getWidth()
         && mouseY > this.component.getY() + this.yOffset
         && mouseY < this.component.getY() + this.getBaseHeight() + this.yOffset;
   }
}

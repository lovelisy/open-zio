package dev.zio.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zio.client.gui.CloseState;
import dev.zio.client.gui.panels.Panel;
import dev.zio.client.modules.client.UIModule;
import dev.zio.client.render.FontEntry;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.GuiDrawUtils;
import dev.zio.client.settings.FontSetting;
import dev.zio.client.utils.ClampedInterpolatedFloat;
import dev.zio.client.utils.InterpolatedFloat;
import java.util.ArrayList;
import java.util.List;

public class FontSettingComponent extends SettingComponent<String> {
   private final ArrayList<FontOptionRow> optionRows = new ArrayList<>();
   private final InterpolatedFloat selectionOffsetAnimation = new InterpolatedFloat(
      () -> 2.0F * this.getUiModule().animSpeedSetting.getValue(),
      true
   );
   private final ClampedInterpolatedFloat fillAnimation = new ClampedInterpolatedFloat(
      () -> 2.0F * UIModule.INSTANCE.animSpeedSetting.getValue(),
      true
   );
   private boolean expanded;
   private int componentYOffset;
   private int cachedX;
   private int cachedY;
   private int selectedIndex;
   private int rowCount;

   public FontSettingComponent(Panel panel, CloseState parentCloseState, FontSetting setting) {
      super(panel, parentCloseState, setting);
      this.rebuildRows();
      this.selectedIndex = this.indexById(setting.getSelected().id());
   }

   @Override
   public FontSetting getSetting() {
      return (FontSetting)super.getSetting();
   }

   public void setSelectedIndex(int index) {
      this.selectedIndex = index;
   }

   @Override
   public void setYOffset(int yOffset) {
      this.componentYOffset = yOffset;
   }

   @Override
   public int getHeight() {
      if (this.isUnavailable()) {
         this.selectionOffsetAnimation.setValue(-this.getBaseHeight());
         return 0;
      }
      return this.expanded
         ? this.getBaseHeight() + this.rowCount * this.getBaseHeight() + 1
         : super.getHeight();
   }

   @Override
   public void init() {
      this.selectedIndex = this.indexById(this.getSetting().getSelected().id());
   }

   private void rebuildRows() {
      List<FontEntry> options = this.getSetting().getOptions();
      this.optionRows.clear();
      int rowYOffset = this.getBaseHeight();
      for (int index = 0; index < options.size(); index++) {
         this.optionRows.add(new FontOptionRow(this, options.get(index), rowYOffset, index));
         rowYOffset += this.getBaseHeight();
      }
      this.rowCount = options.size();
   }

   private int indexById(String id) {
      List<FontEntry> options = this.getSetting().getOptions();
      for (int index = 0; index < options.size(); index++) {
         if (options.get(index).id().equals(id)) {
            return index;
         }
      }
      return 0;
   }

   public int getY() {
      return this.cachedY;
   }

   public int getX() {
      return this.cachedX;
   }

   public boolean isExpanded() {
      return this.expanded;
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (this.isUnavailable()) {
         this.fillAnimation.reset();
         return;
      }
      super.render(poseStack, mouseX, mouseY);
      this.fillAnimation.setTarget(true);
      if (this.expanded) {
         this.selectionOffsetAnimation.setTarget(this.selectedIndex * this.getBaseHeight());
         float selectionOffset = this.selectionOffsetAnimation.updateAndGet();
         float firstRowY = this.panel.getY() + this.componentYOffset + this.getBaseHeight() - 0.5F;
         GuiDrawUtils.fillRect(
            poseStack,
            this.panel.getX() + this.getHorizontalPadding() + 1,
            Math.max(selectionOffset + firstRowY, firstRowY),
            this.panel.getX() + this.panel.getWidth() - 2,
            this.panel.getY() + this.componentYOffset + this.getBaseHeight() * 2 + selectionOffset - 0.5F,
            this.getUiModule().colorSetting.getValue()
         );
      } else {
         this.selectionOffsetAnimation.setTarget(-this.getBaseHeight());
      }
      int edgeInset = this.expanded ? 1 : 0;
      float fillWidth = Math.max(this.panel.getWidth() * this.fillAnimation.updateAndGet() - 1.0F, 2.0F);
      GuiDrawUtils.fillRect(
         poseStack,
         this.panel.getX() + this.getHorizontalPadding() + edgeInset,
         this.panel.getY() + this.componentYOffset + 0.5F + edgeInset,
         this.panel.getX() + fillWidth - edgeInset,
         this.panel.getY() + this.componentYOffset + this.getBaseHeight() - 0.5F,
         this.getUiModule().colorSetting.getValue()
      );
      if (this.expanded) {
         for (FontOptionRow row : this.optionRows) {
            row.render(poseStack, mouseX, mouseY);
         }
         if (UIModule.INSTANCE.lineSetting.getValue()) {
            GuiDrawUtils.drawRectOutline(
               poseStack,
               this.panel.getX() + this.getHorizontalPadding(),
               this.panel.getY() + this.componentYOffset + 0.5F,
               this.panel.getX() + this.panel.getWidth() - 2,
               this.panel.getY() + this.componentYOffset + this.getHeight() - 1.5F,
               this.getUiModule().colorSetting.getValue()
            );
         }
      }
      FontEntry selected = this.getSetting().getSelected();
      String displayName = this.getDisplayName();
      String valueName = selected.name();
      if (valueName.length() > 32) {
         valueName = valueName.substring(0, 32);
      }
      float available = this.panel.getWidth() - 8.0F;
      float prefixWidth = FontRenderer.INSTANCE.getWidth(displayName) + FontRenderer.INSTANCE.getWidth(": ");
      while (valueName.length() > 1 && prefixWidth + FontRenderer.INSTANCE.getWidth(valueName) > available) {
         valueName = valueName.substring(0, valueName.length() - 1);
      }
      String label = new StringBuilder(displayName).append(": ").append(valueName).toString();
      FontRenderer.INSTANCE.drawWithShadow(
         poseStack,
         label,
         this.panel.getX() + 4,
         this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.componentYOffset,
         this.getUiModule().textColorSetting.getValue()
      );
      int rowYOffset = this.getBaseHeight();
      for (FontOptionRow row : this.optionRows) {
         row.setYOffset(rowYOffset);
         rowYOffset += this.getBaseHeight();
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX > this.cachedX
         && mouseX < this.cachedX + this.getPanel().getWidth()
         && mouseY > this.clampYToPanel(this.cachedY)
         && mouseY < this.clampYToPanel(this.cachedY + 11);
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (this.isUnavailable()) {
         return;
      }
      super.mouseClicked(mouseX, mouseY, button);
      for (FontOptionRow row : this.optionRows) {
         row.mouseClicked(mouseX, mouseY, button);
      }
      if (this.isMouseOver(mouseX, mouseY)) {
         if (button == 0) {
            this.getSetting().cycle();
            this.selectedIndex = this.indexById(this.getSetting().getSelected().id());
         }
         if (button == 1) {
            this.selectedIndex = this.indexById(this.getSetting().getSelected().id());
            this.expanded = !this.expanded;
            this.getPanel().updateLayout();
         }
      }
   }

   @Override
   public void update(double mouseX, double mouseY) {
      if (this.isUnavailable()) {
         return;
      }
      this.cachedY = this.panel.getY() + this.componentYOffset;
      this.cachedX = this.panel.getX();
      if (this.getSetting().getOptions().size() != this.rowCount) {
         this.rebuildRows();
         this.selectedIndex = this.indexById(this.getSetting().getSelected().id());
         this.getPanel().updateLayout();
      }
   }
}

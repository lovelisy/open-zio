package dev.zio.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.zio.client.render.FontEntry;
import dev.zio.client.render.SystemFonts;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class FontSetting extends Setting<String> {
   public FontSetting(String name, String defaultValue) {
      super(name, defaultValue);
   }

   public List<FontEntry> getOptions() {
      return SystemFonts.entries();
   }

   public FontEntry getSelected() {
      return SystemFonts.find(this.getValue());
   }

   public void cycle() {
      List<FontEntry> options = this.getOptions();
      if (options.isEmpty()) {
         return;
      }
      int index = 0;
      for (int current = 0; current < options.size(); current++) {
         if (options.get(current).id().equals(this.getValue())) {
            index = current;
            break;
         }
      }
      this.setValue(options.get((index + 1) % options.size()).id());
   }

   public void select(FontEntry entry) {
      if (entry != null) {
         this.setValue(entry.id());
      }
   }

   @Override
   public void setValue(String value) {
      if (!Objects.equals(this.getValue(), value)) {
         super.setValue(value);
      }
   }

   @Override
   public void fromJson(JsonElement json) {
      this.setValue(json.getAsString());
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.getValue());
   }

   @Override
   public void parseValue(String text) {
      if (text == null) {
         return;
      }
      String query = text.trim();
      String lower = query.toLowerCase(Locale.ROOT);
      for (FontEntry entry : this.getOptions()) {
         if (entry.id().equalsIgnoreCase(query) || entry.name().equalsIgnoreCase(query)) {
            this.select(entry);
            return;
         }
      }
      for (FontEntry entry : this.getOptions()) {
         if (entry.name().toLowerCase(Locale.ROOT).contains(lower)) {
            this.select(entry);
            return;
         }
      }
   }
}

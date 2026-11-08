package dev.zio.client.render;

import java.util.Locale;

public record FontEntry(String id, String name, String path, int ttcIndex) {
   public boolean builtin() {
      return this.path == null;
   }

   public String key() {
      return this.name.toLowerCase(Locale.ROOT);
   }
}

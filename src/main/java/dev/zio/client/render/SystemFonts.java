package dev.zio.client.render;

import java.awt.Font;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.lwjgl.BufferUtils;

public final class SystemFonts {
   public static final String BUILTIN_ID = "builtin:misans";
   public static final FontEntry BUILTIN = new FontEntry(BUILTIN_ID, BundledFont.NAME, null, 0);
   private static final Object LOCK = new Object();
   private static final Comparator<FontEntry> BY_NAME = new Comparator<FontEntry>() {
      @Override
      public int compare(FontEntry first, FontEntry second) {
         return first.name().compareToIgnoreCase(second.name());
      }
   };
   private static volatile List<FontEntry> entries = List.of(BUILTIN);
   private static volatile boolean scanning;

   private SystemFonts() {
   }

   public static List<FontEntry> entries() {
      startScan();
      return entries;
   }

   public static FontEntry find(String id) {
      if (id != null) {
         for (FontEntry entry : entries) {
            if (entry.id().equals(id)) {
               return entry;
            }
         }
         FontEntry parsed = fromId(id);
         if (parsed != null) {
            return parsed;
         }
      }
      return BUILTIN;
   }

   public static Font awtFont(FontEntry entry, int style, int size) {
      if (entry == null || entry.builtin()) {
         return BundledFont.awtFont(style, size);
      }
      try {
         Font[] fonts = Font.createFonts(new File(entry.path()));
         if (fonts.length > entry.ttcIndex()) {
            return fonts[entry.ttcIndex()].deriveFont(style, (float)size);
         }
      } catch (Throwable ignoredFailure) {
      }
      return new Font(Font.SANS_SERIF, style, size);
   }

   public static ByteBuffer byteBuffer(FontEntry entry) {
      if (entry == null || entry.builtin()) {
         return BundledFont.buffer();
      }
      return readFile(new File(entry.path()));
   }

   private static FontEntry fromId(String id) {
      int separator = id.lastIndexOf('#');
      if (separator <= 0 || separator == id.length() - 1) {
         return null;
      }
      try {
         int index = Integer.parseInt(id.substring(separator + 1));
         File file = new File(id.substring(0, separator));
         if (!file.isFile()) {
            return null;
         }
         return new FontEntry(id, file.getName(), file.getAbsolutePath(), index);
      } catch (NumberFormatException invalidIndex) {
         return null;
      }
   }

   private static void startScan() {
      if (scanning) {
         return;
      }
      synchronized (LOCK) {
         if (scanning) {
            return;
         }
         scanning = true;
         Thread thread = new Thread(SystemFonts::scan, "zio-font-scan");
         thread.setDaemon(true);
         thread.setPriority(Thread.MIN_PRIORITY);
         thread.start();
      }
   }

   private static void scan() {
      List<FontEntry> found = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (File directory : searchDirectories()) {
         scanDirectory(directory, found, seen, 0);
      }
      found.sort(BY_NAME);
      List<FontEntry> result = new ArrayList<>(found.size() + 1);
      result.add(BUILTIN);
      result.addAll(found);
      entries = List.copyOf(result);
   }

   private static void scanDirectory(File directory, List<FontEntry> found, Set<String> seen, int depth) {
      if (depth > 6 || !directory.isDirectory()) {
         return;
      }
      File[] files = directory.listFiles();
      if (files == null) {
         return;
      }
      for (File file : files) {
         if (file.isDirectory()) {
            scanDirectory(file, found, seen, depth + 1);
            continue;
         }
         String name = file.getName().toLowerCase(Locale.ROOT);
         if (!name.endsWith(".ttf") && !name.endsWith(".otf") && !name.endsWith(".ttc")) {
            continue;
         }
         FontEntry entry = readEntry(file, 0);
         if (entry == null || !seen.add(entry.key())) {
            continue;
         }
         found.add(entry);
      }
   }

   private static FontEntry readEntry(File file, int ttcIndex) {
      String fileName = file.getName();
      int dot = fileName.lastIndexOf('.');
      String base = dot > 0 ? fileName.substring(0, dot) : fileName;
      String name = base.replace('-', ' ').replace('_', ' ').trim();
      if (name.isEmpty()) {
         name = fileName;
      }
      return new FontEntry(file.getAbsolutePath() + "#" + ttcIndex, name, file.getAbsolutePath(), ttcIndex);
   }

   private static ByteBuffer readFile(File file) {
      try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
         long size = channel.size();
         if (size <= 0L || size > Integer.MAX_VALUE) {
            return null;
         }
         ByteBuffer buffer = BufferUtils.createByteBuffer((int)size);
         while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
               break;
            }
         }
         buffer.flip();
         return buffer;
      } catch (Throwable error) {
         return null;
      }
   }

   private static List<File> searchDirectories() {
      List<File> directories = new ArrayList<>();
      if (new File("/system/fonts").isDirectory()) {
         directories.add(new File("/system/fonts"));
         return directories;
      }
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (os.contains("win")) {
         String systemRoot = System.getenv("SystemRoot");
         if (systemRoot != null) {
            directories.add(new File(systemRoot, "Fonts"));
         }
         String localAppData = System.getenv("LOCALAPPDATA");
         if (localAppData != null) {
            directories.add(new File(localAppData, "Microsoft/Windows/Fonts"));
         }
      } else if (os.contains("mac")) {
         directories.add(new File("/System/Library/Fonts"));
         directories.add(new File("/Library/Fonts"));
         directories.add(new File(System.getProperty("user.home", ""), "Library/Fonts"));
      } else {
         directories.add(new File("/usr/share/fonts"));
      }
      String javaHome = System.getProperty("java.home");
      if (javaHome != null) {
         directories.add(new File(javaHome, "lib/fonts"));
      }
      return directories;
   }
}

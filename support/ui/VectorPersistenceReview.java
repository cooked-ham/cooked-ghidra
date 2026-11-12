package uirefresh;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import generic.theme.*;
import generic.theme.uirefresh.icons.VectorIcon;
import ghidra.framework.plugintool.PluginTool;

/** Isolated live-application persistence probe; not a product plugin. */
public class VectorPersistenceReview {
    public static void run(PluginTool tool, String output) {
        Path out = Path.of(output);
        try {
            Thread.sleep(12000);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    ThemeManager tm = ThemeManager.getInstance();
                    List<String> rows = new ArrayList<>();
                    rows.add("state,theme,vector_count,pid");
                    if (Boolean.getBoolean("uirefresh.vector.restart")) {
                        if (!tm.getActiveTheme().getName().equals("Vector Restart Copy")) {
                            throw new AssertionError("Startup failed to restore saved copy: " + tm.getActiveTheme().getName());
                        }
                        check(tm, "restart", rows);
                    }
                    else {
                        tm.setTheme(tm.getTheme("Graphite Preview"));
                        int expected = check(tm, "builtin", rows);
                        File file = new File(output + ".theme");
                        GTheme active = tm.getActiveTheme();
                        GTheme copy = new GTheme(file, "Vector Restart Copy",
                            active.getLookAndFeelType(), active.useDarkDefaults());
                        copy.load(tm.getNonDefaultValues());
                        copy.save();
                        tm.setTheme(GTheme.loadTheme(file));
                        if (check(tm, "reload", rows) != expected) throw new AssertionError("Lost icons");
                        File zip = new File(output + ".theme.zip");
                        new ThemeWriter(copy).writeThemeToZipFile(zip);
                        tm.setTheme(GTheme.loadTheme(zip));
                        if (check(tm, "zip", rows) != expected) throw new AssertionError("Lost exported icons");
                        tm.setTheme(tm.getTheme("Flat Dark Theme"));
                        tm.setTheme(GTheme.loadTheme(file));
                        if (check(tm, "switch-back", rows) != expected) throw new AssertionError("Lost switched icons");
                    }
                    var frame = tool.getToolFrame();
                    BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics();
                    frame.printAll(g);
                    g.dispose();
                    ImageIO.write(image, "png", new File(output + ".png"));
                    Files.write(out, rows);
                    Files.writeString(Path.of(output + ".done"), "ok");
                    tool.setConfigChanged(false);
                    tool.close();
                }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        }
        catch (Throwable e) {
            try { Files.writeString(Path.of(output + ".err"), e.toString()); }
            catch (Exception ignored) { }
        }
    }

    private static int check(ThemeManager tm, String state, List<String> rows) {
        int count = 0;
        for (IconValue value : tm.getCurrentValues().getIcons()) {
            if (value.getRawValue() instanceof VectorIcon) count++;
        }
        if (count == 0) throw new AssertionError("No vector icons in " + state);
        rows.add(state + "," + tm.getActiveTheme().getName() + "," + count + "," + ProcessHandle.current().pid());
        return count;
    }
}

package uirefresh;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import docking.ComponentProvider;
import docking.DockingWindowManager;
import ghidra.app.services.*;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.util.ProgramLocation;

/** Explicit-save application exit/relaunch probe in a disposable runtime/project/profile. */
public class LocationContextRestartReview {
    private static Component find(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container nested) {
                Component found = find(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static void run(PluginTool tool, String output) {
        try {
            boolean restart = Boolean.getBoolean("uirefresh.vector.restart");
            Path expected = Path.of(output).getParent().resolve("expected.properties");
            Properties values = new Properties();
            Thread.sleep(12000);
            if (!restart) {
                SwingUtilities.invokeAndWait(() -> {
                    var pm = tool.getService(ProgramManager.class);
                    var root = tool.getProject().getProjectData().getRootFolder();
                    var files = root.getFiles();
                    if (files.length == 0) throw new AssertionError("Missing project program");
                    var program = pm.openProgram(files[0]);
                    var functions = program.getFunctionManager().getFunctions(true);
                    if (!functions.hasNext()) throw new AssertionError("Missing fixture function");
                    var address = functions.next().getEntryPoint();
                    var goTo = tool.getService(GoToService.class);
                    goTo.goTo(new ProgramLocation(program, address));
                    if (functions.hasNext()) goTo.goTo(new ProgramLocation(program, functions.next().getEntryPoint()));
                    goTo.goTo(new ProgramLocation(program, address));
                    values.setProperty("address", address.toString(true));
                    values.setProperty("path", program.getDomainFile().getPathname());
                    values.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
                });
                Thread.sleep(6000);
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        ComponentProvider decompiler = tool.getComponentProvider("Decompiler");
                        tool.showComponentProvider(decompiler, true);
                        DockingWindowManager wm = tool.getWindowManager();
                        Method get = DockingWindowManager.class.getDeclaredMethod("getPlaceholder", ComponentProvider.class);
                        get.setAccessible(true);
                        Object placeholder = get.invoke(wm, decompiler);
                        Method move = DockingWindowManager.class.getDeclaredMethod("movePlaceholder", placeholder.getClass(), Point.class);
                        move.setAccessible(true);
                        move.invoke(wm, placeholder, new Point(120, 120));
                    }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
                Thread.sleep(1500);
            }
            else {
                try (var in = Files.newInputStream(expected)) { values.load(in); }
                if (values.getProperty("pid").equals(Long.toString(ProcessHandle.current().pid()))) throw new AssertionError("Process did not restart");
            }
            SwingUtilities.invokeAndWait(() -> {
                try {
                    ComponentProvider listing = tool.getComponentProvider("Listing");
                    ComponentProvider decompiler = tool.getComponentProvider("Decompiler");
                    if (listing == null || decompiler == null) throw new AssertionError("Missing providers");
                    JButton address = (JButton) find(listing.getComponent(), "Go to displayed address");
                    JButton program = (JButton) find(listing.getComponent(), "Switch open program");
                    if (address == null || !address.getText().contains(values.getProperty("address"))) throw new AssertionError("Restored address mismatch: " + (address == null ? "missing panel" : address.getText()));
                    if (!program.getToolTipText().contains(values.getProperty("path"))) throw new AssertionError("Restored program mismatch");
                    Window main = SwingUtilities.getWindowAncestor(listing.getComponent());
                    Window detached = SwingUtilities.getWindowAncestor(decompiler.getComponent());
                    if (detached == null || main == detached) throw new AssertionError("Detached layout lost");
                    if (find(decompiler.getComponent(), "Location context") == null) throw new AssertionError("Detached context missing");
                    var history = tool.getService(NavigationHistoryService.class);
                    var target = tool.getService(GoToService.class).getDefaultNavigatable();
                    int previous = history.getPreviousLocations(target).size();
                    if (previous == 0) throw new AssertionError("History missing");
                    if (restart && previous != Integer.parseInt(values.getProperty("history"))) throw new AssertionError("History did not restore: " + previous);
                    values.setProperty("history", Integer.toString(previous));
                    if (!restart) {
                        tool.saveTool();
                        if (!tool.getProject().saveSessionTools()) throw new AssertionError("Session save failed");
                        tool.getProject().save();
                        try (var out = Files.newOutputStream(expected)) { values.store(out, "Location context restart expectations"); }
                    }
                    for (var entry : Map.of("main", main, "detached", detached).entrySet()) {
                        Window window = entry.getValue();
                        BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        var graphics = image.createGraphics();
                        window.printAll(graphics);
                        graphics.dispose();
                        ImageIO.write(image, "png", Path.of(output + "." + entry.getKey() + ".png").toFile());
                    }
                    Files.writeString(Path.of(output), "phase,pid,address,path,detached,history\n" + (restart ? "restart" : "save") + "," + ProcessHandle.current().pid() + "," + values.getProperty("address") + "," + values.getProperty("path") + ",true," + previous + "\n");
                    Files.writeString(Path.of(output + ".done"), "PASS");
                }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            System.exit(0);
        }
        catch (Throwable failure) {
            try {
                java.io.StringWriter trace = new java.io.StringWriter();
                failure.printStackTrace(new java.io.PrintWriter(trace));
                Files.writeString(Path.of(output + ".err"), trace.toString());
            }
            catch (Exception ignored) { }
        }
    }
}

// Live large-graph, repaint/allocation, filtering, keyboard and accessibility checks.
package uirefresh;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.*;
import generic.theme.ThemeManager;
import docking.widgets.table.GTable;
import ghidra.app.plugin.core.functiongraph.FGProvider;
import ghidra.app.plugin.core.functiongraph.mvc.FGView;
import ghidra.app.services.*;
import ghidra.framework.plugintool.PluginTool;
import ghidra.graph.viewer.*;
import ghidra.program.model.listing.Program;
import ghidra.util.task.TaskMonitor;

public final class AcceptanceReview {
    private static final List<String> checks = new ArrayList<>();
    private static final List<String> timings = new ArrayList<>(List.of(
        "theme,workload,sample,milliseconds,allocated_bytes"));
    private static Path out;
    private AcceptanceReview() { }

    public static void run(PluginTool tool, Path output) throws Exception {
        out = output;
        Program program = tool.getService(ProgramManager.class).openProgram(
            tool.getProject().getProjectData().getRootFolder().getFolder("binaries").getFile("source.bin"));
        var entry = program.getAddressFactory().getDefaultAddressSpace().getAddress(0x500000);
        int transaction = program.startTransaction("Create deterministic large graph fixture");
        boolean commit = false;
        try {
            byte[] bytes = new byte[128 * 10 + 1];
            for (int i = 0; i < 128; i++) {
                int offset = i * 10;
                bytes[offset] = (byte) 0x83; bytes[offset + 1] = (byte) 0xf9;
                bytes[offset + 2] = (byte) i;
                bytes[offset + 3] = 0x74; bytes[offset + 4] = 5;
                bytes[offset + 5] = (byte) 0xb8; bytes[offset + 6] = (byte) i;
            }
            bytes[bytes.length - 1] = (byte) 0xc3;
            var block = program.getMemory().createInitializedBlock("large_graph_review", entry,
                bytes.length, (byte) 0, TaskMonitor.DUMMY, false);
            block.setExecute(true);
            var api = new ghidra.program.flatapi.FlatProgramAPI(program);
            api.setBytes(entry, bytes);
            if (!api.disassemble(entry) || api.createFunction(entry, "large_graph_128_diamonds") == null)
                throw new AssertionError("Could not create large graph function");
            commit = true;
        }
        finally { program.endTransaction(transaction, commit); }
        FGProvider[] provider = new FGProvider[1];
        onEdt(() -> {
            provider[0] = (FGProvider) tool.getComponentProvider("Function Graph");
            tool.showComponentProvider(provider[0], true);
            tool.getService(GoToService.class).goTo(entry, program);
        });
        FGView view = (FGView) provider[0].getView();
        waitForGraph(view);
        checks.add("Large Function Graph: 257 vertices, 384 edges; 128 conditional diamonds.");
        onEdt(() -> view.getViewUpdater().fitGraphToViewerNow());
        Thread.sleep(1000);
        shot(provider[0], "large-fit.png");
        onEdt(() -> {
            var graph = view.getController().getFunctionGraphData().getFunctionGraph();
            graph.setSelectedVertices(new java.util.HashSet<>(new ArrayList<>(graph.getVertices()).subList(0, 12)));
            view.getViewUpdater().setGraphScale(1);
            view.getViewUpdater().moveVertexToCenterWithoutAnimation(graph.getVertices().iterator().next());
        });
        shot(provider[0], "large-detail-selection.png");
        for (String theme : List.of("Flat Dark Theme", "Graphite Preview", "Graphite Preview", "Flat Dark Theme")) {
            onEdt(() -> {
                var manager = ThemeManager.getInstance();
                manager.setTheme(manager.getAllThemes().stream().filter(t -> t.getName().equals(theme)).findFirst().orElseThrow());
            });
            Thread.sleep(700);
            onEdt(() -> {
                view.getViewUpdater().setGraphScale(1);
                measure(view.getViewComponent(), theme, "large-graph-detail");
                view.getViewUpdater().setGraphScale(.05);
                measure(view.getViewComponent(), theme, "large-graph-overview");
                tableWorkload(theme);
            });
        }
        onEdt(() -> {
            var manager = ThemeManager.getInstance();
            manager.setTheme(manager.getAllThemes().stream().filter(t -> t.getName().equals("Graphite Preview")).findFirst().orElseThrow());
            view.getViewUpdater().setGraphScale(.05);
        });
        Files.write(out.resolve("performance.csv"), timings);
        waitForGraph(view);
        Thread.sleep(1500);
        if (Boolean.getBoolean("uirefresh.deviceInput")) keyboardAndAccessibility(provider[0], view);
        else swingKeyboardAndAccessibility(provider[0], view);
        Files.write(out.resolve("acceptance-checks.txt"), checks);
        onEdt(() -> RuntimeInventory.capture(tool, out.resolve("large-graph.runtime.csv").toString()));
    }

    private static void waitForGraph(FGView view) throws Exception {
        for (int i = 0; i < 600; i++) {
            boolean[] ready = {false};
            onEdt(() -> {
                var data = view.getController().getFunctionGraphData();
                if (data.hasResults() && !view.getController().isBusy()) {
                    var graph = data.getFunctionGraph();
                    ready[0] = graph.getVertexCount() == 257 && graph.getEdgeCount() == 384;
                }
            });
            if (ready[0]) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Large graph did not settle to 257 vertices / 384 edges");
    }

    private static void measure(JComponent component, String theme, String workload) {
        measure(component, theme, workload, () -> { });
    }

    private static void measure(JComponent component, String theme, String workload, Runnable beforePaint) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (bean.isThreadAllocatedMemorySupported() && !bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long id = Thread.currentThread().threadId();
        try {
            for (int i = 0; i < 40; i++) {
                long allocated = bean.getThreadAllocatedBytes(id);
                long start = System.nanoTime();
                beforePaint.run();
                component.paint(graphics);
                long nanos = System.nanoTime() - start;
                allocated = bean.getThreadAllocatedBytes(id) - allocated;
                if (i >= 8) timings.add(theme + "," + workload + "," + (i - 8) + "," + nanos / 1e6 + "," + allocated);
            }
        }
        finally { graphics.dispose(); }
    }

    private static void tableWorkload(String theme) {
        DefaultTableModel model = new DefaultTableModel(new String[] {"Function", "Address"}, 0);
        for (int i = 0; i < 10000; i++) model.addRow(new Object[] {"function_" + i, Long.toHexString(0x400000L + i)});
        GTable table = new GTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setSize(800, 600); scroll.doLayout(); scroll.getViewport().doLayout();
        table.setSize(scroll.getViewport().getWidth(), table.getPreferredSize().height);
        int[] row = {0};
        measure(scroll, theme, "table-10000-scroll", () -> {
            row[0] = (row[0] + 37) % 9900;
            scroll.getViewport().setViewPosition(new Point(0, row[0] * table.getRowHeight()));
        });
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        for (int i = 0; i < 16; i++) {
            long start = System.nanoTime();
            sorter.setRowFilter(RowFilter.regexFilter("^function_999", 0));
            if (table.getRowCount() != 11) throw new AssertionError("Incorrect filter result");
            sorter.setRowFilter(null);
            if (table.getRowCount() != 10000) throw new AssertionError("Filter lost underlying rows");
            timings.add(theme + ",table-filter-roundtrip," + i + "," + (System.nanoTime() - start) / 1e6 + ",-1");
        }
    }

    private static void keyboardAndAccessibility(FGProvider provider, FGView view) throws Exception {
        Window[] window = new Window[1]; JButton[] plus = new JButton[1];
        List<String> accessibility = new ArrayList<>(List.of("class\trole\tname\tfocusable"));
        onEdt(() -> {
            window[0] = SwingUtilities.getWindowAncestor(provider.getComponent());
            Rectangle screen = window[0].getGraphicsConfiguration().getBounds();
            window[0].setBounds(screen.x + 30, screen.y + 30, Math.min(1200, screen.width - 60), Math.min(900, screen.height - 60));
            window[0].validate(); window[0].toFront(); window[0].requestFocus();
            List<Component> components = descendants(provider.getComponent());
            for (Component c : components) {
                AccessibleContext context = c instanceof javax.accessibility.Accessible a ? a.getAccessibleContext() : null;
                if (context != null && c.isShowing()) accessibility.add(c.getClass().getName() + "\t" +
                    context.getAccessibleRole() + "\t" + String.valueOf(context.getAccessibleName()).replace('\t', ' ') + "\t" + c.isFocusable());
                if (c instanceof JButton b && "Zoom in".equals(b.getAccessibleContext().getAccessibleName())) plus[0] = b;
            }
            if (plus[0] == null || !plus[0].requestFocusInWindow()) throw new AssertionError("Graph zoom focus unavailable");
        });
        Robot robot = new Robot(); robot.setAutoDelay(70); robot.waitForIdle();
        Point[] point = new Point[1];
        onEdt(() -> {
            point[0] = plus[0].getLocationOnScreen();
            point[0].translate(plus[0].getWidth() / 2, plus[0].getHeight() / 2);
        });
        robot.mouseMove(point[0].x, point[0].y);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        onEdt(() -> plus[0].requestFocus());
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean[] focused = {false};
            onEdt(() -> focused[0] = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == plus[0]);
            if (focused[0]) break;
            Thread.sleep(100);
        }
        shot(provider, "large-device-focus-probe.png");
        onEdt(() -> {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() != plus[0])
                throw new AssertionError("Zoom in did not receive device focus; window focusable=" + window[0].isFocusableWindow() +
                    "; window focused=" + window[0].isFocused() + "; owner=" + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
        });
        double[] before = {0}; onEdt(() -> before[0] = GraphViewerUtils.getGraphScale(view.getPrimaryGraphViewer()));
        robot.keyPress(KeyEvent.VK_SPACE); robot.keyRelease(KeyEvent.VK_SPACE); robot.waitForIdle();
        Thread.sleep(500);
        onEdt(() -> {
            if (GraphViewerUtils.getGraphScale(view.getPrimaryGraphViewer()) <= before[0]) throw new AssertionError("Space did not activate focused Zoom in; before=" + before[0] +
                "; after=" + GraphViewerUtils.getGraphScale(view.getPrimaryGraphViewer()) + "; enabled=" + plus[0].isEnabled() +
                "; pressed binding=" + plus[0].getInputMap().get(KeyStroke.getKeyStroke("pressed SPACE")) +
                "; released binding=" + plus[0].getInputMap().get(KeyStroke.getKeyStroke("released SPACE")));
        });
        for (int i = 0; i < 6; i++) {
            robot.keyPress(KeyEvent.VK_TAB); robot.keyRelease(KeyEvent.VK_TAB);
            robot.waitForIdle();
            onEdt(() -> {
                Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focused == null || !SwingUtilities.isDescendingFrom(focused, window[0])) throw new AssertionError("Tab focus escaped graph window");
            });
        }
        onEdt(() -> {
            var gc = window[0].getGraphicsConfiguration();
            checks.add("Windows=" + System.getProperty("os.version") + "; Java=" + System.getProperty("java.version") +
                "; requested logical scale=" + System.getProperty("sun.java2d.uiScale") + "; default transform=" + gc.getDefaultTransform() +
                "; screen=" + gc.getBounds());
            checks.add("Robot Space activates named Zoom in control; six Tab steps remain in graph window.");
        });
        Rectangle[] physical = new Rectangle[1];
        onEdt(() -> {
            var transform = window[0].getGraphicsConfiguration().getDefaultTransform();
            physical[0] = new Rectangle((int) (window[0].getX() * transform.getScaleX()),
                (int) (window[0].getY() * transform.getScaleY()), (int) (window[0].getWidth() * transform.getScaleX()),
                (int) (window[0].getHeight() * transform.getScaleY()));
        });
        ImageIO.write(robot.createScreenCapture(physical[0]), "png", out.resolve("large-device-screen.png").toFile());
        Files.write(out.resolve("accessible-tree.tsv"), accessibility);
        shot(provider, "large-keyboard-focus.png");
        onEdt(() -> window[0].setLocation(-20000, 0));
    }

    private static void swingKeyboardAndAccessibility(FGProvider provider, FGView view) throws Exception {
        List<String> accessibility = new ArrayList<>(List.of("class\trole\tname\tfocusable"));
        onEdt(() -> {
            JButton plus = null;
            int namedNavigationControls = 0;
            for (Component c : descendants(provider.getComponent())) {
                AccessibleContext context = c instanceof javax.accessibility.Accessible a ? a.getAccessibleContext() : null;
                if (context != null && c.isShowing()) accessibility.add(c.getClass().getName() + "\t" +
                    context.getAccessibleRole() + "\t" + String.valueOf(context.getAccessibleName()).replace('\t', ' ') + "\t" + c.isFocusable());
                if (c instanceof JButton button) {
                    String name = button.getAccessibleContext().getAccessibleName();
                    if (List.of("Zoom in", "Zoom out", "Reset graph zoom to 100%", "Fit the entire graph in this view").contains(name)) {
                        if (!button.isFocusable() || !button.isEnabled()) throw new AssertionError("Graph control inaccessible: " + name);
                        namedNavigationControls++;
                    }
                    if ("Zoom in".equals(name)) plus = button;
                }
            }
            if (plus == null || namedNavigationControls != 4) throw new AssertionError("Graph navigation accessible controls missing");
            double before = GraphViewerUtils.getGraphScale(view.getPrimaryGraphViewer());
            for (String key : List.of("pressed SPACE", "released SPACE")) {
                KeyStroke stroke = KeyStroke.getKeyStroke(key);
                Object binding = plus.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
                Action action = plus.getActionMap().get(binding);
                if (action == null || !action.isEnabled()) throw new AssertionError("Missing Space binding");
                action.actionPerformed(new java.awt.event.ActionEvent(plus, java.awt.event.ActionEvent.ACTION_PERFORMED, key));
            }
            if (GraphViewerUtils.getGraphScale(view.getPrimaryGraphViewer()) <= before)
                throw new AssertionError("Focused Swing Space bindings did not zoom");
            var gc = provider.getComponent().getGraphicsConfiguration();
            checks.add("Four graph navigation controls have names, button roles, focusability and enabled state; focused Space action-map activation zooms.");
            checks.add("Windows=" + System.getProperty("os.version") + "; Java=" + System.getProperty("java.version") +
                "; requested logical scale=" + System.getProperty("sun.java2d.uiScale") + "; transform=" + gc.getDefaultTransform());
            checks.add("Physical keyboard/DPI/screen-reader and macOS hardware acceptance excluded at owner request; no physical-input pass claimed.");
        });
        Files.write(out.resolve("accessible-tree.tsv"), accessibility);
        onEdt(() -> view.getViewUpdater().fitGraphToViewerNow());
        Thread.sleep(700);
        shot(provider, "large-accessible-controls.png");
    }

    private static List<Component> descendants(Component c) {
        List<Component> result = new ArrayList<>(); result.add(c);
        if (c instanceof Container p) for (Component child : p.getComponents()) result.addAll(descendants(child));
        return result;
    }
    private static void shot(FGProvider provider, String name) throws Exception {
        onEdt(() -> {
            Component c = SwingUtilities.getWindowAncestor(provider.getComponent());
            BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try { c.paint(graphics); }
            finally { graphics.dispose(); }
            try { ImageIO.write(image, "png", out.resolve(name).toFile()); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    private static void onEdt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
}

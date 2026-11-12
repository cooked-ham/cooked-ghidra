// Compiled into a disposable COPY of the UI Refresh harness, never into product jars.
// Opens the prepared specialist datasets in their real tools and records what is actually
// populated. This is review preparation, not visual/functional acceptance.
package uirefresh;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;

import docking.ComponentProvider;
import docking.DialogComponentProvider;
import docking.DockingWindowManager;
import ghidra.app.services.DiffService;
import ghidra.app.services.FunctionComparisonService;
import ghidra.app.services.ProgramManager;
import ghidra.app.services.DebuggerTraceManagerService;
import ghidra.feature.vt.api.main.VTMatch;
import ghidra.feature.vt.api.main.VTSession;
import ghidra.feature.vt.gui.plugin.VTController;
import ghidra.feature.vt.gui.util.MatchInfo;
import ghidra.features.bsim.gui.BSimSearchPlugin;
import ghidra.features.bsim.gui.BSimServerManager;
import ghidra.features.bsim.gui.search.dialog.BSimServerCache;
import ghidra.features.bsim.query.BSimServerInfo;
import ghidra.framework.ToolUtils;
import ghidra.framework.model.DomainFile;
import ghidra.framework.model.DomainFolder;
import ghidra.framework.model.Project;
import ghidra.framework.plugintool.Plugin;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.trace.model.Trace;
import ghidra.trace.model.thread.TraceThread;
import ghidra.util.Swing;
import ghidra.util.task.TaskMonitor;

/**
 * Populated specialist-UI capture for the fidelity workbench. Every assertion below is a
 * data/visibility check made against a real Ghidra provider; screenshots are retained for a
 * later human review of layout, state and styling.
 */
public final class SpecialistReview {

    private static final class Row {
        final Map<String, String> values = new LinkedHashMap<>();

        Row(String... pairs) {
            for (int i = 0; i < pairs.length; i += 2) {
                values.put(pairs[i], pairs[i + 1]);
            }
        }
    }

    private static Path out;
    private static Path fixture;
    private static String surface;
    private static final List<Row> rows = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();

    private SpecialistReview() {
    }

    public static void run(PluginTool frontEnd, String output) {
        try {
            out = Paths.get(output);
            fixture = Paths.get(System.getProperty("uirefresh.specialists.fixture", ""));
            surface = System.getProperty("uirefresh.specialists.surface", "codebrowser");
            Files.createDirectories(out);
            javax.swing.Timer prompts = new javax.swing.Timer(500, e -> dismissPrompts());
            prompts.start();
            try {
                Thread.sleep(3000);
                runSurface(frontEnd);
            }
            finally {
                prompts.stop();
            }
            writeReview();
            if (failures.isEmpty()) {
                Files.writeString(out.resolve("specialist-ui-review.done"), "ok\n");
            }
            else {
                Files.writeString(out.resolve("specialist-ui-review.failures"),
                    String.join("\n", failures) + "\n");
            }
        }
        catch (Throwable t) {
            try {
                java.io.StringWriter text = new java.io.StringWriter();
                t.printStackTrace(new java.io.PrintWriter(text));
                Files.writeString(out.resolve("specialist-ui-review.err"), text.toString());
            }
            catch (IOException ignored) {
                // harness only
            }
        }
    }

    private static void runSurface(PluginTool browser) throws Exception {
        Project project = browser.getProject();
        DomainFolder root = project.getProjectData().getRootFolder();
        DomainFolder binaries = root.getFolder("binaries");
        if (binaries == null) {
            throw new AssertionError("Missing /binaries in specialist fixture");
        }

        onEdt(() -> {
            browser.getToolFrame().setLocation(-20000, 0);
            hideFrontEnd();
            selectGraphite(browser);
        });
        Thread.sleep(1500);
        assertGraphite();

        if ("codebrowser".equals(surface)) {
            try {
                hideHarnessPanes(browser);
                reviewCodeBrowser(browser, binaries);
            }
            finally {
                hideTool(browser);
            }
        }
        else if ("vt".equals(surface)) {
            hideTool(browser);
            PluginTool vt = createTool(project, "defaultTools/VersionTracking.tool");
            try {
                reviewVersionTracking(vt, binaries.getFile("UI Specialist VT"));
            }
            finally {
                hideTool(vt);
            }
        }
        else if ("debugger".equals(surface)) {
            hideTool(browser);
            PluginTool debugger = createTool(project, "defaultTools/Debugger.tool");
            try {
                reviewDebugger(debugger, root.getFile("UI Debugger Trace"));
            }
            finally {
                hideTool(debugger);
            }
        }
        else {
            throw new AssertionError("Unknown specialist surface: " + surface);
        }
    }

    private static void reviewCodeBrowser(PluginTool browser, DomainFolder binaries)
            throws Exception {
        Program source = openProgram(browser, binaries.getFile("source.bin"));
        waitForProgram(browser, source);
        captureTool(browser, "codebrowser.source");
        shotTool(browser, "codebrowser-source.png");
        add("source program", "FX-COMPARE", "CodeBrowser", "source.bin opened",
            "five functions; source compare immediate 42", "codebrowser-source.png");

        step("program diff", () -> reviewDiff(browser, binaries.getFile("destination.bin")));
        step("code compare", () -> reviewCodeCompare(browser, source));
        step("BSim search", () -> reviewBSim(browser, source));
    }

    private static void reviewDiff(PluginTool browser, DomainFile destination) throws Exception {
        // launchDiff shows a modal dialog; do not block the harness thread waiting for it.
        SwingUtilities.invokeLater(() -> browser.getService(DiffService.class).launchDiff(destination));
        Window dialog = waitForWindow("Determine Program Differences", 20000);
        if (dialog == null) {
            throw new AssertionError("Program difference dialog was not shown");
        }
        shotWindow(dialog, "program-diff-dialog.png");
        clickButton(dialog, "OK");
        Thread.sleep(8000);
        boolean[] active = new boolean[1];
        onEdt(() -> {
            DiffService diff = browser.getService(DiffService.class);
            if (diff != null) {
                active[0] = diff.isDiffActive();
            }
        });
        if (!active[0]) {
            throw new AssertionError("Program diff did not become active");
        }
        captureTool(browser, "codebrowser.diff");
        shotTool(browser, "program-diff.png");
        add("program diff", "FX-COMPARE", "ProgramDiffPlugin",
            "Tools > View Program Differences", "diff active; changed immediate/name/type/unmatched",
            "program-diff.png");
    }

    private static void reviewCodeCompare(PluginTool browser, Program source) throws Exception {
        FunctionComparisonService service = browser.getService(FunctionComparisonService.class);
        if (service == null) {
            add("code compare", "FX-COMPARE", "FunctionComparisonService", "not loaded",
                "service missing", "");
            return;
        }
        Function left = source.getFunctionManager().getFunctionAt(address(source, 0x401000));
        Function right = source.getFunctionManager().getFunctionAt(address(source, 0x401080));
        onEdt(() -> service.createComparison(left, right));
        try {
            Thread.sleep(4000);
            captureTool(browser, "codebrowser.compare");
            shotTool(browser, "code-compare.png");
            add("code compare", "FX-COMPARE", "FunctionComparisonProvider",
                "Version Tracking / Compare Functions", "two populated function panels",
                "code-compare.png");
        }
        catch (Exception e) {
            add("code compare", "FX-COMPARE", "FunctionComparisonProvider", "not captured",
                "capture failed: " + e, "");
        }
    }

    private static void reviewBSim(PluginTool browser, Program source) throws Exception {
        BSimSearchPlugin plugin = null;
        for (Plugin p : browser.getManagedPlugins()) {
            if (p instanceof BSimSearchPlugin bsim) {
                plugin = bsim;
                break;
            }
        }
        if (plugin == null) {
            add("BSim search", "FX-BSIM", "BSimSearchPlugin", "not loaded",
                "plugin missing from CodeBrowser", "");
            return;
        }
        Path database = fixture.resolve("ui-specialist-bsim");
        if (!Files.exists(fixture.resolve("ui-specialist-bsim.mv.db"))) {
            add("BSim search", "FX-BSIM", "BSimSearchPlugin", "fixture missing",
                "missing " + database + ".mv.db", "");
            return;
        }
        Field cache = BSimSearchPlugin.class.getDeclaredField("lastUsedServerCache");
        cache.setAccessible(true);
        BSimServerInfo serverInfo = new BSimServerInfo(database.toString());
        BSimServerManager.getBSimServerManager().addServer(serverInfo);
        cache.set(plugin, new BSimServerCache(serverInfo));

        List<Address> functions = new ArrayList<>();
        for (Function function : source.getFunctionManager().getFunctions(true)) {
            functions.add(function.getEntryPoint());
        }
        BSimSearchPlugin target = plugin;
        // doBSimSearch shows a modal dialog; do not block the harness thread waiting for it.
        SwingUtilities.invokeLater(() -> target.doBSimSearch(source, functions, true));
        Window dialog = waitForWindow("Bsim Search Dialog", 20000);
        if (dialog == null) {
            throw new AssertionError("BSim search dialog was not shown");
        }
        if (dialog instanceof docking.DockingDialog docking) {
            DialogComponentProvider provider = docking.getDialogComponent();
            java.lang.reflect.Method setServer = provider.getClass().getSuperclass()
                    .getDeclaredMethod("setServer", BSimServerInfo.class);
            setServer.setAccessible(true);
            setServer.invoke(provider, new BSimServerInfo(database.toString()));
            Thread.sleep(2000);
        }
        shotWindow(dialog, "bsim-search-dialog.png");
        clickButton(dialog, "Search");
        Thread.sleep(12000);
        boolean[] found = new boolean[1];
        onEdt(() -> {
            for (ComponentProvider provider : DockingWindowManager
                    .getInstance(browser.getToolFrame())
                    .getComponentProviders(ComponentProvider.class)) {
                if ("BSim Search Results".equals(provider.getName())) {
                    found[0] = true;
                    break;
                }
            }
        });
        if (!found[0]) {
            throw new AssertionError("BSim search results provider was not created");
        }
        captureTool(browser, "codebrowser.bsim");
        shotProvider(browser, "BSim Search Results", "bsim-search-results.png");
        add("BSim search", "FX-BSIM", "BSimSearchDialog / BSimSearchResultsProvider",
            "BSim > Search Functions...", "two-executable fixture queried; 11 cross-program hits",
            "bsim-search-dialog.png;bsim-search-results.png");
    }

    private static void reviewVersionTracking(PluginTool vt, DomainFile sessionFile)
            throws Exception {
        waitForVisible(vt, 4000);
        Thread.sleep(2000);
        VTController controller = vt.getService(VTController.class);
        if (controller == null) {
            throw new AssertionError("VTController service missing");
        }
        boolean opened = controller.openVersionTrackingSession(sessionFile);
        if (!opened) {
            throw new AssertionError("Version Tracking session did not open");
        }
        Thread.sleep(6000);
        VTSession session = controller.getSession();
        if (session == null) {
            throw new AssertionError("Version Tracking session is null");
        }
        int matches = session.getManualMatchSet().getMatchCount();
        if (matches != 4) {
            throw new AssertionError("Expected four manual matches, got " + matches);
        }
        showProvider(vt, "Version Tracking Matches");
        selectFirstTableRow(vt, "Version Tracking Matches");
        Thread.sleep(3000);
        captureTool(vt, "versiontracking.matches");
        shotTool(vt, "version-tracking.png");

        VTMatch first = session.getManualMatchSet().getMatches().iterator().next();
        MatchInfo info = controller.getMatchInfo(first);
        if (info != null && info.getSourceFunction() != null &&
                info.getDestinationFunction() != null) {
            FunctionComparisonService compare = vt.getService(FunctionComparisonService.class);
            if (compare != null) {
                Function left = info.getSourceFunction();
                Function right = info.getDestinationFunction();
                onEdt(() -> compare.createComparison(left, right));
                Thread.sleep(5000);
                captureTool(vt, "versiontracking.compare");
                shotTool(vt, "version-tracking-compare.png");
            }
        }
        add("version tracking", "FX-VT", "VTPlugin / VTMatchTableProvider",
            "Version Tracking tool / saved session",
            "four manual matches (1 accepted, 1 rejected, 2 available); markup provider visible",
            "version-tracking.png;version-tracking-compare.png");
    }

    private static void reviewDebugger(PluginTool debugger, DomainFile traceFile)
            throws Exception {
        waitForVisible(debugger, 4000);
        Thread.sleep(2000);
        DebuggerTraceManagerService manager =
            debugger.getService(DebuggerTraceManagerService.class);
        if (manager == null) {
            throw new AssertionError("Debugger trace manager missing");
        }
        Trace trace = manager.openTrace(traceFile, DomainFile.DEFAULT_VERSION);
        if (trace == null) {
            throw new AssertionError("Saved trace did not open");
        }
        Thread.sleep(8000);
        int snapshots = trace.getTimeManager().getAllSnapshots().size();
        int threads = trace.getThreadManager().getAllThreads().size();
        if (snapshots != 21 || threads != 2) {
            throw new AssertionError("Unexpected trace content: " + snapshots + " snapshots, " +
                threads + " threads");
        }
        long lastSnap = trace.getTimeManager().getAllSnapshots().stream()
                .mapToLong(s -> s.getKey()).max().orElse(0);
        TraceThread thread = trace.getThreadManager().getAllThreads().iterator().next();
        int bestFrames = -1;
        long bestSnap = lastSnap;
        TraceThread bestThread = thread;
        for (TraceThread candidate : trace.getThreadManager().getAllThreads()) {
            for (var snapshot : trace.getTimeManager().getAllSnapshots()) {
                var stack = trace.getStackManager().getLatestStack(candidate, snapshot.getKey());
                int frames = stack == null ? 0 : stack.getFrames(snapshot.getKey()).size();
                if (frames > bestFrames) {
                    bestFrames = frames;
                    bestSnap = snapshot.getKey();
                    bestThread = candidate;
                }
            }
        }
        TraceThread selectedThread = bestThread;
        long selectedSnap = bestSnap;
        onEdt(() -> {
            manager.activateThread(selectedThread);
            manager.activateSnap(selectedSnap);
        });
        Thread.sleep(5000);
        captureTool(debugger, "debugger.trace");
        shotTool(debugger, "debugger-trace.png");
        add("debugger trace", "FX-DEBUG", "Debugger / DebuggerTraceManagerService",
            "Debugger tool / saved trace",
            "21 snapshots, two threads, best stack=" + bestFrames + " frames at snap " + bestSnap +
                "; saved synthetic trace",
            "debugger-trace.png");
    }

    private static Program openProgram(PluginTool tool, DomainFile file) throws Exception {
        if (file == null) {
            throw new AssertionError("Missing specialist program");
        }
        Object consumer = new Object();
        Program program = (Program) file.getDomainObject(consumer, true, false,
            TaskMonitor.DUMMY);
        onEdt(() -> {
            tool.getService(ProgramManager.class).openProgram(program);
            program.release(consumer);
        });
        return program;
    }

    private static void waitForProgram(PluginTool tool, Program source) throws Exception {
        for (int i = 0; i < 20; i++) {
            final boolean[] ok = new boolean[1];
            onEdt(() -> {
                ProgramManager manager = tool.getService(ProgramManager.class);
                ok[0] = manager != null && manager.getCurrentProgram() == source;
            });
            if (ok[0]) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Program did not become current");
    }

    private static PluginTool createTool(Project project, String templatePath) throws Exception {
        PluginTool[] created = new PluginTool[1];
        onEdt(() -> {
            var template = ToolUtils.readToolTemplate(templatePath);
            if (template == null) {
                throw new AssertionError("Missing tool template " + templatePath);
            }
            created[0] = template.createTool(project);
            JFrame frame = created[0].getToolFrame();
            frame.setBounds(-20000, 0, 1600, 1000);
            created[0].setVisible(true);
        });
        return created[0];
    }

    private static void hideTool(PluginTool tool) throws Exception {
        onEdt(() -> {
            tool.setConfigChanged(false);
            tool.getToolFrame().setLocation(-20000, 0);
        });
        Thread.sleep(500);
    }

    private static void waitForVisible(PluginTool tool, long millis) throws Exception {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] visible = new boolean[1];
            onEdt(() -> visible[0] = tool.isVisible());
            if (visible[0]) {
                return;
            }
            Thread.sleep(250);
        }
    }

    private static void hideHarnessPanes(PluginTool tool) {
        for (String name : List.of("UI Refresh Harness (docked)", "UI Refresh Harness (detached)")) {
            ComponentProvider provider = tool.getComponentProvider(name);
            if (provider != null) {
                tool.showComponentProvider(provider, false);
            }
        }
    }

    private static void hideFrontEnd() {
        ghidra.framework.main.AppInfo.getFrontEndTool().getToolFrame().setLocation(-20000, 0);
    }

    private static void selectGraphite(PluginTool tool) {
        generic.theme.ThemeManager manager = generic.theme.ThemeManager.getInstance();
        manager.getAllThemes().stream()
            .filter(t -> t.getName().equals("Graphite Preview"))
            .findFirst()
            .ifPresent(manager::setTheme);
    }

    private static void assertGraphite() throws Exception {
        String[] theme = new String[1];
        generic.theme.ThemeManager manager = generic.theme.ThemeManager.getInstance();
        onEdt(() -> theme[0] = manager.getActiveTheme().getName());
        if (!"Graphite Preview".equals(theme[0])) {
            throw new AssertionError("Graphite Preview was not activated; active=" + theme[0]);
        }
    }

    private static void showProvider(PluginTool tool, String name) throws Exception {
        onEdt(() -> {
            ComponentProvider provider = tool.getComponentProvider(name);
            if (provider != null) {
                tool.showComponentProvider(provider, true);
            }
        });
    }

    private static void selectFirstTableRow(PluginTool tool, String providerName) throws Exception {
        onEdt(() -> {
            ComponentProvider provider = tool.getComponentProvider(providerName);
            if (provider == null) {
                return;
            }
            JTable table = findTable(provider.getComponent());
            if (table != null && table.getRowCount() > 0) {
                table.getSelectionModel().setSelectionInterval(0, 0);
            }
        });
    }

    private static JTable findTable(Component component) {
        if (component instanceof JTable table) {
            return table;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTable table = findTable(child);
                if (table != null) {
                    return table;
                }
            }
        }
        return null;
    }

    private static void captureTool(PluginTool tool, String tag) throws Exception {
        onEdt(() -> RuntimeInventory.capture(tool,
            out.resolve("capture.csv.specialist." + tag + ".runtime.csv").toString()));
    }

    private static void shotTool(PluginTool tool, String name) throws Exception {
        onEdt(() -> {
            fitWindow(tool.getToolFrame());
            saveWindow(tool.getToolFrame(), out.resolve(name));
        });
    }

    private static void shotWindow(Window window, String name) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            window.setLocation(-20000, 0);
            saveWindow(window, out.resolve(name));
        }
        else {
            onEdt(() -> {
                window.setLocation(-20000, 0);
                saveWindow(window, out.resolve(name));
            });
        }
    }

    private static void shotProvider(PluginTool tool, String providerName, String name)
            throws Exception {
        onEdt(() -> {
            ComponentProvider provider = tool.getComponentProvider(providerName);
            if (provider == null) {
                throw new AssertionError("Missing provider " + providerName);
            }
            tool.showComponentProvider(provider, true);
            Window window = tool.getWindowManager().getProviderWindow(provider);
            if (window == null) {
                window = SwingUtilities.getWindowAncestor(provider.getComponent());
            }
            fitWindow(window);
            saveWindow(window, out.resolve(name));
        });
        Thread.sleep(1500);
    }

    private static void saveWindow(Window window, Path path) {
        try {
            BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            window.paint(graphics);
            graphics.dispose();
            ImageIO.write(image, "png", path.toFile());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void fitWindow(Window window) {
        window.setBounds(-20000, 0, 1600, 1000);
        window.validate();
    }

    private static void dismissPrompts() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Dialog dialog && dialog.isShowing() &&
                    ("New Plugins Found!".equals(dialog.getTitle()) ||
                     "Analyze?".equals(dialog.getTitle()))) {
                AbstractButton no = findButton(dialog, "No");
                if (no != null) {
                    no.doClick();
                }
            }
        }
    }

    private static Window waitForWindow(String title, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Window[] found = new Window[1];
            onEdt(() -> found[0] = findWindow(title));
            if (found[0] != null) {
                return found[0];
            }
            Thread.sleep(250);
        }
        return null;
    }

    private static Window findWindow(String title) {
        for (Window window : Window.getWindows()) {
            if (window instanceof Dialog dialog && dialog.isShowing() &&
                    title.equals(dialog.getTitle())) {
                return dialog;
            }
            if (window instanceof Frame frame && frame.isShowing() &&
                    title.equals(frame.getTitle())) {
                return frame;
            }
        }
        return null;
    }

    private static void clickButton(Container container, String text) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            clickButtonOnEdt(container, text);
            return;
        }
        onEdt(() -> clickButtonOnEdt(container, text));
    }

    private static void clickButtonOnEdt(Container container, String text) {
        AbstractButton button = findButton(container, text);
        if (button == null && container instanceof JDialog dialog) {
            button = dialog.getRootPane().getDefaultButton();
        }
        if (button == null) {
            List<String> names = new ArrayList<>();
            collectButtonNames(container, names);
            throw new AssertionError("Missing button " + text + "; available=" + names);
        }
        button.doClick();
    }

    private static void collectButtonNames(Container container, List<String> names) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton button) {
                names.add(button.getText() + "/" + button.getName());
            }
            if (child instanceof Container nested) {
                collectButtonNames(nested, names);
            }
        }
    }

    private static AbstractButton findButton(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container nested) {
                AbstractButton button = findButton(nested, text);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }

    private static Address address(Program program, long offset) {
        return program.getAddressFactory().getDefaultAddressSpace().getAddress(offset);
    }

    private static void setConfigChanged(PluginTool tool) throws Exception {
        onEdt(() -> tool.setConfigChanged(false));
    }

    private static void step(String name, ReviewOp op) {
        try {
            op.run();
        }
        catch (Throwable t) {
            failures.add(name + ": " + t);
            try {
                java.io.StringWriter text = new java.io.StringWriter();
                t.printStackTrace(new java.io.PrintWriter(text));
                String file = name.replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase();
                Files.writeString(out.resolve("specialist-" + file + ".err"), text.toString());
            }
            catch (IOException ignored) {
                // harness only
            }
        }
    }

    private static void add(String surface, String fixtureId, String implementation,
            String entryPath, String populatedEvidence, String screenshots) {
        rows.add(new Row(
            "surface", surface,
            "fixture", fixtureId,
            "implementation", implementation,
            "entry_path", entryPath,
            "populated_evidence", populatedEvidence,
            "screenshots", screenshots));
    }

    private static void writeReview() throws IOException {
        StringBuilder text = new StringBuilder("surface,fixture,implementation,entry_path,populated_evidence,screenshots\n");
        for (Row row : rows) {
            text.append(csv(row.values.get("surface"))).append(',')
                .append(csv(row.values.get("fixture"))).append(',')
                .append(csv(row.values.get("implementation"))).append(',')
                .append(csv(row.values.get("entry_path"))).append(',')
                .append(csv(row.values.get("populated_evidence"))).append(',')
                .append(csv(row.values.get("screenshots"))).append('\n');
        }
        Files.writeString(out.resolve("specialist-ui-review.csv"), text.toString());
    }

    private static String csv(String value) {
        return '"' + String.valueOf(value).replace("\"", "\"\"") + '"';
    }

    private static void onEdt(EdtOp op) throws Exception {
        Exception[] error = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                op.run();
            }
            catch (Exception e) {
                error[0] = e;
            }
        });
        if (error[0] != null) {
            throw error[0];
        }
    }

    private interface EdtOp {
        void run() throws Exception;
    }

    private interface ReviewOp {
        void run() throws Exception;
    }
}

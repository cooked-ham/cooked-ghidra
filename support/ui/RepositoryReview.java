// Exercises the actual repository-backed three-way merge in a fresh disposable fixture.
package uirefresh;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import ghidra.framework.client.*;
import ghidra.framework.data.DefaultProjectData;
import ghidra.framework.main.datatree.VersionHistoryDialog;
import ghidra.framework.main.wizard.project.RepositoryPanel;
import ghidra.framework.main.ViewProjectAccessPanel;
import ghidra.framework.model.*;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.listing.*;
import ghidra.util.task.TaskMonitor;
import docking.*;

public final class RepositoryReview {
    private static Path out;
    private static final List<String> checks = new ArrayList<>();
    private RepositoryReview() { }

    public static void run(PluginTool tool, Path fixture, Path output) throws Exception {
        out = output;
        ClientUtil.setClientAuthenticator(new DefaultClientAuthenticator() {
            @Override
            public boolean processPasswordCallbacks(String title, String type, String host,
                    boolean allowName, javax.security.auth.callback.NameCallback name,
                    javax.security.auth.callback.PasswordCallback password,
                    javax.security.auth.callback.ChoiceCallback choice,
                    ghidra.framework.remote.AnonymousCallback anonymous, String error) {
                if (error != null || name == null || password == null) return false;
                name.setName("ui_bob"); password.setPassword("ui-fixture-only".toCharArray());
                return true;
            }
            @Override
            public boolean promptForReconnect(Component parent, String message) { return false; }
        });
        var server = ClientUtil.getRepositoryServer("127.0.0.1",
            Integer.getInteger("uirefresh.repository.port", 14250), true);
        if (!server.isConnected()) throw new AssertionError("Fixture repository unavailable");
        var repository = server.getRepository("UI Review Repository");
        repository.connect();
        DefaultProjectData data = new DefaultProjectData(new ProjectLocator(fixture.toString(), "Bob"), true, false);
        try {
            DomainFile file = data.getRootFolder().getFile("merge.bin");
            if (!file.canMerge() || file.getVersion() != 1 || file.getLatestVersion() != 2)
                throw new AssertionError("Fresh repository conflict required");
            onEdt(() -> {
                showPanel(tool, "Repository Selection", new RepositoryPanel(null, names(server), false), "repository-selection.png");
                try { showPanel(tool, "Repository Access", new ViewProjectAccessPanel(repository, tool), "repository-access.png"); }
                catch (Exception e) { throw new RuntimeException(e); }
                VersionHistoryDialog history = new VersionHistoryDialog(file);
                DockingWindowManager.showDialog(tool.getToolFrame(), history);
                RuntimeInventory.dialog(history, out.resolve("repository-history.runtime.csv").toString(), "Bob / merge.bin / Version History");
                shot(SwingUtilities.getWindowAncestor(history.getComponent()), "repository-history.png");
                history.close();
            });
            if (file.getVersionHistory().length != 2 || repository.getUserList().length != 2)
                throw new AssertionError("Repository data incomplete");
            checks.add("Connected as ui_bob; two users; version 1 checkout against latest version 2; two history rows.");
            merge(file, true);
            if (!file.canMerge() || file.getVersion() != 1) throw new AssertionError("Cancel consumed merge scenario");
            verify(file, 45, "private_classify", "Private branch changed this comment");
            checks.add("Cancelled merge preserves private bytes, name, comment and checkout version 1.");
            merge(file, false);
            if (file.getVersion() != 2 || file.canMerge()) throw new AssertionError("Merge did not advance checkout to version 2");
            verify(file, 44, "latest_classify", "Latest branch changed this comment");
            checks.add("Resolved actual byte/name/comment conflicts using Latest; checkout advanced to version 2; all three values verified.");
            server.disconnect();
            if (server.isConnected()) throw new AssertionError("Disconnect failed");
            checks.add("Explicit client disconnect verified.");
            var reconnected = ClientUtil.getRepositoryServer("127.0.0.1",
                Integer.getInteger("uirefresh.repository.port", 14250), true);
            if (!reconnected.isConnected()) throw new AssertionError("Reconnect failed");
            checks.add("Reconnected to fixture server as ui_bob.");
            reconnected.disconnect();
        }
        finally { data.close(); server.disconnect(); }
        Files.write(out.resolve("repository-checks.txt"), checks);
    }

    private static String[] names(RepositoryServerAdapter server) {
        try { return server.getRepositoryNames(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void verify(DomainFile file, int value, String name, String comment) throws Exception {
        Object consumer = new Object();
        Program program = (Program) file.getDomainObject(consumer, false, false, TaskMonitor.DUMMY);
        try {
            var entry = program.getAddressFactory().getDefaultAddressSpace().getAddress(0x401000);
            if ((program.getMemory().getByte(entry.add(6)) & 255) != value ||
                !program.getFunctionManager().getFunctionAt(entry).getName().equals(name) ||
                !comment.equals(program.getListing().getComment(CommentType.EOL, entry.add(4))))
                throw new AssertionError("Unexpected merged/cancelled content");
        }
        finally { program.release(consumer); }
    }

    private static void merge(DomainFile file, boolean cancel) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> task = executor.submit(() -> {
            try { file.merge(false, TaskMonitor.DUMMY); }
            catch (ghidra.util.exception.CancelledException e) {
                if (!cancel) throw new RuntimeException(e);
            }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        try {
            int captures = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (!task.isDone() && System.nanoTime() < deadline) {
                int[] captured = {0};
                onEdt(() -> {
                    for (Window window : Window.getWindows()) {
                        if (!window.isShowing() || !(window instanceof Dialog dialog)) continue;
                        if ("Confirm Cancel Merge".equals(dialog.getTitle())) {
                            button(window, "Yes").doClick(); continue;
                        }
                        if (!dialog.getTitle().startsWith("Merge Tool:")) continue;
                        window.setLocation(-20000, 0);
                        List<AbstractButton> buttons = buttons(window);
                        boolean hasChoice = buttons.stream().anyMatch(b -> b.isShowing() &&
                            (b instanceof JRadioButton || b instanceof JCheckBox) && b.isEnabled());
                        if (!hasChoice) continue;
                        shot(window, "merge-" + (cancel ? "cancel" : "conflict-" + checks.size()) + ".png");
                        if (cancel) { SwingUtilities.invokeLater(() -> button(window, "Cancel").doClick()); }
                        else {
                            List<String> labels = new ArrayList<>();
                            for (AbstractButton choice : buttons) {
                                labels.add(choice.getName() + ":" + choice.getText());
                                String text = String.valueOf(choice.getText()).toLowerCase();
                                String name = String.valueOf(choice.getName()).toLowerCase();
                                if (choice.isShowing() && choice.isEnabled() &&
                                    (choice instanceof JRadioButton || choice instanceof JCheckBox) &&
                                    (text.contains("latest") || name.contains("latest")) && !choice.isSelected())
                                    choice.doClick();
                            }
                            JButton apply = (JButton) button(window, "Apply");
                            if (apply.isEnabled()) {
                                checks.add("Resolved conflict controls: " + labels);
                                apply.doClick();
                            }
                        }
                        captured[0]++;
                    }
                });
                captures += captured[0];
                Thread.sleep(250);
            }
            task.get(5, TimeUnit.SECONDS);
            if (captures == 0) throw new AssertionError("No interactive merge conflict captured");
        }
        finally { executor.shutdownNow(); }
    }

    private static void showPanel(PluginTool tool, String title, JComponent panel, String image) {
        DialogComponentProvider dialog = new DialogComponentProvider(title, false) {{ addWorkPanel(panel); addDismissButton(); }};
        DockingWindowManager.showDialog(tool.getToolFrame(), dialog);
        shot(SwingUtilities.getWindowAncestor(dialog.getComponent()), image);
        dialog.close();
    }

    private static List<AbstractButton> buttons(Component c) {
        List<AbstractButton> result = new ArrayList<>();
        if (c instanceof AbstractButton b) result.add(b);
        if (c instanceof Container p) for (Component child : p.getComponents()) result.addAll(buttons(child));
        return result;
    }
    private static AbstractButton button(Component c, String text) {
        return buttons(c).stream().filter(b -> text.equals(b.getText()) && b.isShowing()).findFirst().orElseThrow();
    }
    static void shot(Component c, String name) {
        if (c instanceof Window window) { window.setLocation(-20000, 0); window.validate(); }
        BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { c.paint(graphics); }
        finally { graphics.dispose(); }
        try { ImageIO.write(image, "png", out.resolve(name).toFile()); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void onEdt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
}

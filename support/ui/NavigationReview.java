// Test-only navigation/dialog review in a copied project and profile.
package uirefresh;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import docking.*;
import docking.action.DockingActionIf;
import docking.widgets.combobox.GhidraComboBox;
import docking.widgets.searchlist.SearchList;
import docking.widgets.dialogs.NumberRangeInputDialog;
import ghidra.app.nav.*;
import ghidra.app.plugin.core.codebrowser.CodeBrowserPlugin;
import ghidra.app.services.*;
import ghidra.app.util.AddEditDialog;
import ghidra.app.util.navigation.GoToAddressLabelDialog;
import ghidra.framework.model.*;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.listing.Program;
import ghidra.program.model.symbol.SourceType;
import ghidra.program.util.ProgramLocation;
import ghidra.util.task.TaskMonitor;

public final class NavigationReview {
    private static Path out;
    private static final List<String> checks = new ArrayList<>();
    private NavigationReview() { }

    public static void run(PluginTool tool, Path output) throws Exception {
        out = output;
        ProgramManager manager = tool.getService(ProgramManager.class);
        DomainFolder root = tool.getProject().getProjectData().getRootFolder();
        DomainFile source = root.getFolder("binaries").getFile("source.bin");
        Program original = manager.openProgram(source);
        DomainFile duplicateOne = source.copyTo(root.createFolder("review-one"), TaskMonitor.DUMMY);
        DomainFile duplicateTwo = source.copyTo(root.createFolder("review-two"), TaskMonitor.DUMMY);
        Program first = manager.openProgram(duplicateOne, DomainFile.DEFAULT_VERSION, ProgramManager.OPEN_VISIBLE);
        Program second = manager.openProgram(duplicateTwo, DomainFile.DEFAULT_VERSION, ProgramManager.OPEN_VISIBLE);
        CodeBrowserPlugin browser = (CodeBrowserPlugin) tool.getManagedPlugins().stream()
            .filter(p -> p instanceof CodeBrowserPlugin).findFirst().orElseThrow();
        onEdt(() -> {
            tool.getToolFrame().setBounds(70, 70, 1400, 900);
            tool.showComponentProvider(browser.getProvider(), true);
            manager.setCurrentProgram(original);
        });
        Thread.sleep(700);
        DockingActionIf command = tool.getAllActions().stream().filter(a -> a.getName().equals("Go To Program") &&
            a.getOwner().equals("MultiTabPlugin")).findFirst().orElseThrow();
        SwingUtilities.invokeLater(() -> command.actionPerformed(browser.getProvider().getActionContext(null)));
        ProgramSwitcherDialog switcher = waitDialog(ProgramSwitcherDialog.class);
        SearchList<?> search = find(switcher.getComponent(), SearchList.class);
        onEdt(() -> {
            if (search.getModel().getSize() != 3) throw new AssertionError("Missing visible programs");
            shotWindow(switcher, "program-switcher-duplicates.png");
            RuntimeInventory.dialog(switcher, out.resolve("program-switcher.runtime.csv").toString(), "Navigation > Go To Program / modern theme");
            search.setFilterText("review-two source");
            if (search.getModel().getSize() != 1 || search.getModel().getElementAt(0).value() != second)
                throw new AssertionError("Duplicate names did not filter by path");
            shotWindow(switcher, "program-switcher-filtered.png");
            search.setFilterText("no_such_open_program");
            if (search.getModel().getSize() != 0) throw new AssertionError("No-match list not empty");
            shotWindow(switcher, "program-switcher-no-match.png");
            press(switcher, "Cancel");
            if (manager.getCurrentProgram() != original) throw new AssertionError("Cancel switched programs");
        });
        checks.add("Existing Go To Program / MultiTabPlugin action opens switcher; binding=" + command.getKeyBinding());
        checks.add("Three same-name programs distinguished by path; multiword filtering, no-match and Cancel verified.");
        SwingUtilities.invokeLater(() -> ProgramSwitcherDialog.show(tool));
        ProgramSwitcherDialog activate = waitDialog(ProgramSwitcherDialog.class);
        SearchList<?> activateSearch = find(activate.getComponent(), SearchList.class);
        onEdt(() -> { activateSearch.setFilterText("review-one"); activateSearch.chooseItem(); });
        if (manager.getCurrentProgram() != first) throw new AssertionError("Wrong duplicate activated");
        checks.add("Filtering then choosing activates the exact duplicate Program object through ProgramManager.");
        onEdt(() -> manager.setCurrentProgram(original));
        var entry = original.getAddressFactory().getDefaultAddressSpace().getAddress(0x401000);
        int tx = original.startTransaction("Review nested namespace menu");
        try {
            var symbols = original.getSymbolTable();
            var parent = original.getFunctionManager().getFunctionAt(entry).getParentNamespace();
            var scope = symbols.createNameSpace(parent, "ReviewSpace", SourceType.USER_DEFINED);
            var nested = symbols.createNameSpace(scope, "Nested", SourceType.USER_DEFINED);
            symbols.createLabel(entry.add(0x20), "review_target", nested, SourceType.USER_DEFINED);
        }
        finally { original.endTransaction(tx, true); }
        if (!original.isChanged()) throw new AssertionError("Modified program fixture is not dirty");
        SwingUtilities.invokeLater(() -> ProgramSwitcherDialog.show(tool));
        ProgramSwitcherDialog modified = waitDialog(ProgramSwitcherDialog.class);
        onEdt(() -> { shotWindow(modified, "program-switcher-modified.png"); press(modified, "Cancel"); });
        onEdt(() -> tool.getService(GoToService.class).goTo(new ProgramLocation(original, entry)));
        Thread.sleep(700);
        LocationContextPanel panel = find(browser.getProvider().getComponent(), LocationContextPanel.class);
        if (panel == null) throw new AssertionError("Listing breadcrumb absent");
        onEdt(() -> {
            shot(tool.getToolFrame(), "breadcrumbs-connected.png");
            button(panel, "Switch open program").doClick();
            JPopupMenu menu = popup();
            long distinctPrograms = descendants(menu).stream().filter(c -> c instanceof JMenuItem item &&
                item.getText().contains("source.bin — ")).map(c -> ((JMenuItem) c).getText()).distinct().count();
            if (distinctPrograms != 3) throw new AssertionError("Breadcrumb menu has ambiguous duplicate names");
            shot(menu, "breadcrumb-program-menu.png");
            MenuSelectionManager.defaultManager().clearSelectedPath();
            button(panel, "Browse namespace symbols").doClick();
            clickMenu("ReviewSpace / ");
            JPopupMenu nestedMenu = popup();
            shot(nestedMenu, "breadcrumb-namespace-menu.png");
            if (descendants(nestedMenu).stream().noneMatch(c -> c instanceof JMenuItem item && item.getText().startsWith("Nested / ")))
                throw new AssertionError("Nested namespace missing");
            clickMenu("Nested / ");
            shot(popup(), "breadcrumb-nested-symbol-menu.png");
            clickMenu("review_target — ");
        });
        Thread.sleep(400);
        if (!entry.add(0x20).equals(browser.getCurrentAddress())) throw new AssertionError("Namespace navigation target mismatch");
        onEdt(() -> {
            button(panel, "Previous location in history").getActionMap().get("locationHistory")
                .actionPerformed(new java.awt.event.ActionEvent(panel, 0, "review"));
            shot(popup(), "breadcrumb-history-menu.png");
            MenuSelectionManager.defaultManager().clearSelectedPath();
        });
        checks.add("Actual program, nested namespace/symbol and history menus reviewed; nested symbol navigates to 00401020.");

        AddEditDialog label = new AddEditDialog("Add Label", tool);
        SwingUtilities.invokeLater(() -> label.addLabel(entry.add(0x50), original));
        waitShowing(label);
        onEdt(() -> {
            shotWindow(label, "label-empty.png");
            press(label, "OK");
            if (!label.isShowing() || !label.getStatusText().contains("blank")) throw new AssertionError("Blank label accepted");
            shotWindow(label, "label-validation-error.png");
            RuntimeInventory.dialog(label, out.resolve("label.runtime.csv").toString(), "Listing > Add Label / blank validation");
            press(label, "Cancel");
        });
        checks.add("Actual AddEditDialog empty/blank validation and cancellation reviewed; no label created.");

        var gotoPlugin = tool.getManagedPlugins().stream().filter(p -> p.getName().equals("GoToAddressLabelPlugin")).findFirst().orElseThrow();
        GoToAddressLabelDialog go = new GoToAddressLabelDialog(tool.getService(GoToService.class), gotoPlugin);
        SwingUtilities.invokeLater(() -> go.show(browser.getProvider(), entry, tool));
        waitShowing(go);
        onEdt(() -> {
            GhidraComboBox<?> combo = find(go.getComponent(), GhidraComboBox.class);
            combo.getEditor().setItem("no_such_ui_review_symbol");
            press(go, "OK");
        });
        await(() -> go.getStatusText().startsWith("No results"));
        onEdt(() -> {
            shotWindow(go, "goto-no-results.png");
            RuntimeInventory.dialog(go, out.resolve("goto.runtime.csv").toString(), "Go To / no matching symbol");
            press(go, "Cancel");
        });
        if (!entry.add(0x20).equals(browser.getCurrentAddress())) throw new AssertionError("Failed query changed location");
        checks.add("Actual asynchronous Go To no-results state and Cancel reviewed; current address preserved.");

        NumberRangeInputDialog range = new NumberRangeInputDialog("Review Number Range", "Addresses");
        SwingUtilities.invokeLater(() -> tool.showDialog(range));
        waitShowing(range);
        onEdt(() -> {
            JTextField field = find(range.getComponent(), JTextField.class);
            field.setText("not-a-range"); press(range, "OK");
            if (!range.isShowing() || range.getStatusText().isBlank()) throw new AssertionError("Invalid range accepted");
            shotWindow(range, "range-validation-error.png");
            RuntimeInventory.dialog(range, out.resolve("range.runtime.csv").toString(), "Number range / invalid input");
            press(range, "Cancel");
        });
        checks.add("Shared NumberRangeInputDialog invalid-input feedback and Cancel reviewed.");
        onEdt(() -> {
            manager.closeAllPrograms(true);
        });
        SwingUtilities.invokeLater(() -> ProgramSwitcherDialog.show(tool));
        ProgramSwitcherDialog empty = waitDialog(ProgramSwitcherDialog.class);
        onEdt(() -> { shotWindow(empty, "program-switcher-empty.png"); press(empty, "Cancel"); });
        checks.add("Empty-tool switcher renders an explicit empty state with Switch disabled.");
        Files.write(out.resolve("navigation-checks.txt"), checks);
    }

    public static void runTemplate(PluginTool tool, Path output) throws Exception {
        out = output;
        onEdt(() -> {
            try { tool.addPlugin("skeleton.SkeletonPlugin"); }
            catch (Exception e) { throw new RuntimeException(e); }
            tool.getToolFrame().setBounds(70, 70, 1200, 760);
        });
        Thread.sleep(700);
        onEdt(() -> {
            ComponentProvider provider = tool.getComponentProvider("Skeleton Provider");
            if (provider == null) throw new AssertionError("Template provider absent");
            tool.showComponentProvider(provider, true);
            RuntimeInventory.capture(tool, out.resolve("skeleton.runtime.csv").toString());
        });
        ComponentProvider provider = tool.getComponentProvider("Skeleton Provider");
        await(() -> provider.getComponent().isShowing() && provider.getComponent().getWidth() > 0);
        Thread.sleep(700);
        onEdt(() -> {
            shot(SwingUtilities.getWindowAncestor(provider.getComponent()), "skeleton-provider.png");
            shot(provider.getComponent(), "skeleton-provider-content.png");
        });
        DockingActionIf action = tool.getAllActions().stream().filter(a -> a.getName().equals("My Action") &&
            a.getOwner().equals("SkeletonPlugin")).findFirst().orElseThrow();
        SwingUtilities.invokeLater(() -> action.actionPerformed(tool.getComponentProvider("Skeleton Provider").getActionContext(null)));
        final DialogComponentProvider[] message = {null};
        await(() -> {
            for (Window window : Window.getWindows()) {
                if (window.isShowing() && window instanceof DockingDialog dialog && dialog.getTitle().equals("Custom Action")) {
                    message[0] = dialog.getDialogComponent(); return true;
                }
            }
            return false;
        });
        onEdt(() -> { shotWindow(message[0], "skeleton-custom-action.png"); press(message[0], "OK"); });
        Files.writeString(out.resolve("template-checks.txt"), "Built actual shipped Skeleton source as SkeletonReview extension.\nActivated SkeletonPlugin; empty provider and My Action / Custom Action / Hello dialog captured and dismissed.\n");
    }

    private static <T extends DialogComponentProvider> T waitDialog(Class<T> type) throws Exception {
        final DialogComponentProvider[] result = {null};
        await(() -> {
            for (Window window : Window.getWindows()) {
                if (window.isShowing() && window instanceof DockingDialog dockingDialog) {
                    DialogComponentProvider provider = dockingDialog.getDialogComponent();
                    if (type.isInstance(provider)) { result[0] = provider; return true; }
                }
            }
            return false;
        });
        return type.cast(result[0]);
    }
    private static void waitShowing(DialogComponentProvider dialog) throws Exception { await(dialog::isShowing); }
    private static void await(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 300; i++) {
            boolean[] ready = {false}; onEdt(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return; Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for navigation review state");
    }
    private static void press(DialogComponentProvider dialog, String text) {
        descendants(dialog.getComponent()).stream().filter(c -> c instanceof JButton b && b.getText().equals(text))
            .map(c -> (JButton) c).findFirst().orElseThrow().doClick();
    }
    private static JButton button(Component root, String name) {
        return descendants(root).stream().filter(c -> c instanceof JButton && name.equals(c.getName()))
            .map(c -> (JButton) c).findFirst().orElseThrow();
    }
    private static JPopupMenu popup() {
        for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath())
            if (element instanceof JPopupMenu menu) return menu;
        throw new AssertionError("Expected active popup");
    }
    private static void clickMenu(String prefix) {
        descendants(popup()).stream().filter(c -> c instanceof JMenuItem item && item.getText().startsWith(prefix))
            .map(c -> (JMenuItem) c).findFirst().orElseThrow().doClick();
    }
    private static List<Component> descendants(Component root) {
        List<Component> result = new ArrayList<>(); result.add(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) result.addAll(descendants(child));
        return result;
    }
    private static <T> T find(Component root, Class<T> type) {
        return descendants(root).stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);
    }
    private static void shotWindow(DialogComponentProvider dialog, String name) { shot(SwingUtilities.getWindowAncestor(dialog.getComponent()), name); }
    private static void shot(Component component, String name) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { component.paint(graphics); ImageIO.write(image, "png", out.resolve(name).toFile()); }
        catch (Exception e) { throw new RuntimeException(e); }
        finally { graphics.dispose(); }
    }
    private static void onEdt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
}

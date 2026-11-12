// Compiled into a disposable COPY of the UI Refresh harness, never into product jars.
package uirefresh;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;

import docking.*;
import docking.action.*;
import ghidra.framework.main.AppInfo;
import ghidra.framework.plugintool.*;

public final class RuntimeInventory {
    private RuntimeInventory() { }

    private static String csv(Object... fields) {
        return Arrays.stream(fields).map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"")
            .collect(Collectors.joining(","));
    }

    public static void capture(PluginTool tool, String path) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Capture on EDT");
        List<String> rows = new ArrayList<>();
        rows.add("kind,class,name,entry_path,owner");
        for (Plugin plugin : tool.getManagedPlugins()) {
            rows.add(csv("plugin", plugin.getClass().getName(), plugin.getName(),
                "Loaded in " + tool.getName(), plugin.getName()));
        }
        for (ComponentProvider provider : DockingWindowManager.getInstance(tool.getToolFrame())
                .getComponentProviders(ComponentProvider.class)) {
            rows.add(csv("provider", provider.getClass().getName(), provider.getName(),
                tool.getName() + " / Window / " + provider.getName() +
                " (registered; visible=" + provider.isVisible() + ")", provider.getOwner()));
        }
        for (DockingActionIf action : tool.getAllActions()) {
            List<String> routes = new ArrayList<>();
            MenuData menu = action.getMenuBarData();
            MenuData popup = action.getPopupMenuData();
            ToolBarData toolbar = action.getToolBarData();
            if (menu != null) routes.add("Menu " + Arrays.toString(menu.getMenuPath()));
            if (popup != null) routes.add("Popup " + Arrays.toString(popup.getMenuPath()));
            if (toolbar != null) routes.add("Toolbar group=" + toolbar.getToolBarGroup());
            if (routes.isEmpty()) routes.add("Local/action chooser; context required");
            KeyBindingData binding = action.getKeyBindingData();
            if (binding != null && binding.getKeyBinding() != null) {
                routes.add("Shortcut " + binding.getKeyBinding());
            }
            rows.add(csv("action", action.getClass().getName(), action.getName(),
                String.join(" | ", routes), action.getOwner()));
        }
        write(path, rows);
    }

    public static void dialog(DialogComponentProvider dialog, String path, String route) {
        write(path, new ArrayList<>(List.of("kind,class,name,entry_path,owner",
            csv("dialog", dialog.getClass().getName(), dialog.getTitle(), route, "Docking"))));
    }

    private static void write(String path, List<String> rows) {
        rows.subList(1, rows.size()).sort(String::compareTo);
        try { Files.write(Paths.get(path), rows); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    // Only default shipped templates are instantiated; this never enables all optional
    // plugins or invokes arbitrary actions. Captures are registration evidence.
    public static void specialists(PluginTool browser, String out) throws Exception {
        List<String> modules = new ArrayList<>();
        modules.add("module,runtime_root,manifest_sha256");
        for (var root : ghidra.framework.Application.getModuleRootDirectories()) {
            String path = root.getAbsolutePath().replace('\\', '/');
            int marker = path.lastIndexOf("/Ghidra/");
            String relative = marker >= 0 ? path.substring(marker + 1) : "<external>/" + root.getName();
            Path manifest = Paths.get(root.getAbsolutePath(), "Module.manifest");
            String hash = Files.exists(manifest) ? HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(manifest))) : "";
            modules.add(csv(root.getName(), relative, hash));
        }
        write(out + ".modules.csv", modules);
        capture(AppInfo.getFrontEndTool(), out + ".frontend.runtime.csv");
        for (String name : List.of("Debugger", "VersionTracking")) {
            PluginTool view = ghidra.framework.ToolUtils.readToolTemplate(
                "defaultTools/" + name + ".tool").createTool(browser.getProject());
            try {
                view.setVisible(true);
                capture(view, out + "." + name + ".runtime.csv");
            }
            finally {
                view.setConfigChanged(false);
                view.close();
            }
        }
    }
}

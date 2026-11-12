// Compiled into a disposable COPY of the UI Refresh harness, never into product jars.
package uirefresh;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import org.objectweb.asm.*;

import docking.*;
import docking.action.*;
import ghidra.framework.main.AppInfo;
import ghidra.framework.plugintool.*;

public final class RuntimeInventory {
    private static final String HEADER = "kind,class,name,entry_path,owner,source_file,source_line,enclosing_method,callback_host,callback_method,callback_line";
    private static final Map<Class<?>, String[]> origins = new HashMap<>();
    private RuntimeInventory() { }

    private static String csv(Object... fields) {
        return Arrays.stream(fields).map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"")
            .collect(Collectors.joining(","));
    }

    public static void capture(PluginTool tool, String path) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Capture on EDT");
        List<String> rows = new ArrayList<>();
        rows.add(HEADER);
        for (Plugin plugin : tool.getManagedPlugins()) {
            rows.add(registration("plugin", plugin, plugin.getName(),
                "Loaded in " + tool.getName(), plugin.getName()));
        }
        for (ComponentProvider provider : DockingWindowManager.getInstance(tool.getToolFrame())
                .getComponentProviders(ComponentProvider.class)) {
            rows.add(registration("provider", provider, provider.getName(),
                tool.getName() + " / Window / " + provider.getName() +
                " (registered; visible=" + provider.isVisible() + ")", provider.getOwner()));
            if (Boolean.getBoolean("uirefresh.inventory.components")) {
                components(provider.getComponent(), provider.getName(), provider.getOwner(), rows, new HashSet<>());
            }
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
            rows.add(registration("action", action, action.getName(),
                String.join(" | ", routes), action.getOwner()));
        }
        write(path, rows);
    }

    private static void components(java.awt.Component component, String provider, String owner,
            List<String> rows, Set<String> recorded) {
        if (recorded.add("component:" + component.getClass().getName()))
            rows.add(registration("component", component, component.getClass().getSimpleName(),
                provider + " / constructed component hierarchy", owner));
        List<Object> renderers = new ArrayList<>();
        if (component instanceof javax.swing.JTable table) {
            for (int i = 0; i < table.getColumnCount(); i++) {
                var renderer = table.getColumnModel().getColumn(i).getCellRenderer();
                renderers.add(renderer == null ? table.getDefaultRenderer(table.getColumnClass(i)) : renderer);
            }
            if (table.getTableHeader() != null) renderers.add(table.getTableHeader().getDefaultRenderer());
        }
        if (component instanceof javax.swing.JTree tree) renderers.add(tree.getCellRenderer());
        if (component instanceof javax.swing.JList<?> list) renderers.add(list.getCellRenderer());
        for (Object renderer : renderers) {
            if (renderer != null && recorded.add("renderer:" + renderer.getClass().getName()))
                rows.add(registration("renderer", renderer, renderer.getClass().getSimpleName(),
                    provider + " / installed table/tree/list renderer", owner));
        }
        if (component instanceof java.awt.Container parent)
            for (java.awt.Component child : parent.getComponents()) components(child, provider, owner, rows, recorded);
    }

    public static void dialog(DialogComponentProvider dialog, String path, String route) {
        write(path, new ArrayList<>(List.of(HEADER,
            registration("dialog", dialog, dialog.getTitle(), route, "Docking"))));
    }

    private static String registration(String kind, Object object, String name, String route, String owner) {
        String[] origin = origins.computeIfAbsent(object.getClass(), RuntimeInventory::origin);
        Class<?> callback = callbackHost(object, 0);
        String[] target = callback == null ? new String[] {"", "", ""} : lambdaTarget(callback);
        return csv(kind, object.getClass().getName(), name, route, owner,
            origin[0], origin[1], origin[2], callback == null ? "" :
                target[0].isEmpty() ? callback.getNestHost().getName() : target[0], target[1], target[2]);
    }

    private static String[] lambdaTarget(Class<?> callback) {
        String[] target = {"", "", ""};
        if (!callback.isHidden()) return target;
        String name = callback.getName().replace('.', '/').replace("/0x", ".0x") + ".class";
        Path path = Paths.get(System.getProperty("user.dir"), "DUMP_LAMBDA_PROXY_CLASS_FILES", name);
        if (!Files.isRegularFile(path)) return target;
        try {
            new ClassReader(Files.readAllBytes(path)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                    if (!(method.equals("accept") || method.equals("run"))) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (owner.startsWith("java/") || name.equals("<init>")) return;
                            target[0] = owner.replace('/', '.');
                            target[1] = name + descriptor;
                            try {
                                Class<?> host = Class.forName(target[0], false, callback.getClassLoader());
                                try (var stream = host.getResourceAsStream("/" + owner + ".class")) {
                                    if (stream == null) return;
                                    new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                                        @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                                            if (!n.equals(name) || !d.equals(descriptor)) return null;
                                            return new MethodVisitor(Opcodes.ASM9) {
                                                @Override public void visitLineNumber(int line, Label label) {
                                                    if (target[2].isEmpty()) target[2] = Integer.toString(line);
                                                }
                                            };
                                        }
                                    }, ClassReader.SKIP_FRAMES);
                                }
                            }
                            catch (Exception ignored) { }
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        }
        catch (Exception ignored) { }
        return target;
    }

    // Read debug metadata from the class actually loaded in this runtime, including anonymous
    // action bodies. No actions are invoked to discover their source or callback provenance.
    private static String[] origin(Class<?> type) {
        String[] result = {"", "0", ""};
        int[] handlerLine = {Integer.MAX_VALUE};
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (var stream = type.getResourceAsStream(resource)) {
            if (stream == null) return result;
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public void visitSource(String source, String debug) { result[0] = source == null ? "" : source; }
                @Override public void visitOuterClass(String owner, String name, String descriptor) {
                    result[2] = owner.replace('/', '.') + "." + name;
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("actionPerformed") && !name.equals("<init>")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitLineNumber(int line, Label start) {
                            if (name.equals("actionPerformed")) handlerLine[0] = Math.min(handlerLine[0], line);
                            int previous = Integer.parseInt(result[1]);
                            if (previous == 0 || line < previous) result[1] = Integer.toString(line);
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
            if (handlerLine[0] != Integer.MAX_VALUE) result[1] = Integer.toString(handlerLine[0]);
        }
        catch (Exception e) { result[2] = "metadata unavailable: " + e.getClass().getSimpleName(); }
        return result;
    }

    private static Class<?> callbackHost(Object object, int depth) {
        if (object == null || depth > 3) return null;
        for (Class<?> type = object.getClass(); type != null && !type.getName().startsWith("java."); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (!(field.getName().equals("actionCallback") || field.getName().equals("callback") || field.getName().equals("this$0"))) continue;
                try {
                    if (!field.trySetAccessible()) continue;
                    Object value = field.get(object);
                    if (value instanceof Consumer<?> || value instanceof Runnable) return value.getClass();
                    if (field.getName().equals("this$0")) {
                        Class<?> host = callbackHost(value, depth + 1);
                        if (host != null) return host;
                    }
                }
                catch (IllegalAccessException ignored) { }
            }
        }
        return null;
    }

    private static void write(String path, List<String> rows) {
        rows.subList(1, rows.size()).sort(String::compareTo);
        try { Files.write(Paths.get(path), rows); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    // Only default shipped templates are instantiated; this never enables all optional
    // plugins or invokes arbitrary actions. Captures are registration evidence.
    public static void specialists(PluginTool browser, String out) throws Exception {
        if (Boolean.getBoolean("uirefresh.inventory.optional")) optionalPlugins(browser, out);
        List<String> modules = new ArrayList<>();
        modules.add("module,runtime_root,manifest_sha256");
        for (var root : ghidra.framework.Application.getModuleRootDirectories()) {
            String path = root.getAbsolutePath().replace('\\', '/');
            int marker = path.lastIndexOf("/Ghidra/");
            Set<String> firstPartyExtensions = Set.of("Jython", "Lisa", "MachineLearning", "sample",
                "SampleTablePlugin", "SleighDevTools", "SymbolicSummaryZ3");
            String relative = marker >= 0 ? path.substring(marker + 1) : firstPartyExtensions.contains(root.getName()) ?
                "Ghidra/Extensions/" + root.getName() : "<external>/" + root.getName();
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

    private static void optionalPlugins(PluginTool browser, String out) {
        Set<String> optional = Set.of("Jython", "Lisa", "MachineLearning", "SampleTablePlugin",
            "sample", "SleighDevTools", "SymbolicSummaryZ3");
        List<String> availability = new ArrayList<>(List.of("module,class,status,loaded,required_services"));
        List<String> activation = new ArrayList<>(List.of("module,class,result"));
        for (Class<? extends Plugin> type : ghidra.util.classfinder.ClassSearcher.getClasses(Plugin.class)) {
            var description = ghidra.framework.plugintool.util.PluginDescription.getPluginDescription(type);
            String module = description.getModuleName();
            availability.add(csv(module, type.getName(), description.getStatus(),
                browser.getManagedPlugins().stream().anyMatch(plugin -> plugin.getClass() == type),
                description.getServicesRequired().toString()));
            if (!optional.contains(module)) continue;
            try {
                if (browser.getManagedPlugins().stream().noneMatch(plugin -> plugin.getClass() == type)) browser.addPlugin(type.getName());
                activation.add(csv(module, type.getName(), "loaded"));
                capture(browser, out + ".optional." + module + ".runtime.csv");
            }
            catch (Exception | LinkageError e) {
                activation.add(csv(module, type.getName(), "failed: " + e));
            }
        }
        write(out + ".available-plugins.csv", availability);
        write(out + ".optional-activation.csv", activation);
    }
}

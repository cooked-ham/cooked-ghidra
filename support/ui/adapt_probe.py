"""Adapt a COPY of the existing UI Refresh test probe for meaningful reference captures.

The original extension source and installed seed profile are never edited.
"""

import hashlib
import json
from pathlib import Path
import sys


def main():
    original, destination = map(Path, sys.argv[1:])
    source = original.read_text(encoding="utf-8")
    original_hash = hashlib.sha256(source.encode()).hexdigest()
    replacements = {
        "private void runThemeProbe(String out) {\n\t\ttry {\n\t\t\tThread.sleep(12000);": """private void runThemeProbe(String out) {
        try {
            Thread.sleep(12000);
            onEdt(() -> {
                tool.showComponentProvider(docked, false);
                tool.showComponentProvider(floating, false);
                recordRuntime(out);
            });""",
        'dialog[0] = new docking.options.editor.OptionsDialog("Tool Options", "Options", tool.getOptions(), null);':
        'dialog[0] = new docking.options.editor.OptionsDialog("Tool Options", "Options", tool.getOptions(), null);\n'
        '            ((docking.options.editor.OptionsDialog) dialog[0]).displayCategory("Navigation", null);',
        'new String[] { "Bytes", "Function Graph" }': 'new String[] { "Bytes", "Symbol Table", "Function Graph" }',
        'private void runThemeProbe(String out) {': r'''
    private String csv(Object... values) {
        return java.util.Arrays.stream(values).map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"")
            .collect(java.util.stream.Collectors.joining(","));
    }

    private void recordRuntime(String out) {
        List<String> rows = new ArrayList<>();
        rows.add("kind,class,name,entry_path,owner");
        for (Plugin p : tool.getManagedPlugins()) {
            rows.add(csv("plugin", p.getClass().getName(), p.getName(), "Loaded in CodeBrowser", p.getName()));
        }
        for (ComponentProvider p : DockingWindowManager.getInstance(tool.getToolFrame()).getComponentProviders(ComponentProvider.class)) {
            rows.add(csv("provider", p.getClass().getName(), p.getName(), "Window / " + p.getName(), p.getOwner()));
        }
        for (docking.action.DockingActionIf a : tool.getAllActions()) {
            MenuData menu = a.getMenuBarData();
            MenuData popup = a.getPopupMenuData();
            String path = menu != null ? Arrays.toString(menu.getMenuPath()) :
                popup != null ? "Popup " + Arrays.toString(popup.getMenuPath()) : "Toolbar/local/action chooser";
            rows.add(csv("action", a.getClass().getName(), a.getName(), path, a.getOwner()));
        }
        rows.subList(1, rows.size()).sort(String::compareTo);
        try {
            Files.write(Paths.get(out + ".runtime.csv"), rows);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private void runThemeProbe(String out) {''',
    }
    for before, after in replacements.items():
        if source.count(before) != 1:
            raise SystemExit("Probe source changed; review adapter anchor: " + before)
        source = source.replace(before, after)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(source, encoding="utf-8")
    destination.with_suffix(".provenance.json").write_text(json.dumps({
        "original_source": str(original), "original_sha256": original_hash,
        "adapted_sha256": hashlib.sha256(source.encode()).hexdigest(),
        "changes": ["hide synthetic harness panes", "select Navigation options form",
                    "capture actual Symbol Table", "record loaded plugins/providers/actions"]
    }, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

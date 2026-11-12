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
                RuntimeInventory.capture(tool, out + ".codebrowser.runtime.csv");
                if (Boolean.getBoolean("uirefresh.inventory.specialists")) {
                    RuntimeInventory.specialists(tool, out);
                }
            });""",
        'dialog[0] = new docking.options.editor.OptionsDialog("Tool Options", "Options", tool.getOptions(), null);':
        'dialog[0] = new docking.options.editor.OptionsDialog("Tool Options", "Options", tool.getOptions(), null);\n'
        '            ((docking.options.editor.OptionsDialog) dialog[0]).displayCategory("Navigation", null);',
        'new String[] { "Bytes", "Function Graph" }': 'new String[] { "Bytes", "Symbol Table", "Function Graph" }',
        'if (want.equals("(polish)")) {': '''if (want.equals("(graph)")) {
                    GraphReview.run(tool, out, lines);
                    i++;
                    continue;
                }
                if (want.equals("(polish)")) {''',
        'saveComponent(SwingUtilities.getWindowAncestor(dialog[0].getComponent()), out + "." + theme + ".options.png");':
        'RuntimeInventory.dialog(dialog[0], out + ".options.runtime.csv", "CodeBrowser / Edit / Tool Options / Navigation");\n'
        '\t\t\tsaveComponent(SwingUtilities.getWindowAncestor(dialog[0].getComponent()), out + "." + theme + ".options.png");',
        'saveComponent(SwingUtilities.getWindowAncestor(dialog[0].getComponent()), out + "." + theme + ".chooser.png");':
        'RuntimeInventory.dialog(dialog[0], out + ".chooser.runtime.csv", "CodeBrowser / Action chooser");\n'
        '\t\t\tsaveComponent(SwingUtilities.getWindowAncestor(dialog[0].getComponent()), out + "." + theme + ".chooser.png");',
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
                    "capture actual Symbol Table", "record plugins/providers/all action routes",
                    "capture real options/chooser dialog classes", "optional default specialist tool registrations",
                    "optional populated graph grouping/ungrouping and theme-switch review"]
    }, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

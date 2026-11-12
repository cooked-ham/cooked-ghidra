"""Adapt a COPY of the UI Refresh harness for populated specialist captures.

The harness runs in the saved CodeBrowser tool that the original Front End launcher opens for
`uirefresh.autolaunch=true`. It parks that tool off-screen immediately and starts one specialist
surface. The original extension source and installed seed profile are never edited.
"""

import hashlib
import json
from pathlib import Path
import sys


ANCHOR = "\t\ttool.addComponentProvider(floating, true);\n"
INSERT = ANCHOR + '''\t\tString specialistOut = System.getProperty("uirefresh.specialists.out");
\t\tif (specialistOut != null) {
\t\t\tSwingUtilities.invokeLater(() -> tool.getToolFrame().setLocation(-20000, 0));
\t\t\tgeneric.theme.ThemeManager themeManager = generic.theme.ThemeManager.getInstance();
\t\t\tthemeManager.getAllThemes().stream()
\t\t\t\t.filter(theme -> theme.getName().equals("Graphite Preview"))
\t\t\t\t.findFirst().ifPresent(themeManager::setTheme);
\t\t\tThread specialistThread = new Thread(() -> SpecialistReview.run(tool, specialistOut),
\t\t\t\t"uirefresh-specialists");
\t\t\tspecialistThread.setDaemon(true);
\t\t\tspecialistThread.start();
\t\t}
'''


def replace_once(source, before, after, label):
    if source.count(before) != 1:
        raise SystemExit("Harness source changed; review adapter anchor: " + label)
    return source.replace(before, after)


def main():
    harness_source, destination_dir = map(Path, sys.argv[1:])
    harness = harness_source.read_text(encoding="utf-8")
    original_hash = hashlib.sha256(harness.encode()).hexdigest()
    harness = replace_once(harness, ANCHOR, INSERT, "start one specialist surface")
    destination = destination_dir / "uirefresh" / "UiRefreshHarnessPlugin.java"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(harness, encoding="utf-8")
    (destination_dir / "adaptation.provenance.json").write_text(json.dumps({
        "original": {"harness_sha256": original_hash},
        "adapted": {"harness_sha256": hashlib.sha256(harness.encode()).hexdigest()},
        "changes": [
            "park the saved CodeBrowser off-screen before it is shown",
            "activate Graphite Preview before the specialist tool is created",
            "start one specialist surface per process",
        ],
    }, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

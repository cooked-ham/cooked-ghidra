"""Source-backed discovery ledger for first-party UI fidelity work.

This deliberately over-includes candidates. Source discovery is not runtime acceptance.
Run from any directory; only repository files (including new, non-ignored files) are read.
"""

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/ui-fidelity"
FIELDS = ["id", "module", "kind", "surface", "source", "line", "source_hash",
          "entry_path", "fixture", "rendering_route", "required_states", "status",
          "screenshots", "interaction_checks", "scaling_accessibility", "performance",
          "blockers"]
STATUSES = {"inventoried", "designed", "implemented", "visually reviewed",
            "functionally verified", "accepted"}
# Strip comments and strings while retaining character offsets and line numbers.
LEXEMES = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
TYPES = re.compile(r'(?<![.\w])\b(class|interface|enum|record)\s+(\w+)([^;{}]*)(?:\{|;)')
BASES = {
    "provider": {"ComponentProvider", "ComponentProviderAdapter"},
    "dialog": {"DialogComponentProvider", "JDialog", "Dialog", "JOptionPane", "Wizard"},
    "window": {"JFrame", "JWindow", "Frame", "Window"},
    "renderer": {"TableCellRenderer", "TreeCellRenderer", "ListCellRenderer"},
    "editor": {"TableCellEditor", "TreeCellEditor", "PropertyEditorSupport"},
    "component": {"JComponent", "JPanel", "JTable", "JTree", "JList", "JButton",
                  "JLabel", "JTextField", "JTextArea", "JScrollPane", "JTabbedPane",
                  "JToolBar", "JMenu", "JMenuItem", "JCheckBox", "JComboBox",
                  "JTextPane", "JEditorPane", "JToggleButton", "JRadioButton",
                  "WizardPage", "PreferencePage", "FieldEditorPreferencePage", "ViewPart", "EditorPart"},
    "plugin": {"Plugin", "ProgramPlugin"},
    "action": {"DockingAction", "ToggleDockingAction", "AbstractAction"},
    "icon": {"Icon", "ImageIcon"},
    "theme": {"DiscoverableGTheme", "GTheme"},
    "script-workflow": {"GhidraScript"},
}
CALLS = re.compile(r'\b(?:new\s+(MenuData|ActionBuilder|ToggleActionBuilder|HelpLocation)|'
                   r'(setMenuBarData|setPopupMenuData|setToolBarData|showDialog|showComponentProvider)\s*\()')
UI_HINT = re.compile(r'\b(?:javax\.swing|java\.awt|docking\.|org\.eclipse\.swt|org\.eclipse\.jface|paintComponent|paintIcon|'
                     r'getTableCellRendererComponent|getTreeCellRendererComponent|'
                     r'getListCellRendererComponent|new\s+HelpLocation)\b')


def masked(text):
    return LEXEMES.sub(lambda m: re.sub(r'[^\n]', ' ', m.group()), text)


def digest(text):
    return hashlib.sha256(text.encode()).hexdigest()


def tracked_files():
    data = subprocess.check_output(["git", "ls-files", "-z", "--cached", "--others",
                                    "--exclude-standard", "--", "Ghidra", "GhidraBuild"], cwd=ROOT)
    return sorted(set(p.decode("utf-8") for p in data.split(b"\0") if p))


def fixture(module):
    if any(x in module for x in ("Debugger", "TraceModeling", "TaintAnalysis", "SystemEmulation")):
        return "FX-DEBUG"
    if "BSim" in module:
        return "FX-BSIM"
    if "VersionTracking" in module:
        return "FX-VT"
    if any(x in module for x in ("ProgramDiff", "CodeCompare")):
        return "FX-COMPARE"
    if "FileSystem" in module or "FileFormats" in module:
        return "FX-FORMATS"
    if "GhidraServer" in module:
        return "FX-REPO"
    if "/Extensions/" in module or "EclipsePlugins" in module:
        return "FX-OPTIONAL"
    return "FX-CORE"


def route(kind, body):
    if re.search(r'\b(?:org\.eclipse\.swt|org\.eclipse\.jface)\b', body):
        return "SWT/JFace; optional IDE integration"
    if re.search(r'\b(?:FileDialog|Desktop\.getDesktop)\b', body):
        return "native integration; inspect fallback"
    if kind in {"renderer", "icon"} or re.search(
            r'\b(?:paintComponent|paintIcon|paintBorder|getTableCellRendererComponent|'
            r'getTreeCellRendererComponent|getListCellRendererComponent)\s*\(', body):
        return "custom; individual review"
    if kind in {"provider", "dialog", "component", "editor", "window"}:
        return "Swing/shared + custom-content review"
    return "discovery; trace runtime owner"


def write_csv(path, rows, fields):
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def discover(files):
    modules = sorted({str(Path(p).parent).replace("\\", "/") for p in files
                      if p.endswith(("/Module.manifest", "/build.gradle"))})
    # Longest-prefix match also handles nested first-party modules.
    def owner(path):
        return next((m for m in sorted(modules, key=len, reverse=True)
                     if path.startswith(m + "/")), "GhidraBuild/unmapped")

    sources = {}
    declarations = defaultdict(list)
    for path in files:
        if not path.endswith(".java") or not any(x in path for x in ("/src/main/", "/ghidra_scripts/")):
            continue
        text = (ROOT / path).read_text(encoding="utf-8", errors="replace")
        clean = masked(text)
        types = []
        brace_ends = {}
        braces = []
        for pos, char in enumerate(clean):
            if char == "{":
                braces.append(pos)
            elif char == "}" and braces:
                brace_ends[braces.pop()] = pos
        scopes = []
        occurrences = Counter()
        for match in TYPES.finditer(clean):
            name = match[2]
            while scopes and match.start() > scopes[-1][0]:
                scopes.pop()
            qualified = (scopes[-1][1] + "." if scopes else "") + name
            occurrences[qualified] += 1
            identity = qualified + (" #" + str(occurrences[qualified]) if occurrences[qualified] > 1 else "")
            if clean[match.end() - 1] == "{":
                scopes.append((brace_ends.get(match.end() - 1, len(clean)), qualified))
            parents = re.findall(r'\b[A-Za-z_]\w*\b', match[3])
            parents = [p for p in parents if p not in {"extends", "implements", "public", "private"}]
            item = (name, identity, parents, match.start())
            types.append(item)
            declarations[name].extend(parents)
        sources[path] = (text, clean, types)

    # Union on ambiguous simple names intentionally errs toward candidate discovery.
    categories = defaultdict(set)
    for kind, bases in BASES.items():
        for base in bases:
            categories[base].add(kind)
    changed = True
    while changed:
        changed = False
        for name, parents in declarations.items():
            inherited = set().union(*(categories[p] for p in parents))
            before = len(categories[name])
            categories[name].update(inherited)
            changed |= len(categories[name]) != before

    rows = []

    def add(path, kind, name, line, text, rendering=None, entry=None):
        module = owner(path)
        states = "active;inactive;hover;pressed;focus;selected;disabled;empty;loading;error;warning;long-text"
        rows.append(dict(zip(FIELDS, [
            "UI-" + digest(path + "|" + kind + "|" + name)[:14], module, kind, name, path,
            line, digest(text), entry or "Source candidate; confirm shipped menu/provider/help entry",
            fixture(module), rendering or route(kind, text), states, "inventoried", "", "",
            "100/125/150/200% physical DPI; keyboard; contrast; screen reader: unverified",
            "Measure scrolling/filter/repaint if applicable",
            "Reachability, fixture readiness, runtime states and acceptance evidence unreviewed"
        ])))

    for path, (text, clean, types) in sources.items():
        found = False
        for name, identity, parents, pos in types:
            kinds = categories[name]
            # Keep the most specific kind; all nested declarations are considered.
            kind = next((k for k in BASES if k in kinds), None)
            if kind:
                add(path, kind, identity, clean.count("\n", 0, pos) + 1, text)
                found = True
        if UI_HINT.search(text) and not found:
            add(path, "ui-helper", Path(path).stem, 1, text)
        occurrences = Counter()
        for match in CALLS.finditer(clean):
            line_end = text.find("\n", match.start())
            excerpt = text[match.start():line_end if line_end >= 0 else len(text)].strip()[:220]
            key = re.sub(r'\s+', ' ', excerpt)
            occurrences[key] += 1
            # Line numbers are evidence, not identity: inserting lines retains stable IDs.
            label = key + " #" + str(occurrences[key])
            add(path, "entry-hook", label, clean.count("\n", 0, match.start()) + 1, text,
                "entry/action/help discovery", excerpt)

    for path in files:
        kind = None
        if "/src/main/help/" in path and Path(path).suffix.lower() in {".htm", ".html"}:
            kind = "help-workflow"
        elif ("/src/main/resources/" in path or "/data/images/" in path) and Path(path).suffix.lower() in {".png", ".gif", ".svg", ".jpg", ".ico"}:
            kind = "artwork"
        elif "/ghidra_scripts/" in path and path.endswith(".py"):
            kind = "script-workflow"
        elif path.endswith(".tool") or "/data/debugger-launchers/" in path:
            kind = "tool-or-launcher"
        elif path.endswith(".theme.properties"):
            kind = "theme-mappings"
        if kind:
            raw = (ROOT / path).read_bytes()
            add(path, kind, Path(path).name, 1, raw.hex(),
                "resource; trace consumers and semantic meaning", "Resource: " + path)
    return modules, rows, len(sources)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check generated ledgers are current")
    args = parser.parse_args()
    files = tracked_files()
    modules, rows, source_count = discover(files)
    OUT.mkdir(parents=True, exist_ok=True)
    review_path = OUT / "reviews.csv"
    reviews = {}
    if review_path.exists():
        with review_path.open(newline="", encoding="utf-8") as stream:
            reviews = {r["id"]: r for r in csv.DictReader(stream)}
    for row in rows:
        review = reviews.get(row["id"])
        if review:
            if review.get("source_hash") != row["source_hash"]:
                row["blockers"] = "Source changed since review; evidence must be revalidated"
                continue
            if review.get("status") not in STATUSES:
                raise ValueError("Invalid review status: " + row["id"])
            for key in FIELDS:
                if key not in {"id", "source", "line", "source_hash", "module", "kind", "surface"} and (review.get(key) or key == "blockers"):
                    row[key] = review[key]
            if row["status"] == "accepted" and (row["blockers"] or not all(row[k] for k in
                    ("screenshots", "interaction_checks", "scaling_accessibility"))):
                raise ValueError("Accepted entry lacks evidence or has blockers: " + row["id"])
    rows.sort(key=lambda r: (r["module"], r["source"], int(r["line"]), r["kind"], r["surface"]))
    if len({r["id"] for r in rows}) != len(rows):
        raise ValueError("Duplicate stable inventory IDs")
    counts = Counter(r["module"] for r in rows)
    module_rows = [{"module": m, "candidates": counts[m], "fixture": fixture(m),
                    "shipping": "Confirm release configuration and optional installation",
                    "blockers": "Runtime plugin/menu/provider reconciliation outstanding"}
                   for m in modules]
    summary = {
        "schema": 1, "java_sources_scanned": source_count, "modules": len(modules),
        "candidates": len(rows), "kinds": dict(sorted(Counter(r["kind"] for r in rows).items())),
        "statuses": dict(sorted(Counter(r["status"] for r in rows).items())),
        "orphan_reviews": sorted(set(reviews) - {r["id"] for r in rows}),
        "unmapped_candidates": counts["GhidraBuild/unmapped"],
        "runtime_reconciled": False,
        "release_blocker": "Source candidates require runtime reconciliation; inventory is not exhaustive acceptance."
    }
    outputs = [(OUT / "surfaces.csv", rows, FIELDS),
               (OUT / "modules.csv", module_rows, list(module_rows[0]))]
    if args.check:
        for path, expected, fields in outputs:
            with path.open(newline="", encoding="utf-8") as stream:
                actual = list(csv.DictReader(stream))
            if actual != [{k: str(v) for k, v in r.items()} for r in expected]:
                raise SystemExit("Stale ledger: " + str(path))
        if json.loads((OUT / "inventory-summary.json").read_text()) != summary:
            raise SystemExit("Stale inventory summary")
        print("Inventory matches source; no runtime acceptance inferred.")
        return
    for path, values, fields in outputs:
        write_csv(path, values, fields)
    (OUT / "inventory-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()

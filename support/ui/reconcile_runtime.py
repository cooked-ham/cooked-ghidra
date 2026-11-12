"""Reconcile runtime registrations and owners; never infer visual acceptance.

Supply multiple captures for different tools, fixtures or optional installations.
Class links describe implementation; owner links describe registration provenance.
Neither proves that an action's handler or custom content has been reviewed.
"""

import argparse
import csv
import hashlib
import json
import re
from collections import Counter, defaultdict
from functools import lru_cache
from pathlib import Path

from inventory import OUT, ROOT, write_csv, tracked_files


FIELDS = ["capture", "kind", "class", "name", "entry_path", "owner",
          "source_candidates", "class_match", "owner_candidates", "owner_match",
          "modules", "fixtures", "match", "handler_source", "handler_line", "handler_match", "blockers"]
EXTERNAL_HARNESS = {"uirefresh.UiRefreshHarnessPlugin", "uirefresh.UiRefreshHarnessPlugin$HarnessProvider",
                    "uirefresh.UiRefreshLauncherPlugin"}


def source_index(rows):
    declarations = defaultdict(list)
    files = defaultdict(list)
    for row in rows:
        source = row["source"]
        if not source.endswith(".java") or "/src/main/java/" not in source:
            continue
        qualified = source.split("/src/main/java/", 1)[1][:-5].replace("/", ".")
        files[qualified].append(row)
        if row["kind"] in {"entry-hook", "ui-helper"}:
            continue
        package = qualified.rsplit(".", 1)[0]
        declared = package + "." + row["surface"].split(" #", 1)[0].replace(".", "$")
        declarations[declared].append(row)
    return declarations, files


def resolve_class(name, declarations, files):
    if name in declarations:
        return declarations[name], "exact declaration"
    # Anonymous/local classes have no stable declaration in the source ledger.
    # An enclosing file can contain several surfaces, so expose this weaker link.
    enclosing = name.split("$", 1)[0]
    if enclosing in files:
        return files[enclosing], "enclosing source file; handler tracing required"
    return [], "unresolved/external"


def reconcile(captures, sources):
    declarations, files = source_index(sources)
    result = []
    for capture, runtime in captures:
        plugins = defaultdict(set)
        for row in runtime:
            if row["kind"] == "plugin":
                plugins[row["name"]].add(row["class"])
        for row in runtime:
            candidates, class_match = resolve_class(row["class"], declarations, files)
            handler_rows, handler_source, handler_line, handler_match = trace_handler(row, files)
            if class_match.startswith("enclosing source file") and handler_source:
                class_match = "enclosing source file; runtime handler traced"
            if row["class"] in EXTERNAL_HARNESS:
                class_match = "external capture harness; excluded from first-party scope"
            elif row["class"].startswith(("java.", "javax.", "sun.", "com.sun.", "com.formdev.", "org.jdesktop.", "org.fife.", "com.jidesoft.", "edu.uci.")):
                class_match = "platform/third-party implementation; not first-party source"
            elif class_match == "unresolved/external" and handler_source:
                class_match = "runtime source outside surface ledger"
            owners = plugins.get(row["owner"], set())
            owner_rows = []
            for owner in sorted(owners):
                linked, _ = resolve_class(owner, declarations, files)
                owner_rows.extend(linked)
            if len(owners) > 1:
                owner_match = "ambiguous runtime owner"
            elif owner_rows:
                owner_match = "loaded plugin linked"
            elif owners:
                owner_match = "external/unindexed loaded plugin"
            else:
                owner_match = "non-plugin owner label; runtime handler traced" if handler_source else "owner not a loaded plugin; trace service/infrastructure"
            linked = candidates + owner_rows + handler_rows
            result.append({
                "capture": capture, **{key: row[key] for key in
                    ("kind", "class", "name", "entry_path", "owner")},
                "source_candidates": ";".join(sorted({r["id"] for r in candidates})),
                "class_match": class_match,
                "owner_candidates": ";".join(sorted({r["id"] for r in owner_rows})),
                "owner_match": owner_match,
                "modules": ";".join(sorted({r["module"] for r in linked} | ({handler_source.split("/src/main/java/", 1)[0]} if "/src/main/java/" in handler_source else set()))),
                "fixtures": ";".join(sorted({r["fixture"] for r in linked})),
                "match": "implementation and owner linked" if candidates and owner_rows
                    else "implementation linked" if candidates
                    else "owner linked; implementation unresolved" if owner_rows
                    else "unresolved/external",
                "handler_source": handler_source, "handler_line": handler_line,
                "handler_match": handler_match,
                "blockers": "Registration evidence only; exercise entry path, dialogs, dynamic menus, "
                    "custom content and required states; assign reviewer and acceptance evidence",
            })
    return sorted(result, key=lambda r: tuple(r[k] for k in
                  ("capture", "kind", "class", "name", "entry_path", "owner")))


def trace_handler(row, files):
    """Prefer the callback's real nest host over a generic ActionBuilder implementation.

    A unique literal action name in that host supplies a source anchor. Bytecode debug metadata
    supplies the anonymous class's actual actionPerformed line. Neither invokes the action.
    """
    callback = row.get("callback_host", "").split("$", 1)[0]
    if callback and callback in files:
        linked = files[callback]
        paths = {r["source"] for r in linked}
        if len(paths) == 1:
            path = next(iter(paths))
            if row.get("callback_method") and row.get("callback_line", "").isdigit() and int(row["callback_line"]) > 0:
                return linked, path, row["callback_line"], "runtime lambda target and source line"
            source = ROOT / path
            if source.is_file():
                literal = '"' + row["name"].replace('\\', '\\\\').replace('"', '\\"') + '"'
                text = source.read_text(encoding="utf-8")
                matches = list(re.finditer(re.escape(literal), text))
                if len(matches) == 1:
                    return linked, path, str(text.count("\n", 0, matches[0].start()) + 1), "callback host and unique action-name anchor"
            return linked, path, "", "callback host; dynamic/ambiguous action anchor"
    enclosing = row["class"].split("$", 1)[0]
    if row.get("source_file"):
        package = enclosing.rsplit(".", 1)[0]
        debug_file_class = package + "." + Path(row["source_file"]).stem
        if debug_file_class in files:
            enclosing = debug_file_class
    if row.get("source_file") and enclosing in files and row.get("source_line", "0").isdigit():
        linked = files[enclosing]
        paths = {r["source"] for r in linked if Path(r["source"]).name == row["source_file"]}
        if len(paths) == 1 and int(row.get("source_line", "0")) > 0:
            return linked, next(iter(paths)), row["source_line"], "runtime class bytecode source line"
    if row.get("source_file") and row.get("source_line", "0").isdigit() and int(row["source_line"]) > 0:
        source = all_java_sources().get(enclosing)
        if source and Path(source).name == row["source_file"]:
            return [], source, row["source_line"], "runtime class source line outside surface ledger"
    return [], "", "", "not captured; source tracing required"


@lru_cache(maxsize=1)
def all_java_sources():
    return {path.split("/src/main/java/", 1)[1][:-5].replace("/", "."): path
            for path in tracked_files() if "/src/main/java/" in path and path.endswith(".java")}


def module_coverage(sources, rows, installed=None, all_modules=()):
    modules = defaultdict(set)
    for module in all_modules:
        modules[module]
    for source in sources:
        modules[source["module"]].add(source["id"])
    coverage = []
    for module, ids in sorted(modules.items()):
        observed = [r for r in rows if module in r["modules"].split(";")]
        direct = {i for r in observed for i in r["source_candidates"].split(";") if i in ids}
        coverage.append({
            "module": module, "source_candidates": len(ids),
            "class_linked_candidates": len(direct), "registrations": len(observed),
            "captures": ";".join(sorted({r["capture"] for r in observed})),
            "installation": "not captured" if installed is None else
                "active module root captured" if module in installed else
                "absent from captured runtime; verify optional/build configuration",
            "gate": "partial registration evidence" if observed else "no runtime evidence" if ids else "no discovered UI candidates",
            "blockers": "Dialogs/dynamic content and per-state review remain open" if observed
                else "Prepare fixture and capture owning tool/optional installation",
        })
    return coverage


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("captures", nargs="+", type=Path)
    parser.add_argument("--output", type=Path, default=OUT)
    parser.add_argument("--installed-modules", type=Path,
                        help="Application module-root snapshot from the same runtime")
    parser.add_argument("--require-registration-reconciled", action="store_true",
                        help="Fail if any captured first-party registration or action handler is unresolved")
    args = parser.parse_args()
    captures, provenance = [], []
    for path in args.captures:
        with path.open(newline="", encoding="utf-8-sig") as stream:
            runtime = list(csv.DictReader(stream))
        missing = {"kind", "class", "name", "entry_path", "owner"} - set(runtime[0] if runtime else [])
        if missing:
            parser.error(f"{path}: empty capture or missing columns: {sorted(missing)}")
        if path.name in {name for name, _ in captures}:
            parser.error(f"Duplicate capture filename: {path.name}")
        captures.append((path.name, runtime))
        provenance.append({"capture": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                           "registrations": len(runtime)})
    with (OUT / "surfaces.csv").open(newline="", encoding="utf-8") as stream:
        sources = list(csv.DictReader(stream))
    with (OUT / "modules.csv").open(newline="", encoding="utf-8") as stream:
        modules = [r["module"] for r in csv.DictReader(stream)]
    installed = None
    installation = None
    if args.installed_modules:
        with args.installed_modules.open(newline="", encoding="utf-8-sig") as stream:
            installed = {r["runtime_root"] for r in csv.DictReader(stream)}
        installation = {"capture": args.installed_modules.name,
                        "sha256": hashlib.sha256(args.installed_modules.read_bytes()).hexdigest(),
                        "active_module_roots": len(installed)}
    rows = reconcile(captures, sources)
    coverage = module_coverage(sources, rows, installed, modules)
    args.output.mkdir(parents=True, exist_ok=True)
    write_csv(args.output / "runtime-reconciliation.csv", rows, FIELDS)
    unresolved = [r for r in rows if r["class_match"] == "unresolved/external"
                  or r["owner_match"] == "ambiguous runtime owner"
                  or (r["kind"] == "action" and r["handler_match"] in {
                      "not captured; source tracing required", "callback host; dynamic/ambiguous action anchor"})]
    write_csv(args.output / "runtime-unresolved.csv", unresolved, FIELDS)
    write_csv(args.output / "runtime-module-coverage.csv", coverage,
              ["module", "source_candidates", "class_linked_candidates", "registrations",
               "captures", "installation", "gate", "blockers"])
    summary = {"schema": 2, "captures": provenance, "installation": installation,
               "source_ledger_sha256": hashlib.sha256((OUT / "surfaces.csv").read_bytes()).hexdigest(),
               "module_ledger_sha256": hashlib.sha256((OUT / "modules.csv").read_bytes()).hexdigest(),
               "kinds": dict(sorted(Counter(r["kind"] for r in rows).items())),
               "class_matches": dict(sorted(Counter(r["class_match"] for r in rows).items())),
               "owner_matches": dict(sorted(Counter(r["owner_match"] for r in rows).items())),
               "handler_matches": dict(sorted(Counter(r["handler_match"] for r in rows if r["kind"] == "action").items())),
               "unresolved_registrations_or_handlers": len(unresolved),
               "registration_reconciled": not unresolved,
               "modules_with_registration_evidence": sum(bool(r["registrations"]) for r in coverage),
               "modules_without_runtime_evidence": sum(not r["registrations"] for r in coverage),
               "source_modules_absent_from_runtime": sum(installed is not None and r["module"] not in installed for r in coverage),
               "runtime_reconciled": False,
               "release_blocker": "Registration links do not prove exhaustive menus, dialogs, "
                   "providers, optional installations or visual/functional acceptance."}
    (args.output / "runtime-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))
    if args.require_registration_reconciled and unresolved:
        raise SystemExit(f"Unresolved runtime registrations or handlers: {len(unresolved)}")


if __name__ == "__main__":
    main()

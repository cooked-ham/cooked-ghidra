"""Classify UI-bearing roots absent from the application registry without inferring acceptance."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
LEDGER = ROOT / "docs/ui-fidelity"
ECLIPSE = "GhidraBuild/EclipsePlugins/GhidraDev/GhidraDevPlugin"
LAUNCH = "GhidraBuild/LaunchSupport"
SKELETON = "GhidraBuild/Skeleton"


def references_type(body, package, name):
    if not re.search(r"\b" + re.escape(name) + r"\b", body):
        return False
    same_package = re.search(r"(?m)^\s*package\s+" + re.escape(package) + r"\s*;", body)
    imported = re.search(r"(?m)^\s*import\s+(?:static\s+)?" + re.escape(package) +
                         r"\.(?:" + re.escape(name) + r"|\*)(?=[.;])", body)
    return bool(same_package or imported or package + "." + name in body)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=LEDGER / "references/source-scope")
    args = parser.parse_args()
    with (LEDGER / "surfaces.csv").open(newline="", encoding="utf-8") as stream:
        surfaces = list(csv.DictReader(stream))
    xml_path = ROOT / ECLIPSE / "plugin.xml"
    xml_text = xml_path.read_text(encoding="utf-8")
    extensions = ET.fromstring(xml_text)
    registered = {}
    for extension in extensions.findall("extension"):
        for node in extension.iter():
            for attribute in ("class", "delegate", "defaultHandler"):
                if value := node.get(attribute):
                    registered[value] = (extension.get("point"), node.get("name") or node.get("id") or node.tag)
    java_sources = {p: p.read_text(encoding="utf-8") for p in (ROOT / ECLIPSE / "src/main/java").rglob("*.java")}
    eclipse_capture = args.output / "eclipse/eclipse-review.csv"
    eclipse_rows = []
    if eclipse_capture.exists() and (eclipse_capture.parent / "review.done").exists():
        with eclipse_capture.open(newline="", encoding="utf-8") as stream:
            eclipse_rows = list(csv.DictReader(stream))
    rows = []
    for surface in surfaces:
        module = surface["module"]
        if module not in {ECLIPSE, LAUNCH, SKELETON}:
            continue
        source = ROOT / surface["source"]
        text = source.read_text(encoding="utf-8")
        fingerprint_input = text if source.suffix == ".java" else source.read_bytes().hex()
        if hashlib.sha256(fingerprint_input.encode()).hexdigest() != surface["source_hash"]:
            raise SystemExit(f"Stale source fingerprint: {source}")
        row = {key: surface[key] for key in ("id", "module", "kind", "surface", "source", "line", "source_hash")}
        row.update(reviewer="Codex source review", visual_acceptance="not inferred")
        if module == LAUNCH:
            row.update(classification="bootstrap chooser; not an application plugin",
                entry_path="LaunchSupport <installation> -jdk_home -ask; press Enter for directory chooser",
                implementation="askJavaHome: JFileChooser.DIRECTORIES_ONLY; CLI fallback when headless or MacJavaFinder",
                shipping_evidence="GhidraBuild/LaunchSupport/build.gradle; support/LaunchSupport.jar",
                fixture="Disposable application.settingsdir and stdin; do not overwrite the active JDK selection",
                blocker="Bootstrap chooser interaction/cancellation requires separate evidence; application registry absence is expected")
        elif module == SKELETON:
            row.update(classification="extension source template; not an installed plugin",
                entry_path="Create/build/install an extension from Extensions/Ghidra/Skeleton, then Configure > Examples > SkeletonPlugin",
                implementation="SkeletonPlugin > MyProvider > My Action > Custom Action / Hello",
                shipping_evidence="GhidraBuild/Skeleton/build.gradle: compileJava.enabled=false, jar.enabled=false; source copied to Extensions/Ghidra/Skeleton",
                fixture="Build the copied template as a disposable extension; activate SkeletonPlugin",
                blocker="Template-generated extension must be exercised separately; source presence does not imply installed runtime UI")
        else:
            package = re.search(r"\bpackage\s+([\w.]+)\s*;", text)
            qualified = package[1] + "." + source.stem if package else source.stem
            registration = registered.get(qualified)
            if registration:
                entry = "Eclipse > " + registration[0] + " > " + registration[1]
                evidence = "plugin.xml:" + str(xml_text[:xml_text.index(qualified)].count("\n") + 1)
            else:
                users = []
                for path, body in java_sources.items():
                    if path == source or not references_type(body, package[1], source.stem):
                        continue
                    users.append(str(path.relative_to(ROOT)).replace("\\", "/"))
                entry = "Eclipse GhidraDev internal dependency of: " + "; ".join(users)
                evidence = "Source references retained in entry_path; plugin.xml defines the containing Eclipse integrations"
            row.update(classification="separate Eclipse SWT/JFace add-on" if surface["kind"] in {"dialog", "component"} or
                       any(part in source.stem for part in ("Dialog", "WizardPage", "TabGroup"))
                       else "Eclipse integration helper; not a standalone Swing window",
                entry_path=entry, implementation=qualified, shipping_evidence=evidence,
                fixture="Eclipse PDE/JDT/Buildship workbench with GhidraDev bundle; see META-INF/MANIFEST.MF",
                blocker="Eclipse-hosted rendering is not covered by Ghidra Swing captures; no visual pass claimed")
        row["review_evidence"] = ""
        row["reviewed_states"] = "source classification only"
        if module == LAUNCH and (LEDGER / "references/navigation-followup/bootstrap/bootstrap-checks.txt").exists():
            row.update(review_evidence="references/navigation-followup/bootstrap/bootstrap-checks.txt",
                reviewed_states="Actual bootstrap chooser; Cancel; invalid directory feedback; valid CLI JDK recovery in isolated installation",
                blocker="No unexplained application plugin gap; startup chooser is hosted before application theme initialization")
        elif module == SKELETON and (LEDGER / "references/navigation-followup/template/template-checks.txt").exists():
            row.update(review_evidence="references/navigation-followup/template/template-checks.txt; references/navigation-followup/template/skeleton.runtime.csv",
                reviewed_states="Built actual shipped template, activated plugin, empty provider, local action and message dismissal; help resource remains source-reviewed",
                blocker="Template is deliberately shipped as source, not enabled in the primary application runtime")
        elif module == ECLIPSE and eclipse_rows:
            matches = [capture for capture in eclipse_rows if capture["implementation"] == qualified
                       or capture["title"].startswith(source.stem + " / ")]
            row["review_evidence"] = "; ".join("references/source-scope/eclipse/" + capture["screenshot"] for capture in matches)
            row["reviewed_states"] = "; ".join(capture["state"] for capture in matches) or "Source helper classified; exercised through containing Eclipse workflows where recorded"
            row["blocker"] = "Recorded Eclipse form/configuration states only; project generation/export, live communications and optional PyDev/CDT runtime workflows are not certified"
        rows.append(row)
    if {row["module"] for row in rows} != {ECLIPSE, LAUNCH, SKELETON}:
        raise SystemExit("Expected source-only module scope changed; review classifications")
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / "classifications.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    summary = {"candidates_classified": len(rows), "by_module": {
        module: sum(row["module"] == module for row in rows) for module in (ECLIPSE, LAUNCH, SKELETON)},
        "source_classification_complete": True, "visual_acceptance_inferred": False,
        "source_ledger_sha256": hashlib.sha256((LEDGER / "surfaces.csv").read_bytes()).hexdigest(),
        "eclipse_registration_sha256": hashlib.sha256(xml_path.read_bytes()).hexdigest()}
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()

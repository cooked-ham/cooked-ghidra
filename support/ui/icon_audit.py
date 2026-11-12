"""Audit theme and literal Java image references; retain semantic artwork explicitly."""
import csv
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/ui-fidelity/references/icon-audit"


def image_references(line):
    match = re.match(r'\s*(icon\.[\w.-]+)\s*=\s*(.*)', line)
    if not match:
        return []
    value = match[2].split("//", 1)[0]
    return [(match[1], image) for image in re.findall(
        r'(?<![\w.])[\w./+\-]+\.(?:png|gif|svg|ico)(?![\w.])', value)
        if "uirefresh/" not in image and not image.startswith("icon.")]


def direct_references(text):
    clean = re.sub(r'/\*[\s\S]*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"',
        lambda match: re.sub(r'[^\n]', ' ', match[0]) if match[0].startswith(('//', '/*')) else match[0], text)
    pattern = re.compile(r'\b(loadImageIcon|loadImage|loadIcon|getResource|createImageIcon)\(\s*"([^"\n]+\.(?:png|gif|svg|ico))"', re.I)
    return [(match[1], match[2], clean.count("\n", 0, match.start()) + 1)
            for match in pattern.finditer(clean) if "uirefresh/" not in match[2] and "://" not in match[2]]


def audit():
    source = (ROOT / "Ghidra/Framework/Gui/src/main/java/generic/theme/uirefresh/icons/IconSet.java").read_text()
    mappings = {}
    for block in re.findall(r'\bfile\((.*?)\);', source, re.S):
        strings = re.findall(r'"([^"\n]+)"', block)
        if strings:
            for name in strings[1:]:
                mappings[name] = strings[0]
    refs = defaultdict(set)
    direct_images = set()
    for path in sorted((ROOT / "Ghidra").rglob("*.theme.properties")):
        if "build" in path.parts:
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            for identity, image in image_references(line):
                refs[image].add(str(path.relative_to(ROOT)).replace("\\", "/") + ":" + identity)
    theme_images = set(refs)
    for path in sorted((ROOT / "Ghidra").rglob("*.java")):
        if "build" in path.parts or "/src/main/" not in path.as_posix():
            continue
        text = path.read_text(encoding="utf-8")
        for loader, image, line in direct_references(text):
            direct_images.add(image)
            refs[image].add(path.relative_to(ROOT).as_posix() + ":" + loader + "@" + str(line))
    resources = defaultdict(list)
    for path in (ROOT / "Ghidra").rglob("*"):
        if "build" in path.parts or not path.is_file() or path.suffix.lower() not in {".png", ".gif", ".svg", ".ico"}:
            continue
        if "/images/" in path.as_posix() and "/uirefresh/" not in path.as_posix():
            resources[path.name].append(path)
    rows = []
    for image, consumers in sorted(refs.items()):
        base = Path(image).name
        files = resources.get(base, [])
        if image not in theme_images:
            category, reason = "retain-direct-load", "Literal image load bypasses theme-ID remapping; retain resource size and semantics at the recorded call site."
        elif base in mappings:
            category, reason = "glyph", "Equivalent action or object glyph: " + mappings[base]
        elif re.search(r'empty|blank|slash', base, re.I):
            category, reason = "retain-spacing", "Spacer, empty image or literal marker; replacement changes layout or syntax."
        elif re.search(r'hourglass24|eatbits', base, re.I):
            category, reason = "retain-animation", "Frame in a multi-image progress animation; preserve sequence and frame size."
        elif re.search(r'GhidraIcon|vscode|eclipse|www_|debugger\d*\.|start-here|plasma|katomic|kmessedwords|kcmprocessor', base, re.I):
            category, reason = "retain-identity", "Application, provider or brand identity; a generic action glyph changes recognition."
        elif re.search(r'cursor|lasso|dragCopy|dragMove', base, re.I):
            category, reason = "retain-cursor", "Cursor/tool artwork has a specific hotspot or selection geometry."
        elif "icon.fsbrowser.file.extension" in base or re.search(r'^(application-|text-x-|page_|video-|audio-|image|css\.|html\.|xhtml\.|python\.)', base) or "/oxygen/" in image or "/nuvola/" in image:
            category, reason = "retain-file-kind", "File or device kind is intentionally distinct from a generic document action."
        elif re.search(r'overlay|ineff|breakpoint|bullet|led|flag|Check|InView|NotInView|disabled|connected|conflict|changed|lightbulb|register-marker|object-running|object-terminated|color_swatch|Book(Brown|Green|Red)|_can', base, re.I):
            category, reason = "retain-semantic-state", "Color, overlay or state is analysis information; no equivalent state-preserving glyph exists."
        else:
            category, reason = "retain-specialist", "Distinct specialist symbol; preserve original shape and meaning for " + ", ".join(sorted({c.rsplit(":", 1)[-1] for c in consumers}))
        rows.append({"stock_image": image, "disposition": category, "glyph": mappings.get(base, ""),
            "reason": reason, "consumers": ";".join(sorted(consumers)),
            "resources": ";".join(str(p.relative_to(ROOT)).replace("\\", "/") for p in files),
            "sha256": ";".join(hashlib.sha256(p.read_bytes()).hexdigest() for p in files)})
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT / "stock-images.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)
    summary = {"stock_image_values": len(rows), "theme_referenced_stock_images": len(theme_images),
        "direct_literal_image_values": len(direct_images), "dispositions": dict(Counter(r["disposition"] for r in rows)),
        "unresolved_resources": [r["stock_image"] for r in rows if not r["resources"]],
        "note": "Glyph file mappings apply only to unmodified stock values or size(16,16); composite modifiers remain stock. Retention is deliberate, not a promise of equivalent new artwork."}
    (OUT / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    # Contact sheets support visual review of every retained image and its actual consumers.
    from PIL import Image, ImageDraw, ImageFont
    retained = [r for r in rows if r["disposition"] != "glyph" and r["resources"]]
    font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 12)
    for start in range(0, len(retained), 60):
        sheet = Image.new("RGB", (1200, 600), "#24211e"); draw = ImageDraw.Draw(sheet)
        for index, row in enumerate(retained[start:start + 60]):
            x, y = index % 4 * 300, index // 4 * 40
            try:
                with Image.open(ROOT / row["resources"].split(";")[0]) as stock:
                    icon = stock.convert("RGBA"); icon.thumbnail((30, 30))
                    sheet.paste(icon, (x + 5, y + 5), icon)
            except Exception:
                draw.text((x + 5, y + 5), "?", font=font, fill="white")
            draw.text((x + 43, y + 4), row["stock_image"][-35:], font=font, fill="#e4d9c7")
            draw.text((x + 43, y + 20), row["disposition"], font=font, fill="#ae9c84")
        sheet.save(OUT / f"retained-{start // 60 + 1}.png")
    print(json.dumps(summary, indent=2))
    if summary["unresolved_resources"]:
        raise SystemExit("Unresolved stock image resources; see summary.json")


if __name__ == "__main__":
    audit()

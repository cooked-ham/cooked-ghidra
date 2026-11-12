"""Render the graphite token reference and contrast report directly from GraphiteTheme.java."""

from html import escape
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
THEME = ROOT / "Ghidra/Framework/Gui/src/main/java/generic/theme/uirefresh/GraphiteTheme.java"
OUT = ROOT / "docs/ui-fidelity/references"
NAMES = ["canvas", "content", "raised", "hover", "selection", "inactive selection", "outline",
         "primary text", "secondary text", "disabled text", "keyboard focus", "navigation amber",
         "text on amber", "success", "warning", "error", "analysis highlight", "search highlight",
         "current line"]


def contrast(first, second):
    def luminance(color):
        rgb = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
        rgb = [c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in rgb]
        return sum(c * w for c, w in zip(rgb, (.2126, .7152, .0722)))
    light, dark = sorted((luminance(first), luminance(second)), reverse=True)
    return (light + .05) / (dark + .05)


def main():
    source = THEME.read_text(encoding="utf-8")
    body = source.split("new Tokens(", 1)[1].split("public GraphiteTheme", 1)[0]
    colors = ["#" + value for value in re.findall(r'c\(0x([A-Fa-f0-9]{6})\)', body)]
    assert len(colors) == len(NAMES), "Update token names when the Java palette changes"
    tokens = dict(zip(NAMES, colors))
    rows = []
    for fg in ("primary text", "secondary text"):
        for bg in ("canvas", "content", "raised", "hover", "selection", "inactive selection", "current line"):
            rows.append((fg, bg, 4.5))
    rows.append(("text on amber", "navigation amber", 4.5))
    for fg in ("success", "warning", "error", "navigation amber"):
        rows.append((fg, "content", 4.5))
    for fg in ("keyboard focus", "outline"):
        for bg in ("canvas", "content", "raised"):
            rows.append((fg, bg, 3.0))
    failures = [(fg, bg) for fg, bg, minimum in rows
                if contrast(tokens[fg], tokens[bg]) < minimum]
    report = ["# Graphite reference contrast", "",
              "Generated from `GraphiteTheme.java`; sRGB contrast, no alpha blending.", "",
              "| Foreground | Background | Ratio | Minimum | Result |",
              "| --- | --- | ---: | ---: | --- |"]
    for fg, bg, minimum in rows:
        ratio = contrast(tokens[fg], tokens[bg])
        report.append(f"| {fg} | {bg} | {ratio:.2f} | {minimum:.1f} | {'PASS' if ratio >= minimum else 'FAIL'} |")
    report += ["", f"Failures: {len(failures)}. Syntax and graph label contrast are checked separately in JUnit.",
               "Disabled controls are shown in the sheet; they are exempt from the normal text threshold.",
               "Physical DPI, screen reader behavior and custom paint need runtime acceptance.", ""]

    parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="1210" viewBox="0 0 1440 1210">',
             '<defs><linearGradient id="chrome" x2="0" y2="1"><stop offset="0" stop-color="#44372D"/>'
             '<stop offset="1" stop-color="#2B2723"/></linearGradient></defs>',
             '<rect width="1440" height="1210" fill="#171615"/>',
             '<style>text{font-family:Segoe UI,Arial,sans-serif;fill:#F2EDE5} .muted{fill:#C3B7A8}'
             '.mono{font-family:Consolas,monospace}</style>']

    def text(x, y, value, size=16, extra=""):
        parts.append(f'<text x="{x}" y="{y}" font-size="{size}" {extra}>{escape(value)}</text>')

    def rect(x, y, w, h, fill, stroke=tokens["outline"], radius=8):
        parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}"/>')

    text(48, 62, "GRAPHITE / 01", 16, 'class="muted"')
    text(48, 108, "Warm chrome. Quiet analysis surfaces.", 32)
    text(48, 144, "Token and interaction reference · Windows first · implementation preview, acceptance pending", 17, 'class="muted"')
    for index, (name, color) in enumerate(tokens.items()):
        col, row = index % 5, index // 5
        x, y = 48 + col * 272, 184 + row * 112
        rect(x, y, 248, 64, color)
        # Put captions outside the swatches; the colors are not all text backgrounds.
        text(x, y + 86, name + "  " + color, 14, 'class="muted"')
    text(48, 670, "Interaction states", 24)
    samples = [("Active", "navigation amber", "text on amber", None),
               ("Inactive", "raised", "secondary text", None),
               ("Hover", "hover", "primary text", None),
               ("Pressed", "selection", "primary text", None),
               ("Focused", "raised", "primary text", "keyboard focus"),
               ("Selected ✓", "selection", "primary text", "navigation amber"),
               ("Disabled", "raised", "disabled text", None),
               ("Error !", "raised", "error", "error"),
               ("Warning △", "raised", "warning", "warning")]
    for i, (label, bg, fg, outline) in enumerate(samples):
        x, y = 48 + i % 5 * 272, 698 + i // 5 * 74
        rect(x, y, 248, 48, tokens[bg], tokens[outline] if outline else tokens["outline"])
        text(x + 16, y + 30, label, 16, f'style="fill:{tokens[fg]}"')
    rect(48, 870, 1320, 86, "url(#chrome)")
    text(68, 901, "Copper / #CB9474 · chrome gradient / #44372D → #2B2723", 17)
    text(68, 931, "Expressive depth belongs in headers, toolbar groups and overlays. Code and hex stay flat.", 16, 'class="muted"')
    text(48, 1008, "Typography and geometry", 24)
    text(48, 1042, "Segoe UI Variable Text → Segoe UI → logical SansSerif · 13 pt controls", 17)
    text(48, 1074, "Cascadia Mono → JetBrains Mono → Consolas → Monospaced · 13 pt code", 17, 'class="mono"')
    text(48, 1106, "Spacing 4 / 8 / 12 / 16 / 24 · chrome radius 8–10 · compact rows 18 / standard rows 22", 17)
    text(48, 1150, "Brand amber is paired with labels; warnings use a triangle, errors use !, and keyboard focus uses cyan.", 16, 'class="muted"')
    text(48, 1180, "State samples and gradient are design specifications; live screenshots show current implementation fidelity.", 15, 'class="muted"')
    parts.append("</svg>")
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "tokens.svg").write_text("\n".join(parts) + "\n", encoding="utf-8")
    (OUT / "contrast.md").write_text("\n".join(report), encoding="utf-8")
    print(f"Rendered {len(tokens)} tokens; {len(rows)} contrast pairs; {len(failures)} failures")
    if failures:
        raise SystemExit(str(failures))


if __name__ == "__main__":
    main()

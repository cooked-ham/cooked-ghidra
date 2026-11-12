# Stock icon disposition audit — 2026-11-12

[stock-images.csv](stock-images.csv) records 543 distinct image values: 531 from source theme
properties, thirteen literal Java-loader values (one shared with the theme set). All resources
resolve. Each row lists consuming IDs/call sites, resource paths, hashes and a disposition.
This count includes paths/composite values and is not comparable to the earlier 279-file backlog.

234 values use existing glyph mappings. The remaining 309 retain their stock artwork deliberately:
156 specialist symbols, 62 composite/state indicators, 31 file kinds, eighteen identity/brand icons,
eighteen animation frames, seven spacing resources, five cursor/hotspot resources and twelve direct
loads. Literal loaders bypass theme-ID substitution. Dynamic resource construction and unused
assets are outside this reference scan; the audit is not a claim to enumerate every raster file.

All six retained contact sheets were reviewed. Semantic distinction takes precedence over reducing
the retained count. Nine clear action mappings were added. Ambiguous debugger step/reverse-step,
breakpoint-disable, marked stack-frame and directional-header substitutions were removed. Java
file-kind and Eclipse brand artwork were restored. No new glyph artwork or regenerated palette
PNGs were needed for this pass.

```powershell
# Pillow is needed only for contact sheets; the scanner/tests use the standard library.
& build/verification/ui-review-python/Scripts/python.exe support/ui/icon_audit.py
& 'C:\Python315\python.exe' support/ui/test_icon_audit.py
```

Five parser regression tests pass, including composite/alias handling, C++ resource names and
comment-aware direct-loader discovery. Glyph remapping applies only to bare stock values or the
supported 16×16 size modifier; composite/state artwork remains stock. Inspect [summary.json](summary.json)
and `retained-1.png` through `retained-6.png` alongside the consuming IDs before changing a decision.

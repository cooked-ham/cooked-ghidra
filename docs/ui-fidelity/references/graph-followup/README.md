# Graph labels, grouping and specialist follow-up — 2026-11-12

Modern Decompiler Nested Layout labels now clear vertex cards and previously placed labels.
Placement keeps a clear original anchor, or chooses the shortest clear movement along an axis.
Card bounds are cached once per paint pass and reset for each repaint, including theme changes
and panning. Graph topology, vertex placement and stock label placement are unchanged.

## Reviewed captures

- [Edge labels](graph-labels.png): the four-block fixture's labels are fully visible and separate.
- [Grouped branches](graph-grouped.png) and [selected group](graph-grouped-selected.png): two
  branch vertices become one group, with translucent back cards and a selected outline.
- [Stock group](graph-grouped-stock.png): the existing repeated-content stack remains.
- [Overview at 8%](graph-grouped-overview.png): simplified cards below the detailed-paint threshold.
- [Ungrouped](graph-ungrouped.png): the original four vertices return after modern → stock → modern.
- [Debugger](debugger-trace.png): two populated Threads lifespan bars render translucent grey;
  this capture stages the rebuilt ProposedUtils jar.
- [Call trees](call-trees.png): one incoming caller and two outgoing calls, expanded in both trees.
  Two real caller functions are added only to the disposable project after BSim/diff captures.
- [Code Compare](code-compare-graph.png): four-block and single-block functions side by side.
- [Version Tracking](version-tracking-graph.png) and [BSim](bsim-compare-graph.png): two populated
  four-vertex graph viewers, including a loop in the BSim comparison.

The graph harness asserts four vertices → three vertices with two grouped branches → four
vertices with no groups. These are in-process Swing captures at logical scale 1.0 on Windows.
They do not establish physical DPI, screen-reader, performance or exhaustive graph acceptance.

## Validation and reproduction

Seven focused tests pass with zero failures/errors: six placement cases (partial card overlap,
neighbor cards, long labels, prior labels, fractional/panned coordinates and clear anchors),
and one render-pass lifecycle test. The retained JUnit XML files record the results. The five
changed product/test Java files also pass the IDE build and warning/weak-warning inspection.

Build Graph, FunctionGraphDecompilerExtension and their prerequisites, plus ProposedUtils for
the Debugger capture. Full build/test logs are `build/verification/graph-labels-verified-build.log`
and `build/verification/graph-followup-build.log`.

```powershell
.\support\ui\capture-reference.ps1 -Tag graph-review-new `
    -Sequence 'Graphite Preview,(openprogram),(graph)'
.\support\ui\review-specialists.ps1 -Tag debugger-review-new -Surfaces debugger
.\support\ui\review-specialists.ps1 -Tag specialist-graphs-new -Surfaces codebrowser,vt
```

The [graph manifest](graph-capture-manifest.json) and
[Debugger manifest](debugger-capture-manifest.json) record staged jar hashes. Capture runners
copy built jars into disposable runtimes; the specialist runner now includes ProposedUtils
and the Decompiler Nested Layout extension rather than relying on a frozen runtime's jars.
Profiles and projects are disposable copies.

The [specialist manifest](specialist-capture-manifest.json) and
[review rows](specialist-ui-review.csv) retain the comparison/call-tree results. An initial run
exposed stale BSim executable URLs: the seed recorded `/source.bin` instead of
`/binaries/source.bin`. The fixture generator now uses the program's actual parent path (compiled,
not fully regenerated in this pass). The capture relocates its selected result in memory to the
disposable project; the seed database is untouched. A second run correctly rejected empty call
trees; the retained final run adds executable CALL instructions and asserts three direct nodes.
Earlier failed runs remain under `build/verification/specialist-graphs*-20261112`.

Merge/Repository UI, large/dense graph layouts, physical scaling/accessibility, performance and
the inventory/runtime reconciliation gate remain open. The previous nine Docking/Gui/Graph
failures were not reclassified or re-run against a clean checkout during this follow-up.

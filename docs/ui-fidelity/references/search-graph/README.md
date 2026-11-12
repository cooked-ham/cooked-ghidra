# Search presentation and graph navigation

The [inspection report](inspection.json) records 200 initial warnings/weak warnings and
zero remaining errors or warnings across 37 changed Java source/test files. Source hashes
identify the inspected content. No inspection profile was disabled. Narrow annotations retain
external extension/test APIs, the existing protected toolbar field and JDK annotation contracts;
Toolkit modifier masks and captured selection modes have documented false-positive annotations.

The warning cleanup also fixes null handling for Restore Defaults and theme initialization.
The integration regression caught a JTable constructor callback before GTable initialized its
renderer cache; the callback now checks initialization state.

## Reviewed output

- [Action chooser](capture.csv.Graphite-Preview.chooser.png): group headings above results,
  readable owner labels and warm secondary text. Literal query-word emphasis and markup escaping
  are covered by tests. Result entries remain the selectable items; headings add no model rows.
- Memory Search: [ready](capture.csv.memory-ready.png), [loading](capture.csv.memory-loading.png),
  [no matches](capture.csv.memory-empty.png), [populated](capture.csv.memory-results.png), and
  [filtered empty](capture.csv.memory-filtered.png). Filtering preserves the underlying match.
- [Function Graph](capture.csv.Graphite-Preview.Function-Graph.png): zoom out/in, live zoom/reset,
  fit graph and framed overview. The shared footer retains Function Graph's progress monitor.
  Shared graph unavailable states use centered guidance and retain the stock-theme presentation.

The [capture log](capture.csv) includes Graphite → stock → Graphite switching. The
[capture manifest](capture-manifest.json) records the exact Gui/Docking/Graph/Base/Decompiler
jars and harness hash; [artifact hashes](artifacts.json) identify the copied evidence.
Reproduce with `support/ui/review-search-graph.ps1` after building those module jars.
The runner uses disposable profiles/projects and a copied probe source.

## Validation

22 distinct focused tests passed across the recorded runs:

- 10 action chooser tests: filtering, owner queries and disabled-action behavior.
- Four SearchList tests, three search presentation tests and one search context renderer test;
  retained [passing log excerpts](earlier-docking-tests.txt) come from the first focused run.
- One real Memory Search integration test: ready → loading → no matches → results → filtered
  empty → restored results.
- One graph test: live zoom percentage, reset/fit, wrapping at 220 px, progress-footer coexistence,
  unavailable-state controls and stock/modern toolbar visibility.
- Two contrast tests: syntax, graph labels, chrome and secondary guidance across all six themes.

The four `TEST-*.xml` files retain the latest 14 tests with zero failures/errors; the earlier
eight passing tests are retained as original Gradle log excerpts because Gradle replaces its
test XML directory on the next filtered run. Logs remain under `build/verification` as
`search-graph-tests.log`, `search-graph-integration.log` and `search-graph-final-tests.log`.
The initial integration failure is superseded by the passing rerun and retained in the first log.
Gui, Docking, Base, Decompiler, Graph and FunctionGraph jars compile successfully.

These are in-process Swing captures and focused Windows tests, not physical-DPI, screen-reader,
performance or exhaustive specialist-provider acceptance. Cancellation copy is implemented but
not exercised by this capture. Other providers' custom loading/error states, graph card/edge
details and specialist graph viewers remain separate review work.

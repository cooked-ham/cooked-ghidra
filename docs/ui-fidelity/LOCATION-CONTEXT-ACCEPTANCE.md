# Location-context acceptance — 2026-11-11

Follow-up on 2026-11-12: all 36 integration cases pass again. Program/history menus now disambiguate
duplicate names, and the program menu opens the new searchable switcher. Its nine integration cases
and three existing stock navigation checks pass. Actual menu/dialog captures at four logical scales
are in the [navigation follow-up](references/navigation-followup/README.md). Extra physical-DPI,
macOS and screen-reader hardware acceptance is excluded by the owner for this pass; those older
hardware entries below are not outstanding requests. Production breadcrumb rollout remains opt-in.

Status: software checks completed in this Windows/JDK 25 checkout; production rollout
remains opt-in. An isolated application exit/relaunch also passes;
physical acceptance is not established by these tests. This report supersedes the older seven/16/19-case coverage notes.

## Reproduce

```powershell
gradle :Decompiler:integrationTest --tests ghidra.app.plugin.core.decompile.LocationContextTest
```

Check the generated JUnit XML, not only Gradle's exit status (`ignoreFailures=true`).
Retained results: [JUnit](references/location-acceptance/tests.xml).
36 tests pass with zero failures/errors and no skips; IDE error inspections are clean.
The inventory source check and its four regression tests also pass.

Raw run log: `build/verification/location-acceptance-complete.log`.

## Coverage and limits

| Area | Evidence | Result / limit |
| --- | --- | --- |
| Background bursts | 100,000 worker requests while EDT is occupied | One callback/supplier query, before queue sentinel |
| Idle refresh | 1,000 unchanged refreshes under a repaint probe | Zero dirty regions; no timer |
| Navigation stress | 200 alternating navigations, wait for final rendering | Final Listing/Decompiler agree; pending breadcrumbs retain rendered content |
| Performance | 1,000 warm refresh/paint samples; 200 enabled/disabled dispatch samples; 30 completed navigations | Measurements below; sample fixture only |
| History | Existing back/forward service and memento menus, cross-program selection | Program and address restore; refresh does not add history |
| Shortcut rebinding | Change existing Back shortcut to Ctrl+Shift+F9 | Tooltip reflects current binding; binding restored afterward |
| Responsive layout | 240/360/600/1600 logical px; Graphite/Compact/High Contrast captures | Address stays reachable; hidden segments available in overflow; nested program menu anchors to visible control |
| Keyboard/focus | Ghidra key-event harness: Space, Alt+Down, Escape; focus transfer | Swing bindings and traversal pass; native Robot keystrokes did not reach test window in this session |
| Accessibility | Accessible names, descriptions, state text and focusable controls | Metadata checked; actual screen-reader speech is unverified |
| Programs | Tool close/reopen and disk reload into a new Program instance | Targets clear and recover, new domain path shown |
| External references | Project-backed external program and missing destination | Correct destination labels; failed navigation preserves displayed context |
| Address states | Overlay address space, data/undefined bytes, absent function/program, mismatched program/location | No mixed-program breadcrumb; unavailable targets disabled |
| Detached windows | Docking manager detach, navigation, layout XML restore | Breadcrumb follows provider in a real detached Swing window; no mouse drag claim |
| Application restart | Explicitly save session/project/tool, exit JVM, relaunch same disposable profile/project | New PID, same program/address, detached Decompiler, and three previous-history entries restored |
| Saved tools | Save detached tool template and plugin data; load new tool instance | Detached layout and context recreated; original tool keeps its own location |
| Independent views | Clone navigation, program switch restriction, refresh lock/stale edits | Independent binding retained; connected result is not relabeled by clone navigation |
| Multiple tools | Two tool instances at different locations | Each uses its own connected Listing; linkage detail names source tool instance, domain path and address |
| Disabled feature | Startup property absent, new tool and original component checks | No wrapper/supplier; saved format/preferences/shortcuts unchanged |

## Measurement method

Refresh and paint run on the EDT after 100 warm-up refreshes, using the sample x86
program. Paint renders the panel to an ARGB buffer; it excludes desktop composition.
Allocated bytes use the optional JVM thread allocation counter and combine refresh
and paint. Refresh p95 has a 50 ms regression ceiling; reported values are descriptive,
not a large-program release budget.

Dispatch times cover synchronous GoTo service calls in bursts; deferred UI work is
excluded. Disabled and enabled tools run sequentially in one JVM, so warm-up/order
bias prevents interpreting their difference as a causal improvement. Completion times
include GoTo, Listing update, decompiler completion and Swing queue draining. They
include the test harness's waiting overhead and are not input-to-photon latency.

Final measurements (Windows 11, JDK 25; sample fixture):

| Measurement | p50 | p95 |
| --- | --- | --- |
| Refresh | 4.3 µs | 5.0 µs |
| Buffer paint | 57.8 µs | 63.1 µs |
| GoTo dispatch, disabled | 2.845 ms | 3.358 ms |
| GoTo dispatch, enabled | 2.684 ms | 3.145 ms |
| Navigation through decompiler completion | 155.72 ms | 180.21 ms |

Combined refresh/paint allocation: 18,688 bytes per iteration. Unchanged refresh:
zero dirty regions over 1,000 iterations. Full standard output is retained in JUnit.

## Actual application exit/relaunch

Run `support/ui/review-location-restart.ps1 -Tag <unique-tag>` after building Base and
Decompiler jars. This development harness requires the existing disposable release,
seed profile/project and probe extension described by the runner. It copies them;
it does not change the user's active profile or project. The probe explicitly saves
tool/session/project state before exiting the JVM and relaunches Ghidra.

[Save evidence](references/location-acceptance/restart/save.csv) and
[restart evidence](references/location-acceptance/restart/restart.csv) record PIDs
11236 and 7924, `/hostname_x64.exe`, `ram:00401000`, a detached Decompiler and three
prior history entries. The [manifest](references/location-acceptance/restart/manifest.json)
records the four runtime jar and probe source hashes. Main/detached window captures
accompany both CSVs. This verifies explicit-save exit/relaunch, not crash recovery,
mouse docking or every stacked-provider geometry.

## Visual evidence

[Graphite narrow](references/location-acceptance/Graphite-Preview-360.png),
[Graphite wide](references/location-acceptance/Graphite-Preview-1100.png),
[Compact narrow](references/location-acceptance/UI-Refresh-Dark-Compact-360.png),
[Compact wide](references/location-acceptance/UI-Refresh-Dark-Compact-1100.png),
[High Contrast narrow](references/location-acceptance/UI-Refresh-High-Contrast-360.png),
[High Contrast wide](references/location-acceptance/UI-Refresh-High-Contrast-1100.png).
These are panel renders at logical scale 1.0, not physical mixed-DPI validation.

## Remaining release gates

- Exhaustive stacked-provider geometry restoration beyond the exercised detached configuration.
- Real mouse docking/redocking and native keyboard use on the target desktop.
- Actual screen-reader output, physical mixed DPI and multiple monitors.
- Representative large real programs for performance comparison with a matched
  disabled baseline and controlled input-to-render/allocation tracing.

The development property stays opt-in until these release gates have evidence.

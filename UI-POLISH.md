# UI polish progress

The target is a cohesive IDE-quality interface: consistent spacing, typography, icons,
depth, gradients, and interaction states throughout Ghidra. Themes and dock borders
are the foundation; they do not complete the polish work.

The original roadmap and evidence live in `E:\ghidra-ui-refresh\docs`.
The owner's future-work decisions and milestone plan are in [UI-FIDELITY-PLAN.md](UI-FIDELITY-PLAN.md).

## IDE warning cleanup after location/depth work — 2026-11-11

Inspected all 18 changed Java source/test files: 58 warning/weak-warning diagnostics are now
cleared, with zero remaining errors or warnings. Fixed the nullable test lookup, redundant
state/locals, casts, regex escaping and assertions; simplified table-header icon composition
and extracted decompiler persistence helpers without changing saved keys. Five unused dialog
extension methods remain available with method-level `unused` annotations; no inspection profile
was disabled. Support probe scripts remain outside IDE module content roots.

The IDE build and all 48 focused theme/icon/contrast/button/location tests pass. Fresh inspection
results are in [ide-warning-cleanup.json](docs/ui-fidelity/references/ide-warning-cleanup.json);
the Gradle log is `build/verification/ide-warning-cleanup.log`. Regenerated inventory fingerprints
reflect the source edits. Existing captures and their jar hashes remain historical evidence.

## Event-driven location context and Graphite depth — 2026-11-11

Replaced breadcrumb polling with coalesced EDT updates from tool context, provider-local changes
and displayed-program events. Hidden/removed panels release their subscriptions. Added open-program
switching, hierarchical/paged namespace browsing and symbol navigation. Independent clones retain
their existing program binding. Pending feedback includes the plugin's delayed dispatch; clones
show pending/unavailable activity, and edited locked results explicitly show stale state.

Graphite now uses its stronger warm gradient stops in shared headers and toolbars, table headers,
dialog action rows, options/project sections and graph-card headers. Frames gain highlight/shadow
edges, tabs gain a selected highlight, and toolbar hover/pressed/selected fills gain depth. Shared
primitives also style the populated specialist providers. High Contrast retains solid surfaces;
stock rendering and semantic content colors retain their existing paths.

Validation: Gui/Docking/Base/Decompiler/FunctionGraph jars build. Sixteen location-context
integration tests and six contrast/button tests pass with zero failures in JUnit XML. Regressions
include namespace navigation, program switching/closure, overlay address spaces, clone binding,
stale locked results, metadata events and hidden panels. Live captures exercise populated main
views, saved copies/theme switching and Code Browser/VT/Debugger specialist fixtures. Evidence:
[location and depth](docs/ui-fidelity/references/location-graphite/).

The location feature remains opt-in. This implements the shared styling routes; per-widget review
of the full inventory, performance baselines, physical DPI and accessibility acceptance remain open.

## Saved-theme vector restoration — 2026-11-11

Theme parsing now recognizes bundled UI Refresh and Graphite glyph paths and restores
`VectorIcon` instances. Existing saved copies use those same PNG paths, so they gain vector
painting on reload without a format migration. Other resource paths retain normal loading;
the packaged PNGs remain available as fallbacks. Icon modifiers still apply after loading.

Validation: Gui jar builds; 26 `GThemeTest`/`IconValueTest` tests pass with zero failures/errors
in JUnit XML. The round-trip regression saves and reloads all 344 glyph/palette combinations
through both plain theme files and ZIP exports, checking vector identity and multi-resolution
images. Modifier and unrelated/unknown path checks also pass. Log:
`build/verification/vector-theme-roundtrip.log`.

The subsequent [live persistence probe](docs/ui-fidelity/references/vector-persistence/)
retains 283 direct vector registrations through save/reload, ZIP import, theme switch/back
and startup in a second JVM. Preferences restore the saved copy automatically. Both phases
pass with no ERROR/NPE/callout entries in the application log. The runner uses a private
distribution, profile and project. This verifies application APIs and theme startup, not
theme-editor mouse interaction, physical DPI or complete saved-layout restart acceptance.
The frozen specialist runtime was not replaced.

## Specialist surfaces and help callout fix — 2026-11-11

Fixed the help callout NPE that occurred when a theme change reloaded the help page while a
navigation callout was still pending. `DockingHelpBroker.reload()` now disposes the old callout,
and `CalloutRequest` tolerates a missing scroll pane, a missing anchor and a page that is still
reloading instead of dereferencing a null reference area. The rebuilt Docking jar is
SHA-256 `9D630328FA40077C7B450BB3AE25E542E8696A34695DBA58A58A79F0F6613CC2`.

The specialist review now runs one surface per Ghidra process and records real populated providers
from the prepared fixture:

- Code Browser: source program, program diff, two-function comparison and a BSim query with
  11 cross-program hits.
- Version Tracking: four manual matches (one accepted, one rejected, two available), markup
  provider and first-match comparison.
- Debugger: saved synthetic trace with 21 snapshots, two threads and a populated stack/register
  view.

The review report has six evidence rows, nine screenshots and seven runtime registration captures.
Evidence is in [specialists/populated](docs/ui-fidelity/references/specialists/populated/); the raw
run is `build/verification/graphite-specialist-ui-final8`. None of the three surface application
logs contains a `NullPointerException`, `CalloutRequest` or `ERROR` entry after the fix.

The runner is now a single-run tool: it takes a process lock, reclaims a stale lock only after
checking the recorded PID is dead, limits each surface to four minutes, and kills that surface's
own JVM before starting the next one. The earlier repeated-run behavior came from a second agent
session and from a foreground shell cutoff that left Ghidra running; both have been removed. Each
invocation now produces one tagged run directory and does not overlap another invocation.

These are data/visibility checks with in-process Swing painting and off-screen tool frames, not
visual, interaction, physical-DPI or accessibility acceptance. Live Windows debugger agents,
BSim authentication/network failures, VT failure paths and the deliberately unresolved repository
conflict remain open.

## Runtime reconciliation and specialist fixtures — 2026-11-11

[Runtime discovery](docs/ui-fidelity/RUNTIME-RECONCILIATION.md) now combines 2,765 registrations
from Code Browser, Front End, Debugger, Version Tracking and two displayed dialogs. Implementation
and registering-plugin links are separate; every configured menu/popup/toolbar route and shortcut
is retained. Module coverage distinguishes 108 active roots from per-tool observations and the
29 source configurations absent from this runtime. Four unresolved implementation classes belong
to the external harness. Anonymous handlers, service owners and dynamic content remain open.

[Specialist preparation](docs/ui-fidelity/FIXTURES.md#specialist-datasets-and-repository-workflow)
now generates related comparison programs, a saved VT session with accepted/rejected/ambiguous
matches, an H2 BSim database with ten signatures, and a saved debugger trace with 21 snapshots and
two threads. A loopback repository fixture creates password-authenticated Alice/Bob projects,
concurrent checkouts and actual common-ancestor/latest/private conflicts in bytes, function names
and comments. Bob's reopened checkout is version 1 against repository version 2 with `canMerge=true`.
The server stops after preparation; certificates/trust and profiles remain scoped to the fixture.

Validation: four source-inventory and four runtime-reconciliation regression tests; fresh headless
fixture preparation plus cross-process reopen/query checks; and a real loopback repository workflow.
Reports, input/script hashes and remaining gaps are retained in
[runtime evidence](docs/ui-fidelity/references/runtime/) and
[specialist evidence](docs/ui-fidelity/references/specialists/). Fixtures are reproducible data,
not visual acceptance. The integration runner checks explicit reports because a headless script
failure can return process exit code zero. IDE inspections exclude these support scripts; Java
compilation and the executing headless/capture harness provide their validation instead.

Source fixture routing now recognizes graph, merge/repository and version-control surfaces, and
VersionTrackingBSim uses the VT fixture. Inventory acceptance statuses are unchanged. Optional
installations, source-only candidates, visual/interaction acceptance of the populated specialist
surfaces, live Windows debugger agents, failure paths and Windows acceptance remain open. Milestones 0 and 1 are still incomplete; the
location-context spike and production rollout flag have not changed in this slice.

## IDE diagnostics cleanup — 2026-11-11

Fixed the generated SVG's missing gradient-stop offset in both the generator and its output.
The touched Java files now pass IntelliJ inspection with zero errors and warnings, without
disabling inspections. Cleanup preserves action registration order and retains/removes the
Decompiler service listener explicitly because the service manager stores weak references.

The IDE build and 24 navigation/history/contrast tests pass. All 344 regenerated glyph PNGs are
byte-for-byte unchanged. [Inspection and regression evidence](docs/ui-fidelity/references/ide-diagnostics.json)
records the scope; the existing reference PNGs and capture hashes remain the original visual baseline.

## Graphite reference and location-context spike — 2026-11-11

The [reference pack and coverage workbench](docs/ui-fidelity/README.md) now contain:

- A distinct **Graphite Preview** theme, original amber glyph variants and their 86 packaged
  fallbacks, a token/state sheet generated from the Java palette, and live Code Browser,
  Front End, actual options form, dense Symbol Table and nonempty Function Graph references.
- A repeatable source inventory with 10,243 candidates across 132 module/build roots, including
  optional first-party modules. Every candidate records ownership, fixture/entry discovery,
  rendering route, states, evidence fields and blockers. These are discovery candidates, not a
  count of accepted windows. Source fingerprints invalidate stale reviews.
- A captured runtime registration list (126 plugins, 33 providers, 958 actions) and source-class
  links. Three registrations belong to the external test harness; all still require per-surface/state review.
- An opt-in location-context spike (`-Dghidra.ui.locationContext=true`) for Listing and Decompiler:
  provider-local locations, function/address navigation, existing back/forward history, pending,
  unavailable, out-of-sync and independent states. Refresh lock is explicitly distinguished
  from independent location following. No new saved-tool fields or shortcut bindings are added.
- Original deterministic core/graph fixtures and a specialist fixture/blocker plan.

Validation: Gui/Base/Decompiler jars build; seven headed integration checks pass, and syntax/graph
contrast passes across all five existing themes plus Graphite Preview. The 25 token contrast checks
and three inventory regression fixtures pass. The live probe switches to stock and back, saves a
theme copy and records its properties/icon paths. Reviewed screenshots, test XML, jar hashes and
probe output are retained under `docs/ui-fidelity/references`; raw runs are under
`build/verification/graphite-reference-reviewed-v2`.

This is a development reference and spike, not milestone or release acceptance. Full runtime
inventory reconciliation, specialist fixtures, namespace navigation, production event/performance
design, all navigation edge cases, physical DPI/accessibility and process-restart acceptance remain
open. New-profile default selection is deferred; existing theme names and choices are preserved.
The stronger gradient/state specification is not yet implemented across every custom widget.
The bitmap fallback on saved-theme reload was subsequently addressed by the vector restoration
change above; live theme-editor and physical-DPI acceptance remain open.

## Shell pass — 2026-11-11

Implemented:

- Continuous subtle vertical gradients across dock titles and their action areas.
- Shaded application toolbars, with existing action groups and ordering preserved.
- Rounded hover/pressed/selected toolbar states, a selected-state underline, and
  a keyboard focus ring; disabled buttons do not retain hover fills.
- At least 28 logical pixels for standard toolbar button preferred dimensions.
- Shared UI font family at 13 pt for controls, menus, and data views.
- System color mappings for custom widgets and consistent read-only text surfaces,
  tree backgrounds, and alternating table rows.
- High Contrast keeps solid surfaces and its stronger focus ring. Saved theme copies
  retain the depth property. Stock rendering is selected dynamically on theme switch.

Validation: Gui/Docking jars build; all four EmptyBorderButton tests pass, including
painted hover/pressed/selected/disabled states and stock size/opacity restoration.
The isolated running-app probe checks dark/light/high-contrast/stock theme switching,
theme copying, stacking, splitting, detaching, redocking, closing/reopening, and layout
serialization. Screenshots and probe logs are in `build/verification/shell-polish*`.
The palette contrast script reports zero failures (`build/verification/shell-contrast.md`).
The final probe uses `shell-polish-verified.csv` and opens hostname and whoami sample
programs before checking the shell.

## Application-wide pass — 2026-11-11

Implemented after the shell pass:

- Project manager: shared section frames, clearer headings, labeled tool launchers,
  larger tool tiles, and guidance when no project is open.
- Dialogs: content padding replaces nested etched borders; action rows align to the
  trailing edge in Refresh themes. The shared provider covers settings, import,
  structure editors and other existing dialogs.
- Menus and tables: roomier menu rows, shortcut spacing and table-header padding;
  tab overflow no longer draws a raised bevel on hover.
- Listing and Decompiler: coordinated colors for keywords, constants, types,
  parameters, registers, references, comments, errors and search highlights.
- Graphs: readable semantic node tints, coordinated edge/path colors, minimap
  colors, and shared modern borders for normal and grouped Function Graph cards.
- Byte viewer and debugger: themed cursor/edit/stale/changed states and consistent
  text/header fonts. Existing state meanings and actions are retained.
- Action chooser: placeholder and keyboard guidance; searches can combine words
  from an action name, shortcut and plugin owner. Disabled actions remain disabled.
- Density: **UI Refresh Dark Compact** and **UI Refresh Light Compact** offer
  smaller controls and 18 px data rows. Standard Refresh themes use 22 px rows.
  Density and High Contrast focus geometry now survive saving a theme copy.
- Additional vector mappings for graph, project and dialog actions, using the
  existing original glyphs and their packaged fallbacks.
- **Classic Enhanced** and **Reverse Engineering** tool templates use the standard
  saved-tool format. They preserve all 28 stock provider placements and leave
  existing saved tools intact. Import them through Tools > Import Default Tools
  if an existing profile does not already show them in the Tool Chest.

The remaining table distinguishes follow-up workflow features from acceptance work;
neither is a claim that the entire original roadmap has shipped.

## Follow-up and acceptance

| Scope | Remaining |
| --- | --- |
| Workflow extensions (UI-018–022) | Ranked/recent-action search, clickable breadcrumbs, a dedicated program switcher and redesigned notifications |
| Component-specific refinements | Deep redesign of individual import/settings/structure forms, new graph zoom UI, custom progress/job UI and additional debugger timeline treatments |
| Icon coverage | Branded tool artwork, plugin-owned raster icons and specialized glyphs remain; icons are not blindly replaced with generic symbols |
| Acceptance (UI-028–035) | Real mouse docking, full process restart with stacked layouts, physical mixed-DPI/multi-monitor tests, additional third-party extensions, Linux/macOS checks, performance benchmarking and screen-reader testing |

Live verification captures the Front End, chooser, options dialog, Listing/Decompiler,
Bytes and Function Graph under standard, compact, high-contrast and stock themes.
The initial run (`build/verification/ui-completion.csv`) checks saved density/contrast
copies and the dock regression sequence. It also exposed an old Generic jar in the
previous test installation; the final run must use the newly packaged distribution.

`UiRefreshContrastTest` checks actual syntax and graph-label contrast in all five
themes. `ActionsDialogTest` checks multiword/owner search, disabled-action behavior
and stock filtering. Existing toolbar interaction tests continue to pass.

## Packaged verification

Build: `build/dist/ghidra_12.3_DEV_20261111.zip` (the current repository version is
12.3 DEV). Extract and run `ghidraRun.bat`; select a Refresh theme through
**Edit > Theme > Switch...**. A SHA-256 sidecar accompanies the ZIP.

- Full `buildGhidra` passes (`build/verification/ui-release-final.log`).
- Fifteen focused tests pass: syntax/graph contrast in all five themes, action chooser
  filtering/activation, and toolbar button states and stock restoration.
- The packaged app opens the sample program and renders Front End, code views,
  Bytes, Function Graph, chooser and Tool Options. Live theme changes, saved compact
  and High Contrast copies, and the dock/layout regression pass are recorded in
  `build/verification/ui-release-verified.csv`.
- Both workspace templates load from the packaged resources with Listing and
  Decompiler each at least 500 logical pixels wide. Correct divider ratios and the
  saved layouts are checked in `build/verification/ui-presets-final.csv` and its PNG/XML
  companions. Divider positions use thousandths of the split, as required by Ghidra.
- Java UI scaling at 150% is exercised separately. Test windows are fitted to the
  display before capture; this is not physical mixed-monitor DPI validation.
  Dark, Light and High Contrast view/dialog checks pass in
  `build/verification/ui-scale150-fitted.csv`; the screen capture is
  `ui-scale150-fitted.csv.screen-dark150.png` in the same directory.

Build notes: two pre-existing Javadoc errors were corrected (a stale parameter name
and a link to a nonexistent Library method). One repeat build encountered an
intermittent MIPS Sleigh compiler exception; its isolated retry and subsequent full
builds pass. The analysis engine was not changed.

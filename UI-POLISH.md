# UI polish progress

The target is a cohesive IDE-quality interface: consistent spacing, typography, icons,
depth, gradients, and interaction states throughout Ghidra. Themes and dock borders
are the foundation; they do not complete the polish work.

The original roadmap and evidence live in `E:\ghidra-ui-refresh\docs`.
The owner's future-work decisions and milestone plan are in [UI-FIDELITY-PLAN.md](UI-FIDELITY-PLAN.md).

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

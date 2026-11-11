# UI polish progress

The target is a cohesive IDE-quality interface: consistent spacing, typography, icons,
depth, gradients, and interaction states throughout Ghidra. Themes and dock borders
are the foundation; they do not complete the polish work.

The original roadmap and evidence live in `E:\ghidra-ui-refresh\docs`.

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

## Remaining work

| Scope | Remaining |
| --- | --- |
| Shell (UI-011–014) | Toolbar group composition/menu organization, tab overflow/close/pin treatment, drop previews, progress/jobs/messages, popup/dialog elevation |
| Project manager (UI-015) | Tool Chest, project groups, onboarding and empty states |
| Workspaces (UI-016–017) | Classic Enhanced and Reverse Engineering presets; restart restoration |
| Navigation (UI-018–022) | Ctrl+3 chooser, history/breadcrumbs, symbols/results, context menus and notifications, program switching |
| Dense views (UI-023–027) | Graph cards/edges/satellite/zoom, Listing/Decompiler rhythm, byte viewer, table/tree filters and headers, dialogs, debugger and console details |
| Foundation coverage (UI-008–009, UI-037–041) | Density presets, remaining raster/custom icons, popup shadows, frame consistency and component-by-component typography review |
| Acceptance (UI-028–035) | Real mouse docking, restart with stacked layouts, actual mixed-DPI/multi-monitor tests, third-party extensions, cross-platform checks, performance/accessibility, packaging |

This pass improves shared shell painting. It does not establish visual acceptance for
every provider or complete the application-wide roadmap.

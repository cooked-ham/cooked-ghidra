# UI fidelity and IDE experience plan

Status: planning baseline from the owner's decisions on 2026-11-11.
This describes future work; implementation evidence belongs in `UI-POLISH.md`.

## Confirmed direction

| Decision | Requirement |
| --- | --- |
| Visual identity | Graphite with amber, copper, warm highlights, stronger gradients and visible depth |
| Scope | Everyday workspace, supporting UI and graph workspace |
| Product direction | Full IDE experience, including navigation, search, program switching and notifications |
| First functional priority | Location context: breadcrumbs, history, and clear Listing/Decompiler synchronization |
| Platforms | Windows PCs and macOS are equal targets; shared portable Java/Swing implementation |
| Themes | Graphite dark first; other themes in later milestones |
| Defaults | Modern defaults for new profiles; preserve existing shortcuts and saved layouts |
| First release target | Exhaustive first-party coverage, including rarely used dialogs and specialist tools |

The existing Refresh themes and shell changes are a foundation. They are not visual
acceptance for this new identity or completion of the exhaustive coverage target.

## Design rules proposed for implementation

- Put expressive gradients and depth in chrome: headers, tabs, toolbar groups,
  popup surfaces, dialogs and graph cards. Keep code, hex and data backgrounds quiet.
- Give surfaces distinct roles: application canvas, content, raised controls and overlays.
  Define warm neutrals rather than tinting every surface orange.
- Define separate tokens for navigation accent, keyboard focus, selection, warnings,
  errors and changed data. Amber branding must remain distinguishable from warnings.
- Use a consistent radius, spacing, typography, border and elevation scale. Specify
  compact and comfortable density rather than accumulating local exceptions.
- Preserve semantic colors where distinctions carry analysis meaning. Use text, icons,
  outlines or patterns as additional state cues.
- Retain keyboard access, readable contrast and reduced-motion behavior in the dark
  flagship. Deferring a dedicated High Contrast theme does not defer basic accessibility.
- Build original artwork. Keep an explicit inventory of brand marks and specialized
  symbols so they retain meaning when redrawn.

Exact palette values, gradient stops, type sizes and shadow strengths remain design
choices to validate with representative screens, not decisions already made by the owner.

## Milestone 0 — Establish the visual reference and coverage inventory

Deliverables:

- A graphite/amber/copper token sheet with active, inactive, hover, pressed, focused,
  selected, disabled, error and warning treatments.
- Reference compositions for the Code Browser, Front End, an options dialog,
  dense table/tree view and Function Graph. Include real content and long labels.
- A complete first-party surface inventory combining shipped plugin/module lists,
  menus, providers, dialogs, custom renderers and help workflows. Include optional
  first-party modules; do not rely on a small screenshot sample as the inventory.
- A fixture plan for specialist tools, including binaries, projects, repositories,
  debugger targets and services needed to exercise their UI.

Exit gate: every inventoried surface has an owner/module, implementation route,
required fixture and acceptance entry. Unknown coverage is visible in the tracker.

## Milestone 1 — Location context and everyday shell

Deliverables:

- A location breadcrumb showing active program, namespace/function and address or
  function offset. Define an explicit display for undefined code and missing context.
- Navigable breadcrumb segments and discoverable back/forward history using Ghidra's
  existing navigation system and shortcut bindings.
- Clear Listing/Decompiler linkage states: following, locked/independent, pending,
  unavailable and out of sync. Labels and icons explain the state without color alone.
- Matching headers, tabs, toolbar groups, overflow controls, docking previews and
  status surfaces using the new visual reference.
- Clear active-program, active-pane and keyboard-focus distinctions.

Acceptance:

- The displayed program/address always belongs to the represented view. A locked or
  cloned provider must not silently claim to follow another provider.
- Back/forward navigation restores the correct existing history location; refreshing
  breadcrumbs does not create duplicate history entries.
- Exercise cross-program jumps, external references, undefined code, address spaces
  and overlays, program close/reopen, detached panes and independent clones.
- Existing shortcuts, action identities, selections and saved layouts remain usable.
- Context updates remain responsive during navigation and decompilation; stale or
  pending information is visibly identified.

## Milestone 2 — Rendering infrastructure and dense views

Deliverables:

- Reliable vector icon persistence across built-in, copied, saved and exported themes.
  Bundled-path vector restoration and live API/restart checks are implemented (see
  `UI-POLISH.md`); editor interaction and physical-DPI acceptance remain open.
- A full icon coverage report with direct loads, composite icons, disabled variants,
  specialized symbols and remaining raster artwork accounted for.
- Listing/Decompiler gutters, line emphasis, code typography, cross-highlights and
  reference states refined together; byte-viewer headers and edit states included.
- Consistent table/tree headers, filters, rows, selection, focus, sort cues and empty,
  loading and error states. Review custom renderers individually.
- Shared primitives for sections, toolbars, forms, feedback and elevation, plus a
  documented way for first-party widgets to adopt them.

Acceptance: aligned code and hex columns, no truncated essential controls, stable
keyboard behavior, and legible semantic states at supported Windows and macOS scales. Compare
scrolling, filtering, repaint and allocation measurements with recorded baselines.

## Milestone 3 — Supporting UI and exhaustive specialist coverage

Deliverables:

- Front End, project creation/opening, Tool Chest, workspaces, import/export and empty
  states designed as a coherent application entry experience.
- Individual form redesigns for settings, import, structure/data-type editors and
  other dialogs: grouping, alignment, validation, explanations and button hierarchy.
- Coverage of debugger panes and timelines, script management and consoles, program
  comparison/diff, merge and version tracking, BSim, extension management, repository
  and collaboration dialogs, and the remaining inventory entries.
- Window/dialog behavior for long text, large fonts, small displays, progress,
  cancellation, failures and disconnected services.

Exit gate: every first-party inventory entry has reviewed evidence or remains an
explicit release blocker. A shared border/theme change alone does not mark custom
content as reviewed. Missing fixtures require a fixture task, not an automatic exemption.

## Milestone 4 — Graph workspace

Deliverables:

- Purposeful card headers, group treatment, selection outlines/halos and restrained
  shadows, consistent with the graphite identity.
- Readable edge colors, paths, direction markers and labels at useful zoom levels.
- Deliberate minimap/viewport framing and discoverable zoom, fit and reset controls.
- Consistent treatment across function, call, data and other first-party graph views.

Acceptance: inspect selected, grouped, hovered and focused states; small and large
graphs; light/dark code annotations within the dark theme; zoomed and detached views.
Graph layout, navigation and rendering performance must retain functional parity.

## Milestone 5 — Search, program switching and feedback

Deliverables:

- An integrated search entry point for actions, symbols, files and open programs,
  with contextual ranking, recent results, clear result types and enabled states.
- Program switching and tab management for many open programs, including pinning,
  overflow, clear names and tooltips, and discoverable switching commands.
- Consistent background jobs, analysis progress, notifications and error details.
  Interruptions should reflect whether user action is actually required.
- A review of command discovery and shortcut conflicts across the complete workflow.

Acceptance: test ambiguous names, disabled/plugin actions, closed programs, cancelled
searches/jobs and concurrent background activity. New affordances must preserve
existing shortcuts and the correctness of navigation targets.

## Milestone 6 — Shared application release acceptance

- Close the complete surface/state matrix, including specialist fixtures.
- Verify Windows scaling and macOS Retina/scaled displays, mixed-scale monitor moves,
  ultrawide displays, smaller screens, docking and detached windows as relevant to the change.
  Portable UI changes do not require duplicate runs on both operating systems.
- Test complete process restart, saved tools, workspace presets, saved themes and
  copied themes. Validate both an existing profile and a genuinely fresh profile.
- Select graphite dark and the modern workspace defaults only for fresh profiles.
  Do not silently rewrite existing theme choices, shortcuts or saved layouts.
  Give the graphite identity a distinct theme entry so an existing theme choice is
  not silently repurposed as the new palette.
- Run golden paths for project creation, import, analysis, navigation, editing,
  save/reopen, graphs, debugger and specialist tools.
- Verify keyboard traversal, focus visibility, screen-reader behavior, contrast,
  reduced motion and measurable performance against the baseline.
- Smoke-test representative third-party providers and document the limits of styling
  plugin-owned renderers and assets.
- Produce a reproducible package, screenshot gallery, installation/upgrade/rollback
  instructions, known limitations and an inventory-backed completion report.

Exit gate: no unresolved first-party visual coverage entries, blocking usability
defects, data/navigation regressions or unreviewed changes to existing profiles.

Intermediate builds for either target can be reviewed throughout the milestones. The exhaustive
release label is reserved for the completed coverage and acceptance gates.

## Later milestones

Existing themes remain available; this sequencing concerns the new graphite identity.

- Matching Light and dedicated High Contrast themes, validated against the same
  semantic tokens, state coverage and workflows.
- Linux validation beyond the shared Windows/macOS release target.

## Coverage tracker requirements

Each entry records: stable ID; module and surface; entry path; fixture; standard/custom/
native rendering route; required states; implementation status; screenshots; interaction
checks; scaling/accessibility checks; performance relevance; and remaining blockers.

Use distinct statuses: inventoried, designed, implemented, visually reviewed,
functionally verified and accepted. Missing evidence cannot be represented as complete.

## Constraints and execution rules

- Implement UI features once using portable Java/Swing APIs. Do not add Windows-only product
  features or dependencies. Respect existing platform keyboard/menu/window conventions through
  Ghidra's abstractions. Validate shared UI behavior in the available environment; testing every
  portable change on both operating systems is not required. Use targeted platform checks only
  when a change depends on OS-specific behavior.
- Existing PowerShell runners and local drive paths are development harness infrastructure,
  not product runtime dependencies. Keep future verification tooling portable where practical.
- Standard Swing controls can adopt shared styling. Bespoke providers and renderers
  need targeted changes; exhaustive coverage requires that work to be explicitly tracked.
- Preserve large-view virtualization and keep expensive work off the UI thread.
- Native OS windows and external applications fall outside first-party drawing control.
  Define intentional integrations and platform fallbacks for them.
- Keep analysis models, project formats and docking serialization compatible.
- Maintain the stock-theme path and test switching in both directions.
- Use small reviewable changes, with runtime fixtures and before/after evidence for
  custom painting. Keep implementation and acceptance status separate.
- Establish estimates after the inventory and location-context spike. There is no
  agreed deadline or effort budget, and the exhaustive target needs discovery before
  a credible release date can be committed.

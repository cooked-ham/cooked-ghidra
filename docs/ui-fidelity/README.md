# Graphite UI fidelity workbench

This is the first implementation slice of [the agreed plan](../../UI-FIDELITY-PLAN.md).
Graphite Preview is a separate, opt-in theme. Location context is a development spike,
enabled with `-Dghidra.ui.locationContext=true` at process startup. Existing theme choices,
shortcuts, tool identities and layout serialization are not migrated by this change.

Windows and macOS are equal product targets, sharing portable Java/Swing UI code. Current
captures were collected on Windows; portable changes do not require duplicate macOS runs.
Use targeted platform checks when OS-specific behavior is involved. PowerShell runners
are development tooling and must not become runtime requirements for the shared application.

## Review the reference pack

- [Current remaining-checks report](references/remaining-gates/README.md): Merge/Repository,
  large graphs, icon dispositions, Windows logical scaling/accessibility, performance and runtime handlers.
- [Token and state sheet](references/tokens.svg): generated from `GraphiteTheme.java`.
- [Contrast report](references/contrast.md): primary/secondary text, primary action,
  semantic feedback, focus and control outlines.
- [Live reference gallery](references/README.md): actual Swing output with deterministic
  fixture content; distinguish the current implementation from the state/gradient specification.
- [Location-context findings](LOCATION-CONTEXT-SPIKE.md): behavior, validation and open work.
- [Fixture plan](FIXTURES.md): preparation, availability and explicit specialist blockers.

The preview adopts the existing Refresh rendering infrastructure. The token sheet specifies
the desired stronger copper/graphite gradient, spacing and states. It does not claim that every
custom header, icon, dialog or graph card already implements that specification.

## Coverage ledger

[surfaces.csv](surfaces.csv) is the generated discovery and acceptance tracker;
[modules.csv](modules.csv) lists every source module/build root, including optional first-party
modules and developer integrations. [inventory-summary.json](inventory-summary.json) records
the scan counts and unresolved reconciliation gate.

Each candidate has a stable ID, owning module, source location/fingerprint, entry-path evidence,
fixture ID, rendering route, required states, implementation status, screenshot/check fields,
scaling/accessibility requirements, performance relevance and blockers. The module is the initial
ownership boundary; a named reviewer still needs assignment during runtime reconciliation.

Discovery combines Java inheritance (including named nested classes), Swing and SWT/JFace
helpers, providers, dialogs, renderers, editors, plugins, actions, entry/menu/toolbar/help hooks,
help pages, script workflows, artwork, theme mappings, tool templates and debugger launchers.
Module discovery includes `Module.manifest` and `build.gradle` roots. The longest matching
module owns a source. Generated outputs, tests, downloaded dependencies and the local graft
cache are excluded. Optional modules remain in scope even when absent from a particular tool.

[Runtime reconciliation progress](RUNTIME-RECONCILIATION.md) records 2,765 registrations across
Code Browser, Front End, Debugger, Version Tracking and two actual dialogs. The
[registration ledger](runtime-reconciliation.csv) keeps source implementation and loaded-plugin
owner links separate. [Module coverage](runtime-module-coverage.csv) also distinguishes the 108
active module roots from per-tool registration evidence and absent optional/build configurations.
These snapshots are separate from visual/functional acceptance; anonymous handlers, dynamic menus
and service-owned surfaces still need tracing.

This is an over-inclusive source ledger, **not a count of distinct windows or a claim of complete
runtime coverage**. Simple-name inheritance can create false positives. Anonymous widgets,
dynamic menus, generated forms, reflection, service-owned windows and native integrations need
runtime reconciliation. A shared theme change is not evidence that their content was reviewed.
Every source candidate starts blocked until its real entry path and fixture/state coverage are
confirmed. Unmatched or obsolete review IDs are reported; changed source invalidates its review.

To regenerate or check:

```powershell
& 'C:\Python315\python.exe' support\ui\inventory.py
& 'C:\Python315\python.exe' support\ui\inventory.py --check
& 'C:\Python315\python.exe' support\ui\reference_sheet.py
& 'C:\Python315\python.exe' support\ui\test_inventory.py
& 'C:\Python315\python.exe' support\ui\test_reconcile_runtime.py
$captures = Get-ChildItem docs/ui-fidelity/references/runtime -Filter '*.runtime.csv' |
    Select-Object -ExpandProperty FullName
& 'C:\Python315\python.exe' support/ui/reconcile_runtime.py @captures `
    --installed-modules docs/ui-fidelity/references/runtime/capture.csv.modules.csv
```

Keep review changes in [reviews.csv](reviews.csv), keyed by `id` and `source_hash`, rather than
editing generated rows. Allowed statuses are `inventoried`, `designed`, `implemented`,
`visually reviewed`, `functionally verified` and `accepted`. Acceptance requires screenshot,
interaction and scaling/accessibility evidence and an empty blocker field. Review notes must
describe the exact exercised states; passing a subset of checks does not remove remaining blockers.

## Gates still open

1. Reconcile candidates against installed/shipped plugins, every menu/action/provider, modal and
   nonmodal dialogs, help workflows and optional first-party installations. Give dynamic/anonymous
   surfaces their own review entries, and record source-only false positives explicitly.
2. Exercise the prepared specialist datasets and repository-backed conflict; assign actual entry
   paths and reviewers to each surface. The main Code Browser comparison/diff/BSim, Version
   Tracking and Debugger providers now have [populated capture](references/specialists/populated/),
   but every specialist dialog/state and the repository-backed conflict still need review. Live
   Windows debugging and specialist failure cases remain fixture gaps; available data is not a
   claim that every specialist dialog has been reviewed.
3. Review the remaining location-context cases and performance baselines in the spike report.
   Polling has been replaced by lifecycle-managed events, with 36 integration regressions
   and an explicit-save exit/relaunch check; see [acceptance evidence](LOCATION-CONTEXT-ACCEPTANCE.md);
   production rollout remains opt-in pending the remaining acceptance work.
4. Review physical DPI, keyboard traversal, screen-reader output, performance, process restart,
   detached windows and preservation of existing profiles. Fresh-profile default migration is later work.

Milestone 0's exhaustive inventory gate and milestone 1's full navigation acceptance remain open.
Candidate volume is useful for batching discovery, but it does not justify a release-date estimate.
Estimate concrete surface families after runtime reconciliation and specialist fixture preparation;
record unknown fixture effort separately from implementation and acceptance effort.

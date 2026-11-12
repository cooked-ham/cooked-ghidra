# Runtime reconciliation progress — 2026-11-11

The expanded [registration ledger](runtime-reconciliation.csv) combines Code Browser, Front End,
the shipped Debugger and Version Tracking tool templates, and actual Navigation options and action
chooser dialogs. [Raw snapshots and capture provenance](references/runtime/) are retained separately
from acceptance. The debugger and VT registration captures instantiate their default tools without
opening specialist data; the specialist datasets below were prepared and verified separately.

| Evidence | Observed result |
| --- | --- |
| Runtime entries | 2,765: 302 plugin registrations, 85 providers, 2,376 actions, two dialogs |
| Exact source declarations | 1,808 |
| Enclosing source-file links | 953; anonymous/local implementations still need handler tracing |
| External registrations | Four: the probe plugin, its two synthetic providers, and the Front End launcher plugin |
| Registration owners | 2,235 linked to loaded source plugins; six external-plugin links; 524 service/infrastructure owners need tracing |
| Active runtime module roots | 108, including the external harness |
| Source module/build roots | 132; 23 have registration evidence; 109 have none in these captures |
| Source roots absent from this installation | 29; optional extensions and developer/build configurations remain in scope |

Counts include registrations shared by several tools; they are not counts of unique windows.
[Module coverage](runtime-module-coverage.csv) distinguishes an installed module from one with
observed registrations. A module with no UI candidates still needs its classification checked;
absence from the installation does not exempt an optional first-party surface.

Class and owner links have different meanings. For example, an `ActionBuilder` implementation can
belong to Docking while its action is registered by a Base plugin. The ledger retains both links
and the owning fixture families; neither substitutes for tracing the actual handler/dialog.
Menu, popup and toolbar routes are retained together, with configured shortcut bindings. Capturing
menu metadata does not invoke actions or enumerate menus created lazily for a particular context.

The [unresolved implementation list](runtime-unresolved.csv) currently contains only the four
external harness registrations. This does **not** resolve the anonymous handlers, service owners,
dynamic menus, dialogs or source-only candidates. No acceptance status was promoted in this pass.

## Repeat the capture and reconciliation

```powershell
.\support\ui\capture-reference.ps1 -Tag graphite-runtime-review -Specialists
# Copy the six *.runtime.csv files, capture.csv.modules.csv, capture.csv and manifest
# from that isolated run to a new evidence directory before reconciling it.
$captures = Get-ChildItem docs/ui-fidelity/references/runtime -Filter '*.runtime.csv' |
    Select-Object -ExpandProperty FullName
& 'C:\Python315\python.exe' support/ui/reconcile_runtime.py @captures `
    --installed-modules docs/ui-fidelity/references/runtime/capture.csv.modules.csv
& 'C:\Python315\python.exe' support/ui/test_reconcile_runtime.py
```

The reconciliation summary hashes the input snapshots, source ledger and module ledger. Capture
filenames identify tools/contexts and must be unique. Owner resolution is local to each snapshot;
a plugin loaded in another tool cannot silently resolve a registration's owner.

## Specialist preparation

[Fixture preparation and entry paths](FIXTURES.md#specialist-datasets-and-repository-workflow)
now cover comparison programs, a saved VT session, a local populated H2 BSim database, a saved
debugger trace, and repository-backed shared projects with an unresolved three-way merge.
[Preparation evidence](references/specialists/) records expected results and script hashes.
Generated projects/databases and test certificates remain under `build/verification` and can be
recreated; no external executable, Windows service installation or production repository is used.

## Next discovery acceptance work

1. Open the prepared trace, session, database and shared projects in their actual tools; record
   individual providers, modal/nonmodal dialogs, dynamic menus and populated/error/empty states.
2. Trace the 953 enclosing-file links and service/infrastructure owners to actual handlers and
   entry hooks. Review source-only candidates and record false positives with source evidence.
3. Build/install the missing optional first-party configurations and capture them with their
   dependencies. Confirm shipping scope rather than inferring it from the current installation.
4. Assign reviewers and per-surface fixtures/entry paths, then collect interaction, physical-DPI,
   accessibility and performance evidence. Keep changed source and untested states blocked.
5. Complete live Windows debugger/agent preparation and specialist failure cases before declaring
   fixture coverage complete. The saved synthetic trace does not certify a live Windows target.

Milestone 0 remains open. Location-context production work follows this discovery/fixture work;
the existing spike, feature flag and seven recorded integration checks are unchanged.

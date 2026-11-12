# UI fixture plan

Fixture IDs in the coverage ledger refer to these families. Each runtime review must record its
exact dataset, entry path and exercised states. Sharing a fixture family does not exempt individual
providers, dialogs or custom renderers from review.

| ID / owner | Required content | Availability and release blockers |
| --- | --- | --- |
| FX-CORE / Base, Project, Docking, Gui | Empty project, two open programs, namespaces, functions, undefined bytes, strings, long names, settings and script output | Deterministic x86-64 generator and real Listing/Decompiler/history integration fixture available. Import/export variants, structure editor fixtures, malformed input, external references, overlays and all modal paths still need individual reviews. |
| FX-GRAPH / graph modules | Conditional branches, shared exit, grouped/selected cards, edge labels, minimap; small and large function/call/data graphs | Four-block function generated with FX-CORE. Large graphs, grouping, graph-specific custom labels, every graph implementation and performance baselines remain blockers. Graph candidates initially inherit FX-CORE in the generated ledger; refine them to this fixture during reconciliation. |
| FX-DEBUG / Debugger, TraceModeling, TaintAnalysis, SystemEmulation | Windows dbgeng/x64dbg target, live/saved trace, threads, stack, registers, memory, modules, breakpoints, timeline, changed/stale values, disconnection and restart | Source training targets exist in `GhidraDocs/GhidraClass/ExerciseFiles/Debugger`. No debugger target/session is certified by this slice. Record debugger versions, agents, privileges, reproducible launch/attach steps and expected stop points; all specialist panes remain blocked. |
| FX-COMPARE / ProgramDiff, CodeCompare | Two related programs with renamed functions, signatures, changed bytes, types and unmatched functions | The generated core programs intentionally have identical bytes; they are insufficient comparison acceptance. Create controlled variant pairs and expected differences before review. |
| FX-VT / VersionTracking, VersionTrackingBSim | Source/destination pair, correlation session, accepted/rejected matches, ambiguous results, markup conflicts, interrupted session | Training material is available; no complete session/expected-result fixture is validated. Correlators, matching, markup dialogs and failure paths remain blockers. |
| FX-BSIM / BSim and visualizer modules | Populated function database, credentials/connection variants, duplicate/similar functions, query results and feature visualization | BSim training material exists. A known database with reproducible populated results, server/configuration details and offline/authentication/error cases is missing. Database-backed UI is blocked. |
| FX-REPO / GhidraServer and collaboration workflows | Local disposable repository, two users/checkouts, checkout conflict, version history, multiway merge, server failure and reconnect | No running server or multi-user merge fixture prepared. Project module candidates currently inherit FX-CORE; map repository/merge entries to this fixture during reconciliation. Merge and repository dialogs remain blocked. |
| FX-FORMATS / FileFormats, FileSystem | Archives/nested containers, supported binaries, malformed files, encrypted inputs, missing libraries, import/export settings | Shipped exercise binaries provide starting content. No format-by-format manifest or failure corpus is validated; first-party browser/import/export forms remain blocked. |
| FX-OPTIONAL / first-party Extensions and developer integrations | Each shipped optional plugin installed separately and in representative combinations; SWT/Eclipse integrations where shipped | Source inventory includes these modules. Build/install provenance, dependencies and per-extension runtime fixtures still need verification. Do not classify an absent plugin as visually accepted. |

Generated module fixture assignments are deliberately broad. Split them when a source exposes more
than one workflow (Base alone contains import, repository, merge, scripting and type editing).
This refinement is part of the open runtime inventory gate.

## Deterministic core and graph fixture

Preparation/capture scripts use PowerShell 7+, the checkout's JDK (25 in this run), and Python
(`C:\Python315\python.exe` by default). The distribution and seed profile paths are parameters.

`support/ui/prepare-fixture.ps1` writes two original 256-byte raw x86-64 fixtures, imports them at
`ram:00401000`, and runs `CreateUiFixture.java` to create instructions, function signatures, long
namespace/label content and a string. The main function compares its argument with 42 and returns
1 or 0 via a shared exit. A second function returns 42; 48 additional labels populate the actual
Symbol Table. The suffixes `hostname_x64.exe` and
`whoami_x64.exe` retain compatibility with the existing probe; these are synthetic raw bytes,
not Windows executables. No external executable is committed or required.

```powershell
.\support\ui\prepare-fixture.ps1
.\support\ui\capture-reference.ps1 -Tag graphite-reference-final
```

Defaults target the disposable distribution under `build/verification/release/ghidra_12.3_DEV`.
The capture requires the pre-existing UI Refresh probe extension and configured CodeBrowser in
`build/verification/profile`; its original source is under
`E:\ghidra-ui-refresh\extension\GhidraUiRefresh`. `adapt_probe.py` changes a copy to hide its synthetic
panes, open the actual Navigation options form and Symbol Table, and record runtime registrations;
the original source and seed profile are preserved. That harness dependency is explicit; this is
not a self-contained release acceptance runner. The capture copies the seed profile/project,
backs up and replaces three UI jars only in the disposable runtime, and records jar hashes.
It checks theme/view availability and saves screenshots. Its visibility checks cannot certify
meaningful content; inspect Listing, Decompiler and graph output before promoting any capture.

Core fixture SHA-256 (both raw files):
`34EE14997515E48ABE0B14EA34F1C0E19E766EE04EB051BC20384E8734E20CE8`.

# UI fixture plan

Fixture IDs in the coverage ledger refer to these families. Each runtime review must record its
exact dataset, entry path and exercised states. Sharing a fixture family does not exempt individual
providers, dialogs or custom renderers from review.

| ID / owner | Required content | Availability and release blockers |
| --- | --- | --- |
| FX-CORE / Base, Project, Docking, Gui | Empty project, two open programs, namespaces, functions, undefined bytes, strings, long names, settings and script output | Deterministic x86-64 generator and real Listing/Decompiler/history integration fixture available. Import/export variants, structure editor fixtures, malformed input, external references, overlays and all modal paths still need individual reviews. |
| FX-GRAPH / graph modules | Conditional branches, shared exit, grouped/selected cards, edge labels, minimap; small and large function/call/data graphs | Four-block function generated with FX-CORE. Graph modules and graph paths now route to this fixture. Large graphs, grouping, graph-specific custom labels, every graph implementation and performance baselines remain blockers. |
| FX-DEBUG / Debugger, TraceModeling, TaintAnalysis, SystemEmulation | Windows dbgeng/x64dbg target, live/saved trace, threads, stack, registers, memory, modules, breakpoints, timeline, changed/stale values, disconnection and restart | Saved synthetic x86-64 demo trace verified across processes: 21 snapshots, two threads, register/stack/memory changes. Live Windows target/agents, modules, breakpoints, disconnection and restart still need fixtures and reviews. |
| FX-COMPARE / ProgramDiff, CodeCompare | Two related programs with renamed functions, signatures, changed bytes, types and unmatched functions | Related source/destination programs prepared and reopened: changed compare immediate, function/parameter names, signed/unsigned parameter type, 8/12-byte structure and unmatched function addresses. Individual diff/comparison views and interactions remain unreviewed. |
| FX-VT / VersionTracking, VersionTrackingBSim | Source/destination pair, correlation session, accepted/rejected matches, ambiguous results, markup conflicts, interrupted session | Saved manual session verified after reopening: one accepted, one rejected and two ambiguous available matches. Accepted match has unapplied name/signature differences. Correlator accuracy, applied/failed/conflicting markup and interrupted-session states remain blockers. |
| FX-BSIM / BSim and visualizer modules | Populated function database, credentials/connection variants, duplicate/similar functions, query results and feature visualization | Local H2 database prepared with two programs/ten signatures; reopened source query returns 11 cross-program hits. No network service required for these data-backed reviews. Authentication/network/error cases and individual visualizer/query forms remain blockers. |
| FX-REPO / GhidraServer and collaboration workflows | Local disposable repository, two users/checkouts, checkout conflict, version history, multiway merge, server failure and reconnect | Loopback server workflow prepared and verified: two password-authenticated users, concurrent checkouts, version 2 checked in by Alice, Bob's modified version-1 checkout with canMerge=true. Byte/name/comment conflicts share an actual repository ancestor. Server stops after preparation; conflict resolution, failure/reconnect and UI acceptance remain open. |
| FX-FORMATS / FileFormats, FileSystem | Archives/nested containers, supported binaries, malformed files, encrypted inputs, missing libraries, import/export settings | Shipped exercise binaries provide starting content. No format-by-format manifest or failure corpus is validated; first-party browser/import/export forms remain blocked. |
| FX-OPTIONAL / first-party Extensions and developer integrations | Each shipped optional plugin installed separately and in representative combinations; SWT/Eclipse integrations where shipped | Source inventory includes these modules. Build/install provenance, dependencies and per-extension runtime fixtures still need verification. Do not classify an absent plugin as visually accepted. |

Generated fixture assignments now recognize graph modules, merge/repository/checkout paths and
version-control class names; VersionTrackingBSim uses FX-VT. Assignments remain discovery hints.
Split them when a source exposes multiple workflows and verify them during runtime review.

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

## Specialist datasets and repository workflow

Preparation uses the same JDK/disposable distribution as the core fixture. Choose a new directory;
the scripts refuse to overwrite an existing fixture. Use `-Repository` to include the real shared
projects and merge conflict; without it, the offline repository root is prepared without checkouts.

```powershell
.\support\ui\prepare-specialists.ps1 `
    -Output E:\cooked-ghidra\build\verification\graphite-specialists-review -Repository
```

The verified run is `build/verification/graphite-specialists-acceptance-inputs`.
Inspect [retained expected results and provenance](references/specialists/) before using it.
`fixture-manifest.json` records script hashes, source revision, dirty-tree state and remaining
fixture gaps. `inputs.json` records raw byte hashes and the source/adapted demo-trace hashes.
Each raw program is 1,024 original bytes, loaded at `ram:00401000` using x86-64/windows.
Projects and database files include generated metadata; reproducibility means the documented
content/assertions, not byte-identical project files or test certificates.

| Fixture entry | How to reach it | Expected content / limits |
| --- | --- | --- |
| `UiSpecialists.gpr`, `/binaries/source.bin` and `destination.bin` | Open both in Code Browser; Tools > View Program Differences; function comparison actions | Five functions each. Compare immediate 42/43 at 00401006; renamed classification function; signed/unsigned parameter; `LocationReviewRecord` at 00401200 is 8/12 bytes. Duplicate functions at 00401080/004010a0; unmatched function at 00401100/00401140. |
| `/binaries/UI Specialist VT` | Open the session from the Front End in Version Tracking | Manual matches: accepted 00401000 -> 00401000, rejected 00401040 -> 00401040, available ambiguous 00401080 -> 00401080 / 004010a0. These known states do not validate correlator accuracy. |
| `/UI Debugger Trace` | Open the saved trace with the Debugger tool | Adapted shipped `PopulateDemoTrace.java`, with only class name and final GUI-open block changed to headless save/verification. 21 snapshots and two threads; synthetic demonstration rather than a live Windows recording. |
| `ui-specialist-bsim.mv.db` | Code Browser > BSim > Manage Servers, add local H2 file; BSim > Search Functions / Perform Overview | Two executables, five signatures each; query source against populated database at similarity 0.7/significance 0.0. Eleven destination hits are verified across source result groups, including deliberately duplicate functions. |
| `Alice.gpr` / `Bob.gpr`, `/merge.bin` | Start fixture server as below; open Bob's project, connect as ui_bob; Front End program popup > Check In / Merge | Common ancestor version 1: immediate 42, source_classify, common comment. Alice checks in version 2: 44/latest_classify/latest comment. Bob retains version 1 with 45/private_classify/private comment. All three contents and two version-history entries are verified; actual merge remains unresolved for review. |

The three separately imported `merge-*.bin` files provide convenient comparison inputs. The
shared projects additionally provide the **actual versioned ancestor and concurrent checkout**;
independent imports alone are insufficient to exercise repository merge.

Preparation creates a fixture-specific PKCS12 certificate and exported PEM in the output directory.
Only the spawned client trusts that PEM via `ghidra.cacerts`; no OS trust setting is changed.
The fixture password for both repository users and the test keystore is `ui-fixture-only`.
The server binds `127.0.0.1` on base port 14150 and the next two ports, installs no Windows service,
and is stopped by the preparation runner's `finally` block. To review the shared projects:

```powershell
# Terminal 1: holds the loopback fixture server until Ctrl+C.
.\support\ui\fixture-repository.ps1 `
    -Fixture E:\cooked-ghidra\build\verification\graphite-specialists-acceptance-inputs

# Terminal 2: use a disposable profile, scoped trust and the shared Bob project.
$fixture = 'E:\cooked-ghidra\build\verification\graphite-specialists-acceptance-inputs'
$priorOptions = $env:GHIDRA_JAVA_OPTIONS
try {
    $env:GHIDRA_JAVA_OPTIONS = "-Dapplication.settingsdir=$fixture\review-profile -Dghidra.cacerts=$fixture\repository-fixture.pem"
    & build/verification/release/ghidra_12.3_DEV/ghidraRun.bat "$fixture\Bob.gpr"
} finally { $env:GHIDRA_JAVA_OPTIONS = $priorOptions }
```

Connect as `ui_bob` with the fixture password. Reviewing merge resolution consumes this scenario;
generate a fresh fixture directory to repeat it. Saved traces, H2 and shared projects are prepared
data, not specialist UI acceptance. Live dbgeng/x64dbg agents and full failed/cancelled/disconnected
state coverage remain open. Headless success is checked with explicit assertion reports/markers,
because script-loading errors can otherwise leave a zero process exit code.

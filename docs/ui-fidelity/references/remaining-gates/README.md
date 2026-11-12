# Remaining UI checks — 2026-11-12

This pass completed the concrete Merge/Repository review, a larger real Function Graph, icon
triage, Windows logical scaling/accessibility checks, stock/modern performance measurements and
captured runtime registration/handler reconciliation. Nothing was committed or pushed.

| Check               | Evidence and result                                                                                                                                                                                                            |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Repository          | [Selection](repository/repository-selection.png), [two users](repository/repository-access.png), [two history entries](repository/repository-history.png); actual loopback repository and concurrent checkout                  |
| Merge cancellation  | [Cancel confirmation](repository/merge-cancel.png); private byte/name/comment and checkout version 1 preserved                                                                                                                 |
| Merge resolution    | Actual [byte](repository/merge-conflict-2.png), [function](repository/merge-conflict-3.png) and [comment](repository/merge-conflict-4.png) conflict controls; Latest chosen, version 2 and all three resulting values verified |
| Connection states   | Explicit disconnect followed by successful reconnect as ui_bob; [assertion report](repository/repository-checks.txt)                                                                                                           |
| Large graph         | 128 conditional diamonds, 257 vertices, 384 edges; settled layout, fit, 100% detail and twelve selected vertices                                                                                                               |
| Graph captures      | [100% fit](scale-100/large-fit.png), [100% detail](scale-100/large-detail-selection.png), [200% fit](scale-200/large-fit.png), [200% detail](scale-200/large-detail-selection.png)                                             |
| Logical scaling     | Separate successful JVM runs at 100%, 125%, 150%, 200%; actual transforms checked in each `scale-*/acceptance-checks.txt`                                                                                                      |
| Swing accessibility | Four navigation controls have accessible names/button roles and are enabled/focusable; actual focused Space action-map bindings activate Zoom In; accessible trees retained at every scale                                     |
| Performance         | ABBA stock/modern runs, 64 samples per theme/workload/scale; [measurements and limitations](PERFORMANCE.md), raw CSVs under each scale directory                                                                               |
| Icons               | [543-reference audit](../icon-audit/README.md), no unresolved resources, every retained value has a disposition                                                                                                                |
| Runtime             | [Registration reconciliation](../../RUNTIME-RECONCILIATION.md): 15,649 entries, all 8,361 action handlers traced; zero unresolved registrations/handlers                                                                       |

The repository panel/access/history components and actual merge workflow were driven by the
test-only harness. This does not claim every page of the connection wizard was traversed. The
fixture is disposable, trusts only its own test certificate, and uses no production repository.
The owned server was stopped after review. Native input focus probing did not establish a physical
keyboard pass; the successful automated check uses Swing's focused action map. Extra physical-DPI,
screen-reader and macOS hardware acceptance was explicitly excluded by the owner.

Two product defects were fixed during this review: the large-graph minimap's opaque white viewport
now uses a translucent accent and outline in modern chrome, and Script Directories ignores queued
table events after disposal. The latter was reproduced by optional-plugin activation and has a
new integration regression test. The final runtime application log contains zero ERROR entries.

Modern graph median paint time was 10.3–12.3 ms across these runs, roughly 1–4 ms above stock;
modern graph p95 was at most 18.8 ms. Table scrolling/filtering was comparable or faster except
small differences at 125%. These are measured Swing paint costs on this host, not physical frame
latency. No release budget was supplied, so no arbitrary performance pass threshold is asserted.

Validation: full matching distribution build (835 tasks), seven matching extension packages,
Graph navigation/renderer tests, two contrast tests and twenty icon-value tests passed. Both the
new disposal regression and the existing script-directory enable/disable integration test passed
([JUnit](bundle-disposal-tests.xml)). Nine runtime and five icon-audit Python tests pass; source
inventory check and strict registration gate pass. IntelliJ compilation of the touched Base files
passes; IconSet and minimap inspections are clean. Existing unrelated Base inspection warnings
remain. This pass does not replace the handoff's broad-suite report of nine pre-existing failures.

The exhaustive per-source-surface visual release gate is still open. A successful registration
gate cannot establish interactions for thousands of unreviewed source candidates. The hardware
exclusions above are no longer acceptance requirements for this pass.

## Reproduce

Use the matching runtime procedure in [runtime reconciliation](../../RUNTIME-RECONCILIATION.md).
`runtime-build-manifest.json` records the distribution/profile preparation. Capture manifests retain
the exact product jar and harness hashes used; `SHA256SUMS.csv` hashes the retained evidence.
`current-tooling-sha256.csv` records the final helpers separately from capture-time manifests.

```powershell
# Use a new directory: merging consumes Bob's conflict scenario.
.\support\ui\prepare-specialists.ps1 -Output build/verification/ui-fixtures-next `
    -Distribution build/verification/coherent-ui-20261112/ghidra_12.2_DEV `
    -Repository -RepositoryPort 14250
# Keep fixture-repository.ps1 running separately for the repository surface.
.\support\ui\fixture-repository.ps1 -Fixture build/verification/ui-fixtures-next -Port 14250 `
    -Distribution build/verification/coherent-ui-20261112/ghidra_12.2_DEV
# In another terminal, pass that fresh Fixture and the same RepositoryPort.
.\support\ui\review-specialists.ps1 -Fixture build/verification/ui-fixtures-next `
    -Surfaces repository -RepositoryPort 14250 -Tag repository-next
foreach ($scale in @(1.0, 1.25, 1.5, 2.0)) {
    .\support\ui\review-specialists.ps1 -Surfaces acceptance -UiScale $scale `
        -Tag ("large-next-" + [int]($scale * 100))
}
# Supply the four new run directories to performance_report.py.
```

The capture harness seed remains an explicit external test dependency described in
[FIXTURES.md](../../FIXTURES.md). Current product code has no dependency on it.

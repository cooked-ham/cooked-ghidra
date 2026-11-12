# Runtime reconciliation — 2026-11-12

The captured registration gate passes with **zero unresolved first-party registrations or action
handlers**. The [ledger](runtime-reconciliation.csv), [summary](runtime-summary.json),
[module coverage](runtime-module-coverage.csv) and [raw evidence](references/remaining-gates/runtime/)
use the current source inventory and a matching 12.2 DEV installation.

| Evidence                                    | Result                                                                                       |
|---------------------------------------------|----------------------------------------------------------------------------------------------|
| Runtime entries across twelve contexts      | 15,649; repeated registrations are retained                                                  |
| Actions                                     | 8,361; 6,908 traced by class bytecode/source line, 1,453 by actual lambda target/source line |
| Other entries                               | 1,145 plugins, 316 providers, two dialogs, 5,011 components, 814 installed renderers         |
| Class links                                 | 9,216 exact declarations; 3,560 enclosing files with traced handlers                         |
| Scope classifications                       | 2,851 platform/third-party implementations; 22 explicit external harness entries             |
| Active module roots                         | 113; registration evidence in 30 source roots                                                |
| Optional extensions                         | Seven built/installed; all fourteen discovered plugins activated successfully                |
| Runtime application errors in final capture | Zero                                                                                         |

The former 953 weak anonymous/local implementation links now have actual handler locations. ASM
reads the loaded class debug information. For builder callbacks, the harness follows the callback
object and inspects the JDK's dumped lambda proxy to recover the exact host method and source line.
It does not invoke actions. Registration owner and implementation remain separate fields; generic
service labels do not become fictitious plugin owners. The 344 external loaded-owner links belong
to the explicitly scoped probe/launcher plugins. Nine reconciliation regression tests pass.

The contexts include Front End, Code Browser, shipped Debugger and Version Tracking templates,
actual chooser/options dialogs, and cumulative optional-extension activation. Components and
installed table/tree/list renderers are recorded separately. Menu routes and shortcuts are metadata;
context-sensitive menus and lazy dialogs are not exercised by collecting registrations.

The previous frozen runtime was version 12.3 while this checkout is version 12.2. It has been replaced
for current verification by a full `assembleAll` distribution plus matching extension packages.
Both capture runners now reject a version mismatch before launching; both rejection paths were
verified. The final capture has no queued Script Directories disposal exception after the Base fix.
Older captures remain historical evidence and are not used for this registration gate.

The broader exhaustive visual acceptance flag remains **false**. Registration reconciliation does
not certify every source candidate's dialogs, populated/error states or interactions. Of 132 source
module/build roots, 22 are absent from this runtime; the UI-bearing absent roots are the separate
Eclipse GhidraDev plugin, LaunchSupport and the extension Skeleton. The other absent roots have no
discovered UI candidates. Installed shared libraries and scripts also need source classification
beyond plugin activation. No source review was automatically promoted to accepted.

Physical mixed-DPI, macOS and screen-reader hardware checks are excluded at the owner's request.
Windows logical scaling, Swing accessibility checks, Merge/Repository and measured performance are
recorded in the [current acceptance report](references/remaining-gates/README.md).

## Repeat

```powershell
.\gradlew.bat assembleAll --offline --console=plain
.\gradlew.bat :Jython:zipExtensions :Lisa:zipExtensions :MachineLearning:zipExtensions `
    :SleighDevTools:zipExtensions :SymbolicSummaryZ3:zipExtensions `
    :SampleTablePlugin:zipExtensions :sample:zipExtensions --offline --console=plain
# prepare-review-runtime.ps1 copies the built distribution and seed profile into a fresh directory.
.\support\ui\prepare-review-runtime.ps1 -Tag coherent-review-next
# Pass the resulting Distribution and ProfileSeed paths when changing the default run directory.
.\support\ui\capture-reference.ps1 -Tag runtime-next -Specialists -TraceHandlers -OptionalPlugins `
    -Distribution E:\cooked-ghidra\build\verification\coherent-review-next\ghidra_12.2_DEV `
    -ProfileSeed E:\cooked-ghidra\build\verification\coherent-review-next\profile
$captures = Get-ChildItem docs/ui-fidelity/references/remaining-gates/runtime -Filter '*.runtime.csv' |
    Select-Object -ExpandProperty FullName
& 'C:\Python315\python.exe' support/ui/inventory.py --check
& 'C:\Python315\python.exe' support/ui/reconcile_runtime.py @captures `
    --installed-modules docs/ui-fidelity/references/remaining-gates/runtime/capture.csv.modules.csv `
    --require-registration-reconciled
& 'C:\Python315\python.exe' support/ui/test_reconcile_runtime.py
```

For a new capture, retain its raw snapshots/module list/activation list/manifest under a new evidence
directory and use those paths in reconciliation. Snapshot filenames must be unique. Summaries hash
all inputs and the source/module ledgers. Owner resolution is local to each snapshot.

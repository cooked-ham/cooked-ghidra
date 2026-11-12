# Source-only UI scope review — 2026-11-12

All fifty candidates in the three UI-bearing source roots absent from the primary application
module list have an explicit [classification, entry route, source fingerprint and evidence](classifications.csv).
This resolves their shipping/hosting role; it does not promote them wholesale to visual acceptance.

| Source root | Candidates | Classification and exercised evidence |
| --- | --- | --- |
| Eclipse GhidraDev | 44 | Separate SWT/JFace add-on; compiled unchanged from checkout source and reviewed in a private Eclipse RCP workbench |
| LaunchSupport | 1 | Bootstrap CLI/chooser, shipped as `support/LaunchSupport.jar` before application theme initialization; actual cancellation/CLI recovery checked |
| Skeleton | 5 | Source template deliberately excluded from core compilation; built as a real extension and its provider/local action reviewed |

The other absent module/build roots have no discovered UI candidates. LaunchSupport is not an
application plugin. Skeleton's source template is not expected in the primary runtime registry.
Eclipse's widgets are hosted by Eclipse, with the host theme; Swing styling is not imposed on it.
The separate template activation adds registration evidence without altering the primary runtime's
module-root snapshot.

## Eclipse evidence

[31 captured states](eclipse/eclipse-review.csv) cover six wizard classes and their pages,
the source-folder chooser, six Ghidra launch-configuration tabs, four preference pages and two
invalid-port states. Source entry paths come from the actual `plugin.xml` declarations and caller
references. Pages were selected programmatically in real wizard dialogs to review their controls;
this does not claim a successful project-generation/export workflow through every Next/Finish step.
All dialogs were cancelled/closed. Port validation checks the exact message, not merely an invalid
page flag. Script-editor and symbol-lookup listeners stayed disabled.

Representative [missing installation](eclipse/03-ChooseGhidraInstallationWizardPage.png),
[Gradle validation](eclipse/15-ConfigureGradleWizardPage.png),
[source selection](eclipse/19-PackageFragmentRootSelectionDialog.png),
[launch arguments](eclipse/21-GhidraLaunchTabGroup.png),
[script port validation](eclipse/29-GhidraScriptEditorPreferencePage.png),
[symbol port validation](eclipse/31-GhidraSymbolLookupPreferencePage.png).

[manifest.json](eclipse/manifest.json) records every GhidraDev source hash, bundled Utility/LaunchSupport
hashes and verified dependency downloads. The host is the official
[Eclipse RCP/RAP package](https://www.eclipse.org/downloads/packages/release/2026-09/r/eclipse-ide-rcp-and-rap-developers).
Its SHA-512 was verified; PyDev 9.3 and CDT 8.6 build dependencies match the repository's pinned
SHA-256 values. They are compilation dependencies here, not activated optional Eclipse extensions.
Launch configurations were inspected without starting a Ghidra/debug process. Live script editing,
symbol lookup, Python/CDT workflows and extension export execution are outside the recorded states.
Third-party Eclipse/Chromium shutdown diagnostics are retained in `application.err`; no first-party
GhidraDev exception is present in the final capture.

```powershell
.\support\ui\review-eclipse.ps1 -Tag eclipse-source-next
& 'C:\Python315\python.exe' support/ui/classify_source_scope.py
```

The runner verifies archives, compiles original source and creates a private installation/workspace.
It does not install Eclipse globally or enable GhidraDev listeners. The corrected bootstrap probe's
isolation procedure and initial saved-JDK incident are documented in the
[navigation follow-up report](../navigation-followup/README.md#probe-incident).

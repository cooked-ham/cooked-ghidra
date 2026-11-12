# Navigation and remaining checks — 2026-11-12

The prior UI work was already committed in `c0045a54a9` and `f41fd315e2` when this pass began.
That history was preserved. New product changes are split into `f1dffe5ecc` (Enter activation fix)
and `f9f9dc7d9e` (program switching and breadcrumb menu disambiguation). Nothing was pushed.

## Product behavior

Modern themes now use a dedicated **Switch Program** dialog through the existing
**Navigation > Go To Program** command and its existing shortcut (Ctrl+F7 on this host).
It searches program names, project names and paths using space-separated words; exact names rank
ahead of prefixes and substrings. Rows show project paths, current status and unsaved changes.
Filtering selects the first match, Enter works from both the input and result list, and Cancel
preserves the current program. The event-driven list follows open/close/rename events, excludes
background-only programs and rechecks membership before activation. Disposal releases listeners
and ignores queued refreshes. Stock themes retain their existing tab-list command.

[Duplicate names](navigation/program-switcher-duplicates.png), [filtered](navigation/program-switcher-filtered.png),
[modified](navigation/program-switcher-modified.png), [no match](navigation/program-switcher-no-match.png),
[empty tool](navigation/program-switcher-empty.png), [200% logical scale](scale-200/program-switcher-duplicates.png).

Clickable provider-local breadcrumbs were already implemented. This pass verified them and added
the switcher entry to their program menu. Program and history menus now show project paths when
names collide. Nested namespace traversal and real symbol navigation were exercised, with the
destination checked against the Listing's address. Independent views retain their program binding.
Breadcrumbs remain enabled through `-Dghidra.ui.locationContext=true`; production default rollout
was not changed.

[Program menu](navigation/breadcrumb-program-menu.png), [nested namespaces](navigation/breadcrumb-namespace-menu.png),
[symbol target](navigation/breadcrumb-nested-symbol-menu.png), [history](navigation/breadcrumb-history-menu.png).

The review exposed a separate SearchList bug: a typed Enter invoked the result twice and then
redispatched the event to the filter. The first invocation could close/dispose the model and the
second would throw. The event is now consumed and handled once. Its regression
[fails before the fix](search-enter-before.xml) and [passes after it](search-enter-after.xml).

## Validation

| Check | Result |
| --- | --- |
| [Program switcher](program-switcher-tests.xml) | Nine integration tests, zero failures/errors |
| [Existing stock navigation](stock-navigation-tests.xml) | Three integration tests, zero failures/errors |
| [Breadcrumb/history/provider lifecycle](breadcrumb-tests.xml) | All 36 integration tests, zero failures/errors |
| Search component tests | Five SearchList, three presentation and one ranking test pass |
| [Full Docking/Gui/Graph suite](unit-test-suites.csv) | 837 tests, nine failures, zero errors/skips |
| [Clean baseline comparison](../clean-baseline/README.md) | All nine failed methods and assertion messages reproduce before the UI changes |
| Logical scales | Successful separate runs at 100%, 125%, 150% and 200%; screenshots/manifests retained |
| IDE checks | New dialog/test and changed breadcrumb file inspect clean; IntelliJ compilation passes |
| [Expanded runtime gate](runtime-registration-gate.json) | 17,478 entries, 9,338 traced action handlers, zero unresolved registrations/handlers |

Additional physical-DPI, macOS and screen-reader hardware acceptance remains excluded at the
owner's request. Key-event tests verify Swing dispatch, not native device input. Scale captures
are logical Swing rendering. This pass does not invent a physical-input pass.

## Dialog and source-only reviews

Actual blank-label validation, failed asynchronous Go To queries, numeric-range validation,
switcher empty/no-match states and cancellation were reviewed. Failed/cancelled operations leave
the represented program/location intact. [Assertions](navigation/navigation-checks.txt),
[label feedback](navigation/label-validation-error.png), [Go To no results](navigation/goto-no-results.png),
[range feedback](navigation/range-validation-error.png).

All fifty candidates in the three UI-bearing roots missing from the application registry are now
[classified with source evidence](../source-scope/README.md). The actual shipped Skeleton was built
as a disposable extension, loaded, and its [provider](template/skeleton-provider.png) and
[local action](template/skeleton-custom-action.png) exercised. The real bootstrap
[JDK chooser](bootstrap/bootstrap-jdk-chooser.png) was shown and cancelled; CLI recovery was verified.
GhidraDev was compiled from this checkout and reviewed in a private Eclipse installation/workspace:
31 captures cover wizard pages, source-folder selection, launch-configuration tabs and preferences,
including exact invalid-port messages and cancellation. No application/debug target was launched.

The overall exhaustive application visual release gate remains open: these are specific reviewed
states, not certification of every source candidate or optional service workflow. Source helpers,
templates and externally hosted Eclipse pages now have concrete classifications and entry routes;
their presence/absence is no longer an unexplained application-plugin gap.

## Reproduce

```powershell
.\gradlew.bat :Docking:test --tests docking.widgets.searchlist.SearchListTest `
    :Base:integrationTest --tests ghidra.app.nav.ProgramSwitcherDialogTest `
    :Decompiler:integrationTest --tests ghidra.app.plugin.core.decompile.LocationContextTest `
    :Base:jar :Docking:jar --offline --console=plain
.\support\ui\review-specialists.ps1 -Surfaces navigation -TraceHandlers -Tag navigation-next
# Supply a seed profile containing the built SkeletonReview extension for the template surface.
.\support\ui\review-eclipse.ps1 -Tag eclipse-next
& 'C:\Python315\python.exe' support/ui/classify_source_scope.py
```

Use a new tag. The harness copies project/profile fixtures; modern and stock command identities
and saved formats are preserved. `capture-manifest.json`, scale manifests and runtime manifests
record capture-time jars/helpers. Final tooling/source fingerprints and retained artifact hashes
are recorded separately; commit IDs alone do not identify a dirty capture's contents.
Scoped Git attributes preserve the evidence bytes so checksum validation survives line-ending conversion.

## Probe incident

The first bootstrap probe incorrectly assumed a JVM `application.settingsdir` argument governed
LaunchSupport's save destination. LaunchSupport reads that value from `launch.properties`, so the
probe wrote the currently running JDK path to the existing
`C:/Users/Sean/AppData/Roaming/ghidra/ghidra_12.2_DEV/java_home.save`.
The prior file contents were not captured and no restoration is claimed. The resulting path is
`C:/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot`. The corrected harness copies the installation
properties, checks the actual resolved save-file path before invocation and saves only below its
private fixture. Hash checks confirm subsequent runs left the active file unchanged.

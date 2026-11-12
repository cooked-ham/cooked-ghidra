# Clean pre-UI failure baseline — 2026-11-12

All nine known failed methods and assertion messages reproduce in an unmodified pre-UI checkout.
The baseline is `9363fdb808cb6eb7d500feee67dcf3322bd3217f`: the upstream merge plus Gradle compatibility
changes, immediately before the first UI-refresh merge. Its tracked tree stayed clean. Dependencies
were shared through a junction; source, compiled output, Gradle project state and reports were separate.
Both runs used the same Windows host, JDK 25.0.5 and Gradle 9.9.0 wrapper.

[Comparison metadata](comparison.json), [identical failures](failures.csv), and the original JUnit
reports under `pre-ui/` and `current/` are retained. Each focused run executed 46 tests: nine
failures, zero errors. The final full-suite comparison also has exactly those nine failures.
This establishes that they predate the UI changes; it does not diagnose every font/layout cause.

```powershell
git worktree add --detach E:/cooked-ghidra/build/verification/pre-ui-next 9363fdb808
New-Item -ItemType Junction -Path E:/cooked-ghidra/build/verification/pre-ui-next/dependencies `
    -Target E:/cooked-ghidra/dependencies
.\gradlew.bat -p build/verification/pre-ui-next :Docking:test `
    --tests docking.widgets.fieldpanel.field.CompositeVerticalLayoutTextFieldTest `
    --tests docking.widgets.fieldpanel.FlowLayoutTextFieldTest `
    --tests docking.widgets.fieldpanel.VerticalLayoutTextFieldTest `
    --tests docking.widgets.tab.GTabPanelTest --offline --console=plain
```

Check JUnit results, not only the process exit code: these Gradle tasks use `ignoreFailures=true`.
The baseline's application version is 12.3; the fork later changed its version metadata to 12.2.
No baseline product source was edited to accommodate the test run.

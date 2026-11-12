# Live vector persistence

Run: `build/verification/vector-persistence-live1`, 2026-11-11.
Reproduce with `support/ui/review-vector-persistence.ps1` (requires the prepared
release distribution, seed profile, fixture project and external harness source).
The runner clones the distribution, project and profile; the specialist runtime is untouched.

`save.csv` records 283 direct vector registrations under built-in Graphite, a saved/reloaded
copy, ZIP import and stock-theme switch/back. `restart.csv` records the same count with
the saved theme selected automatically by preferences in a different JVM (PID 25988,
versus 10944 before restart). `manifest.json` records the executing jar and probe-source hashes.
`review.done` is emitted only after both phase reports and process/count checks pass.

The application log contains no ERROR, NullPointerException or CalloutRequest entry.
The PNGs are in-process Swing frame painting of the empty Code Browser with harness panes;
they establish capture availability, not populated-view visual acceptance. This run uses
the application theme APIs also used by the editor; it does not automate editor clicks.
Physical DPI, screen-reader behavior and full saved-layout restart acceptance remain open.

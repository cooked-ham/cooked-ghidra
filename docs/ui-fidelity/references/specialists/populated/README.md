# Populated specialist UI capture

Captured on Windows on 2026-11-11 from the disposable
`build/verification/runtime-specialist-ui` distribution, using the prepared
[specialist fixture](../fixture-manifest.json) and the Graphite Preview theme. One Ghidra process
was used per surface so a transient tool or dialog could not affect the next capture. The Front End
and tool frames were hidden or positioned off-screen before being shown.

The assertions are data and visibility checks against real providers. The screenshots are retained
for later human review of layout, state and styling; they are **not** visual, interaction or
accessibility acceptance.

| Surface | Populated fixture evidence | Screenshots |
| --- | --- | --- |
| Code Browser: source | `source.bin` opens; five functions; source compare immediate is 42 | [source](screenshots/codebrowser-source.png) |
| Code Browser: program diff | Determine Program Differences accepted; diff active with changed immediate, name, type and unmatched functions | [dialog](screenshots/program-diff-dialog.png), [diff](screenshots/program-diff.png) |
| Code Browser: function comparison | Two populated function panels from the source fixture | [comparison](screenshots/code-compare.png) |
| BSim | Local two-executable H2 fixture queried; 11 cross-program destination hits | [search](screenshots/bsim-search-dialog.png), [results](screenshots/bsim-search-results.png) |
| Version Tracking | Saved session opens with four manual matches: one accepted, one rejected, two available; markup provider visible; first match compared | [matches](screenshots/version-tracking.png), [compare](screenshots/version-tracking-compare.png) |
| Debugger | Saved synthetic trace opens with 21 snapshots and two threads; the best stack has one frame at snapshot 0 | [trace](screenshots/debugger-trace.png) |

The combined [review.csv](review.csv) retains the six evidence rows. The runtime registration
captures are in [runtime/](runtime/) and contain the plugins, providers and actions observed for
each surface.

[run-manifest.json](run-manifest.json) records the source revision, distribution, fixture, script
hashes, the fixed Docking jar hash, retained screenshots and runtime captures. The run directory is
`build/verification/graphite-specialist-ui-final8`.

The theme-change/help-reload path that previously produced
`CalloutRequest.doUpdate` NPEs is covered by these runs: the three surface application logs contain
no `NullPointerException`, `CalloutRequest` or `ERROR` entries. The stale-callout fix is in
`docking.help.CalloutRequest` and `docking.help.DockingHelpBroker`.

## Limits

- The screenshots are in-process Swing paintings with off-screen tool frames, not native-window or
  physical-DPI evidence.
- The debugger trace is the shipped synthetic demonstration adapted for this fixture; it is not a
  live Windows dbgeng/x64dbg recording.
- BSim uses the prepared local H2 database; authentication, network failure and reconnect states
  are not covered.
- Version Tracking uses the prepared saved session; correlator accuracy and all markup failure
  paths are not evaluated.
- Dialog layout, styling, focus, keyboard interaction and accessibility still require human visual
  review. The prepared repository conflict also remains deliberately unresolved.

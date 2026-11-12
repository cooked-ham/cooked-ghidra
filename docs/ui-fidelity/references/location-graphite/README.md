# Event-driven location context and Graphite depth

Windows verification, 2026-11-11. The location feature remains enabled with
`-Dghidra.ui.locationContext=true`. Main captures use the final provider code; specialist
captures exercise shared chrome with their normal existing entry paths and prepared data.

- `location-tests.xml`: 16 headed integration tests, zero failures/errors. Covers history,
  provider/model events, hidden panels, namespace-symbol navigation, program switching and
  closure, overlay address spaces, clone binding, pending results and edited locked results.
- `contrast-tests.xml` and `button-tests.xml`: six tests, zero failures/errors. Includes text
  contrast at both Graphite gradient stops, existing syntax/graph contrast across six themes,
  button states and stock restoration.
- `main/`: populated Listing/Decompiler, Bytes, Symbol Table, Function Graph, Front End,
  Navigation options and action chooser; saved Graphite copy, stock switch and switch back.
  See `capture.csv` and `capture-manifest.json` for sequence and jar hashes.
- `specialists/`: populated Code Browser comparison/diff/BSim, VT and Debugger providers;
  six evidence rows, nine screenshots, seven runtime registration captures. The application
  logs have zero ERROR, NullPointerException or CalloutRequest entries.

Raw runs: `build/verification/location-context-final16.log`,
`build/verification/graphite-depth-contrast.log`,
`build/verification/location-graphite-capture2`,
`build/verification/location-graphite-specialists1`.
Reproduce captures with `support/ui/capture-reference.ps1` and
`support/ui/review-specialists.ps1`, using an isolated distribution containing the changed jars.

The shared rendering routes reach dock/custom/graph headers, graph-card borders, table headers,
toolbar states, tabs, options sections and dialog action rows. Screenshots were inspected for
legible headers, content separation and gradient continuity. They are in-process Swing painting,
not physical-DPI, screen-reader, performance or exhaustive per-widget acceptance. The inventory
keeps those gates open; this evidence does not mark every specialist state as accepted.

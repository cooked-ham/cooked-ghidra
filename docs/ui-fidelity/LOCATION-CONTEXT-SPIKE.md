# Location-context spike

Enable `-Dghidra.ui.locationContext=true` at startup. The spike wraps the existing Listing and
Decompiler components with provider-local breadcrumbs and history controls. With the property
absent, `getComponent()` returns the original decoration component and the new supplier is never
queried. There are no new action identities, key bindings, serialized layout fields or preferences.

## What the spike establishes

- Program, namespace, function, explicit address space/address and signed function offset come
  from that provider's represented content. Undefined bytes, defined data, instructions outside
  functions, missing locations and missing programs have explicit text. Cross-program stale
  locations cannot supply an address beneath another program's name.
- Function and address buttons use the existing `GoToService`. The program button explicitly
  navigates to its first address. The namespace segment is informational in this spike; namespace
  browsing/discovery is still open. Long content has a horizontal viewport and complete tooltips.
- Back/forward use `NavigationHistoryService`, restoring existing location mementos. Connected
  providers must query/navigate the shared default navigatable: `addNewLocation` redirects to it,
  while `hasPrevious`, `hasNext`, `previous` and `next` do not. Clones retain their own target.
  Updating the labels never adds a history entry.
- The Listing labels itself `Connected` or `Independent`. The Decompiler labels `Following`,
  `Pending`, `Unavailable`, `Out of sync` or `Independent`. Text describes the state independently
  of color. `Following` means the same program and function, because C cursor addresses are
  approximate. A Listing jump to data can intentionally leave the old decompiled function visible;
  the badge then says `Out of sync` instead of silently claiming linkage.
- Pending decompilation retains the rendered function's location; it never substitutes the
  pending target under the old C result. With no rendered result, the address is unavailable.
- Existing `Lock Display` freezes automatic refresh after edits, **not location following**.
  It is displayed as `/ refresh locked` alongside the linkage state. Independent clones remain
  independent even when they permit outgoing navigation events.

Only showing panels refresh, using a coalescing Swing timer at 200 ms; removal stops the timer.
Refresh performs local metadata lookups and history availability queries. It does not decompile,
walk a program, traverse a namespace, query a service over the network or write a setting.
This polling is a spike implementation, not a measured production performance result.

## Validation

`LocationContextTest` runs against actual Ghidra providers and navigation/decompiler services:

1. Repeated context refresh leaves history unchanged; back/forward restore the expected locations.
2. The function segment navigates to the real function entry via the normal navigation service.
3. A pending decompilation continues to describe the rendered result.
4. Listing navigation to data exposes an out-of-sync Decompiler without relabeling its address.
5. Refresh lock retains following semantics; a disconnected clone reports independence.
6. A foreign-program location cannot produce a mixed program/address breadcrumb.
7. Disabling the spike preserves the original component and does not query the context supplier.

All seven pass in `build/verification/graphite-validation-final.log`. Read the JUnit XML as well:
this repository's Gradle test tasks use `ignoreFailures=true`, so a successful Gradle exit alone
is insufficient. Syntax/graph contrast also passes for the five existing themes and Graphite Preview.

## Work remaining before normal rollout

- Event/service lifecycle subscriptions with measured latency, repaint and allocation baselines;
  verify rapid navigation, cancellation, hidden panes and disposed/closed programs.
- Namespace segment navigation, history menus/tooltips that reflect actual shortcut bindings,
  useful program switching semantics, responsive overflow and compact/comfortable layouts.
- Explicit pending feedback during the plugin's delayed-location dispatch, before a decompiler
  request starts. Independent clones currently keep their independence badge while compiling;
  a separate activity indicator is still needed for pending/unavailable clone results.
- Distinguish edit-stale locked results from merely refresh-locked results; provider-to-provider
  linkage should identify its actual source in detached/multiple tool workflows.
- Cross-program history, external references, overlays/address spaces, undefined functions,
  program close/reopen, detached panes and independently navigated clones need additional
  end-to-end acceptance. The mismatch guard and simple clone test do not cover that full matrix.
- Physical DPI, screen-reader names/descriptions, keyboard traversal and focus restoration,
  saved-tool/process restart, profile preservation and actual user docking acceptance remain open.

The spike supports the integration route and exposes the important history/refresh-lock semantics.
It does not close milestone 1 or establish an effort estimate for exhaustive coverage.

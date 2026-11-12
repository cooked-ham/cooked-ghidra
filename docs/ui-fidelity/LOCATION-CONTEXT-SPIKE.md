# Location context (opt-in)

Enable `-Dghidra.ui.locationContext=true` at startup. The spike wraps the existing Listing and
Decompiler components with provider-local breadcrumbs and history controls. With the property
absent, `getComponent()` returns the original decoration component and the new supplier is never
queried. There are no new action identities, key bindings, serialized layout fields or preferences.

## What the spike establishes

- Program, namespace, function, explicit address space/address and signed function offset come
  from that provider's represented content. Undefined bytes, defined data, instructions outside
  functions, missing locations and missing programs have explicit text. Cross-program stale
  locations cannot supply an address beneath another program's name.
- Function, address and namespace-symbol navigation use the existing `GoToService`. The program
  menu activates an open program through `ProgramManager`, retaining its normal location handling.
  Independent clones retain their original program binding and explain the disabled switch.
  The namespace menu supports parent/child traversal, function/label navigation, pages of 100
  symbols and revealing non-global namespaces in the Symbol Tree when its service is present.
  Long content has a horizontal viewport and complete tooltips.
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

Showing panels subscribe to tool context and displayed-program model events. Provider-local
notifications also update independent/unfocused panes; the Decompiler plugin notifies before its
delayed location dispatch. Requests coalesce into an EDT callback, with no periodic timer. Hiding
or removing a panel releases subscriptions, and reopening refreshes current state. Refresh performs
only local metadata/history queries; namespace enumeration happens on explicit menu interaction.
Independent decompilers expose pending/unavailable activity alongside independence. Locked results
with an active refresh overlay say `stale / refresh locked`, distinguishing edits from the lock alone.

## Validation

`LocationContextTest` runs against actual Ghidra providers and navigation/decompiler services:

1. Repeated context refresh leaves history unchanged; back/forward restore the expected locations.
2. The function segment navigates to the real function entry via the normal navigation service.
3. A pending decompilation continues to describe the rendered result.
4. Listing navigation to data exposes an out-of-sync Decompiler without relabeling its address.
5. Refresh lock retains following semantics; a disconnected clone reports independence.
6. A foreign-program location cannot produce a mixed program/address breadcrumb.
7. Disabling the spike preserves the original component and does not query the context supplier.

The original seven passed in `build/verification/graphite-validation-final.log`. The expanded
16-test suite passes in `build/verification/location-context-final16.log`, including event-driven
navigation/rename updates, hidden-panel suppression, namespace navigation, program switching,
clone binding, actual edits to locked results, program close and overlay address spaces.
Read the JUnit XML as well:
this repository's Gradle test tasks use `ignoreFailures=true`, so a successful Gradle exit alone
is insufficient. Syntax/graph contrast also passes for the five existing themes and Graphite Preview.

## Burst and lifecycle follow-up — 2026-11-11

Background model notifications now acquire the refresh gate before posting to the EDT.
Previously each worker notification posted a dispatcher even when a refresh was already
pending. The atomic gate bounds a burst to one outstanding callback; visibility and provider
state are checked on the EDT, and hidden/disposed providers still skip supplier queries.

Three additional integration regressions cover 100,000 worker requests while the EDT is
occupied (one supplier query, delivered before a queue sentinel), reparenting a panel between
two visible Swing frames with a pending refresh and a changed location, and tool-level program
close/reopen followed by navigation. The reopen fixture retains a program consumer; it does
not test loading a new program instance from disk. Window reparenting tests panel lifecycle,
not mouse docking or docking-layout persistence.

All 19 location-context tests pass with zero failures/errors in
`build/verification/location-context-stress-final.log`; the retained JUnit result is
[location-context-stress-tests.xml](references/location-context-stress-tests.xml).
IDE error inspections pass for both changed Java files.

The burst log reports total fixture elapsed time, including window setup and Swing draining.
It is diagnostic output, not a navigation latency, allocation, repaint, or release performance
baseline. Those measurements and the broader detached/external-reference/restart matrix
remain open. The feature remains opt-in.

## Work remaining before normal rollout

- Measured latency, repaint/allocation baselines and navigation/cancellation stress tests.
- History menus/tooltips reflecting actual shortcut bindings, responsive overflow and density review.
- Provider-to-provider linkage identifying its actual source across detached/multiple tools.
- Cross-program history, external references, overlays/address spaces, undefined functions,
  program reopen, detached panes and independently navigated clones need additional
  end-to-end acceptance. The targeted regressions do not cover every combination in that matrix.
- Physical DPI, screen-reader names/descriptions, keyboard traversal and focus restoration,
  saved-tool/process restart, profile preservation and actual user docking acceptance remain open.

The spike supports the integration route and exposes the important history/refresh-lock semantics.
It does not close milestone 1 or establish an effort estimate for exhaustive coverage.

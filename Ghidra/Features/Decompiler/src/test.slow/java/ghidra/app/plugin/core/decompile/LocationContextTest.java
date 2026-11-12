/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.decompile;

import static org.junit.Assert.*;

import java.awt.Component;
import java.awt.Container;

import javax.swing.*;

import org.junit.*;

import ghidra.app.nav.LocationContextPanel;
import ghidra.app.services.GoToService;
import ghidra.app.services.NavigationHistoryService;
import ghidra.app.services.ProgramManager;
import ghidra.program.model.symbol.SourceType;
import ghidra.program.model.listing.Program;
import ghidra.program.util.ProgramLocation;
import ghidra.test.ClassicSampleX86ProgramBuilder;

/** Exercises the opt-in spike against real Listing, Decompiler and history services. */
public class LocationContextTest extends AbstractDecompilerTest {

	private String oldProperty;

	@Before
	@Override
	public void setUp() throws Exception {
		oldProperty = System.getProperty("ghidra.ui.locationContext");
		System.setProperty("ghidra.ui.locationContext", "true");
		super.setUp();
	}

	@After
	@Override
	public void tearDown() throws Exception {
		try {
			super.tearDown();
		}
		finally {
			if (oldProperty == null) {
				System.clearProperty("ghidra.ui.locationContext");
			}
			else {
				System.setProperty("ghidra.ui.locationContext", oldProperty);
			}
		}
	}

	@Override
	protected Program getProgram() throws Exception {
		return new ClassicSampleX86ProgramBuilder("location-context", false, this).getProgram();
	}

	@Test
	public void testProgramCloseClearsDisplayedTargets() {
		decompile("01002cf5");
		runSwing(() -> assertTrue(tool.getService(ProgramManager.class).closeProgram(program, true)));
		waitForSwing();
		runSwing(() -> {
			assertFalse(button(listingPanel(), "Go to displayed address").isEnabled());
			assertFalse(button(listingPanel(), "Go to function entry").isEnabled());
			assertNull(provider.getLocationContext().program());
		});
	}

	@Test
	public void testOverlayAddressKeepsItsAddressSpace() throws Exception {
		decompile("01002cf5");
		ghidra.program.model.address.Address overlay;
		int transaction = program.startTransaction("Overlay context fixture");
		try {
			overlay = program.getMemory().createInitializedBlock("context_overlay", addr("01002cf5"),
				0x100, (byte) 0, ghidra.util.task.TaskMonitor.DUMMY, true).getStart();
		}
		finally { program.endTransaction(transaction, true); }
		runSwing(() -> tool.getService(GoToService.class).goTo(new ProgramLocation(program, overlay)));
		codeBrowser.updateNow();
		waitForSwing();
		runSwing(() -> {
			assertEquals(overlay, codeBrowser.getCurrentAddress());
			assertTrue(button(listingPanel(), "Go to displayed address").getText().contains("context_overlay"));
		});
	}

	@Test
	public void testHiddenContextDoesNotQuerySupplier() {
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		LocationContextPanel[] panel = new LocationContextPanel[1];
		JFrame[] frame = new JFrame[1];
		try {
			runSwing(() -> {
				panel[0] = new LocationContextPanel(tool, codeBrowser.getProvider(), () -> {
					calls.incrementAndGet();
					return new LocationContextPanel.Context(program, null, "Connected", "Test lifecycle");
				});
				frame[0] = new JFrame("Context lifecycle");
				frame[0].add(panel[0]);
				frame[0].pack();
				frame[0].setVisible(true);
			});
			waitForSwing();
			assertTrue(calls.get() > 0);
			runSwing(() -> frame[0].setVisible(false));
			waitForSwing();
			int hiddenCount = calls.get();
			runSwing(() -> { for (int i = 0; i < 100; i++) panel[0].requestRefresh(); });
			waitForSwing();
			assertEquals(hiddenCount, calls.get());
		}
		finally { runSwing(() -> { if (frame[0] != null) frame[0].dispose(); }); }
	}

	@Test
	public void testWorkerBurstCoalescesBeforeEdtDispatch() {
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		LocationContextPanel[] panel = new LocationContextPanel[1];
		JFrame[] frame = new JFrame[1];
		java.util.concurrent.atomic.AtomicInteger callsAtSentinel = new java.util.concurrent.atomic.AtomicInteger();
		long started = System.nanoTime();
		try {
			runSwing(() -> {
				panel[0] = new LocationContextPanel(tool, codeBrowser.getProvider(), () -> {
					calls.incrementAndGet();
					return new LocationContextPanel.Context(program, null, "Connected", "Burst fixture");
				});
				frame[0] = new JFrame("Context stress");
				frame[0].add(panel[0]);
				frame[0].pack();
				frame[0].setVisible(true);
			});
			waitForSwing();
			runSwing(() -> {
				calls.set(0);
				// Hold the EDT while a model thread emits the entire burst.
				Thread worker = new Thread(() -> {
					for (int i = 0; i < 100_000; i++) panel[0].requestRefresh();
				});
				worker.start();
				try { worker.join(10_000); }
				catch (InterruptedException e) { throw new AssertionError(e); }
				assertFalse("Worker must not block on EDT", worker.isAlive());
				// Verify queue ordering: refresh runs before this sentinel, and no
				// second-stage dispatcher postpones it until after the sentinel.
				SwingUtilities.invokeLater(() -> callsAtSentinel.set(calls.get()));
			});
			waitForSwing();
			assertEquals("Refresh must precede queue sentinel", 1, callsAtSentinel.get());
			assertEquals(1, calls.get());
			System.out.println("Context burst: 100000 requests, 1 supplier query; fixture elapsed ms=" +
				(System.nanoTime() - started) / 1_000_000);
		}
		finally { runSwing(() -> { if (frame[0] != null) frame[0].dispose(); }); }
	}

	@Test
	public void testReparentedPanelRefreshesLatestLocation() {
		LocationContextPanel[] panel = new LocationContextPanel[1];
		JFrame[] frames = new JFrame[2];
		java.util.concurrent.atomic.AtomicReference<ProgramLocation> location =
			new java.util.concurrent.atomic.AtomicReference<>(new ProgramLocation(program, addr("01002cf5")));
		try {
			runSwing(() -> {
				panel[0] = new LocationContextPanel(tool, codeBrowser.getProvider(), () ->
					new LocationContextPanel.Context(program, location.get(), "Connected", "Reparent fixture"));
				for (int i = 0; i < frames.length; i++) frames[i] = new JFrame("Context window " + i);
				frames[0].add(panel[0]);
				frames[0].pack();
				frames[0].setVisible(true);
			});
			waitForSwing();
			runSwing(() -> {
				panel[0].requestRefresh();
				frames[0].remove(panel[0]);
				location.set(new ProgramLocation(program, addr("010059a3")));
				frames[1].add(panel[0]);
				frames[1].pack();
				frames[1].setVisible(true);
			});
			waitForSwing();
			runSwing(() -> assertTrue(button(panel[0], "Go to displayed address").getText().contains("010059a3")));
		}
		finally { runSwing(() -> { for (JFrame frame : frames) if (frame != null) frame.dispose(); }); }
	}

	@Test
	public void testProgramReopenRestoresTargets() {
		Object consumer = new Object();
		program.addConsumer(consumer);
		try {
			decompile("01002cf5");
			runSwing(() -> {
				ProgramManager manager = tool.getService(ProgramManager.class);
				assertTrue(manager.closeProgram(program, true));
			});
			waitForSwing();
			runSwing(() -> tool.getService(ProgramManager.class).openProgram(program));
			decompile("010059a3");
			waitForSwing();
			runSwing(() -> {
				assertTrue(button(listingPanel(), "Go to displayed address").isEnabled());
				assertTrue(button(listingPanel(), "Go to displayed address").getText().contains("010059a3"));
				assertSame(program, provider.getLocationContext().program());
			});
		}
		finally { program.release(consumer); }
	}

	@Test
	public void testNamespaceMenuNavigatesToSymbol() {
		decompile("01002cf5");
		runSwing(() -> {
			button(listingPanel(), "Browse namespace symbols").doClick();
			JPopupMenu popup = (JPopupMenu) MenuSelectionManager.defaultManager().getSelectedPath()[0];
			for (Component c : popup.getComponents()) {
				if (c instanceof JMenuItem item && item.getText().contains(" — ")) {
					String address = item.getText().substring(item.getText().indexOf(" — ") + 3);
					item.doClick();
					assertEquals(program.getAddressFactory().getAddress(address), codeBrowser.getCurrentAddress());
					return;
				}
			}
			fail("Fixture must expose an addressable namespace symbol");
		});
	}

	@Test
	public void testLockedEditedResultReportsStale() throws Exception {
		decompile("01002cf5");
		performAction(getAction(decompiler, "Lock Display"), provider, true);
		int transaction = program.startTransaction("Edit locked result");
		try {
			program.getFunctionManager().getFunctionContaining(addr("01002cf5")).setName("stale_fixture", SourceType.USER_DEFINED);
		}
		finally { program.endTransaction(transaction, true); }
		program.flushEvents();
		waitForCondition(() -> runSwing(() -> provider.getLocationContext().state().contains("stale")));
	}

	@Test
	public void testCloneProgramSwitchDoesNotActivateMainProgram() throws Exception {
		decompile("01002cf5");
		ClassicSampleX86ProgramBuilder other = new ClassicSampleX86ProgramBuilder("clone-target", false, this);
		DecompilerProvider[] cloneHolder = new DecompilerProvider[1];
		try {
			runSwing(() -> {
				ProgramManager manager = tool.getService(ProgramManager.class);
				manager.openProgram(other.getProgram());
				manager.setCurrentProgram(program);
				DecompilerProvider clone = decompiler.createNewDisconnectedProvider();
				cloneHolder[0] = clone;
				clone.doSetProgram(program);
				clone.goTo(program, new ProgramLocation(program, addr("01002cf5")));
				tool.showComponentProvider(clone, true);
			});
			waitForSwing();
			runSwing(() -> {
				DecompilerProvider clone = cloneHolder[0];
				ProgramManager manager = tool.getService(ProgramManager.class);
				LocationContextPanel panel = (LocationContextPanel) find(clone.getComponent(), "Location context");
				assertNotNull(panel);
				panel.refreshContext();
				button(panel, "Switch open program").doClick();
				JPopupMenu popup = (JPopupMenu) MenuSelectionManager.defaultManager().getSelectedPath()[0];
				for (Component c : popup.getComponents()) {
					if (c instanceof JMenuItem item && item.getText().equals("clone-target")) assertFalse(item.isEnabled());
				}
				MenuSelectionManager.defaultManager().clearSelectedPath();
				assertSame(program, clone.getProgram());
				assertSame(program, manager.getCurrentProgram());
			});
		}
		finally { other.dispose(); }
	}

	@Test
	public void testProviderEventsRefreshWithoutPollingOrManualRefresh() {
		decompile("01002cf5");
		runSwing(() -> tool.getService(GoToService.class).goTo(new ProgramLocation(program, addr("010059a3"))));
		codeBrowser.updateNow();
		waitForDecompiler();
		waitForSwing();
		runSwing(() -> assertTrue(button(listingPanel(), "Go to displayed address").getText().contains("010059a3")));
	}

	@Test
	public void testProgramMetadataEventRefreshesFunctionCrumb() throws Exception {
		decompile("01002cf5");
		int transaction = program.startTransaction("Rename breadcrumb fixture");
		try {
			program.getFunctionManager().getFunctionContaining(addr("01002cf5")).setName("renamed_context", SourceType.USER_DEFINED);
		}
		finally { program.endTransaction(transaction, true); }
		program.flushEvents();
		waitForSwing();
		runSwing(() -> assertEquals("renamed_context", button(listingPanel(), "Go to function entry").getText()));
	}

	@Test
	public void testProgramMenuSwitchesAcrossPrograms() throws Exception {
		decompile("01002cf5");
		ClassicSampleX86ProgramBuilder other = new ClassicSampleX86ProgramBuilder("switch-target", false, this);
		try {
			runSwing(() -> tool.getService(ProgramManager.class).openProgram(other.getProgram()));
			waitForSwing();
			runSwing(() -> {
				button(listingPanel(), "Switch open program").doClick();
				JPopupMenu popup = (JPopupMenu) MenuSelectionManager.defaultManager().getSelectedPath()[0];
				for (Component c : popup.getComponents()) {
					if (c instanceof JMenuItem item && item.getText().equals(program.getName())) item.doClick();
				}
			});
			codeBrowser.updateNow();
			waitForSwing();
			assertSame(program, tool.getService(ProgramManager.class).getCurrentProgram());
		}
		finally { other.dispose(); }
	}

	@Test
	public void testRefreshDoesNotAddHistoryAndButtonsRestoreLocations() {
		GoToService goTo = tool.getService(GoToService.class);
		NavigationHistoryService history = tool.getService(NavigationHistoryService.class);
		runSwing(() -> goTo.goTo(new ProgramLocation(program, addr("01002cf5"))));
		codeBrowser.updateNow();
		waitForDecompiler();
		runSwing(() -> goTo.goTo(new ProgramLocation(program, addr("010059a3"))));
		codeBrowser.updateNow();
		waitForDecompiler();
		LocationContextPanel panel = listingPanel();
		runSwing(() -> {
			int previous = history.getPreviousLocations(goTo.getDefaultNavigatable()).size();
			for (int i = 0; i < 20; i++) {
				assertNotNull(panel);
				panel.refreshContext();
			}
			assertEquals(previous, history.getPreviousLocations(goTo.getDefaultNavigatable()).size());
			assertTrue(button(panel, "Previous location in history").isEnabled());
			button(panel, "Previous location in history").doClick();
		});
		codeBrowser.updateNow();
		waitForSwing();
		assertEquals(addr("01002cf5"), codeBrowser.getCurrentAddress());
		runSwing(() -> button(panel, "Next location in history").doClick());
		codeBrowser.updateNow();
		waitForSwing();
		assertEquals(addr("010059a3"), codeBrowser.getCurrentAddress());
	}

	@Test
	public void testFunctionCrumbUsesExistingGoToService() {
		decompile("01002cf5");
		runSwing(() -> {
			LocationContextPanel panel = listingPanel();
			assertNotNull(panel);
				panel.refreshContext();
			button(panel, "Go to function entry").doClick();
			assertEquals(program.getFunctionManager().getFunctionContaining(addr("01002cf5")).getEntryPoint(),
				codeBrowser.getCurrentAddress());
		});
	}

	@Test
	public void testPendingBreadcrumbDescribesRenderedResult() {
		decompile("01002cf5");
		runSwing(() -> {
			ProgramLocation rendered = provider.getLocationContext().location();
			assertNotNull(rendered);
			provider.setLocation(new ProgramLocation(program, addr("010059a3")), null);
			LocationContextPanel.Context context = provider.getLocationContext();
			assertEquals("Pending", context.state());
			assertTrue(provider.getController().getDecompileData().contains(context.location()));
			assertNotEquals(addr("010059a3"), context.location().getAddress());
		});
	}

	@Test
	public void testOutOfSyncAfterListingNavigatesToData() {
		decompile("01002cf5");
		runSwing(() -> tool.getService(GoToService.class).goTo(program.getMinAddress(), program));
		waitForDecompiler();
		runSwing(() -> {
			LocationContextPanel.Context context = provider.getLocationContext();
			assertEquals("Out of sync", context.state());
			assertNotEquals(program.getMinAddress(), context.location().getAddress());
		});
	}

	@Test
	public void testRefreshLockStillFollowsAndCloneIsIndependent() {
		decompile("01002cf5");
		runSwing(() -> assertEquals("Following", provider.getLocationContext().state()));
		performAction(getAction(decompiler, "Lock Display"), provider, true);
		runSwing(() -> {
			assertEquals("Following / refresh locked", provider.getLocationContext().state());
			DecompilerProvider clone = decompiler.createNewDisconnectedProvider();
			clone.doSetProgram(program);
			clone.goTo(program, new ProgramLocation(program, addr("01002cf5")));
			assertTrue(clone.getLocationContext().state().startsWith("Independent"));
		});
	}

	@Test
	public void testMismatchedProgramNeverProducesMixedBreadcrumb() throws Exception {
		ClassicSampleX86ProgramBuilder other =
			new ClassicSampleX86ProgramBuilder("other-program", false, this);
		try {
			runSwing(() -> {
				LocationContextPanel panel = new LocationContextPanel(tool, codeBrowser.getProvider(),
					() -> new LocationContextPanel.Context(program,
						new ProgramLocation(other.getProgram(), other.addr("01002cf5")), "Pending", "Test"));
				assertNotNull(panel);
				panel.refreshContext();
				assertEquals("—", button(panel, "Go to displayed address").getText());
				assertFalse(button(panel, "Go to displayed address").isEnabled());
			});
		}
		finally {
			other.dispose();
		}
	}

	@Test
	public void testDisabledSpikePreservesOriginalComponent() {
		System.clearProperty("ghidra.ui.locationContext");
		runSwing(() -> {
			JPanel original = new JPanel();
			assertSame(original, LocationContextPanel.wrap(original, tool, codeBrowser.getProvider(),
				() -> { throw new AssertionError("Disabled spike must not query providers"); }));
		});
	}

	private LocationContextPanel listingPanel() {
		return (LocationContextPanel) find(codeBrowser.getProvider().getComponent(), "Location context");
	}

	private JButton button(Container container, String name) {
		return (JButton) find(container, name);
	}

	private Component find(Container container, String name) {
		for (Component child : container.getComponents()) {
			if (name.equals(child.getName())) {
				return child;
			}
			if (child instanceof Container nested) {
				Component result = find(nested, name);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}
}

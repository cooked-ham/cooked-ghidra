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

	@Test
	public void testHistoryMenuAndReboundShortcut() {
		decompile("01002cf5");
		decompile("010059a3");
		runSwing(() -> {
			var action = tool.getAllActions().stream().filter(a -> a.getName().equals("Previous Location in History")).findFirst().orElseThrow();
			var old = action.getKeyBindingData();
			try {
				action.setKeyBindingData(new docking.action.KeyBindingData(KeyStroke.getKeyStroke("ctrl shift F9")));
				LocationContextPanel panel = listingPanel();
				panel.refreshContext();
				JButton back = button(panel, "Previous location in history");
				assertTrue(back.getToolTipText().contains("F9"));
				back.getActionMap().get("locationHistory").actionPerformed(null);
				JPopupMenu menu = back.getComponentPopupMenu();
				assertTrue(menu.getComponentCount() > 0);
				((JMenuItem) menu.getComponent(0)).doClick();
				menu.setVisible(false);
			}
			finally { action.setKeyBindingData(old); }
		});
		codeBrowser.updateNow();
		assertEquals(addr("01002cf5"), codeBrowser.getCurrentAddress());
	}

	@Test
	public void testResponsiveOverflowAndAccessibleControls() {
		decompile("01002cf5");
		runSwing(() -> {
			LocationContextPanel panel = listingPanel();
			java.awt.Dimension old = panel.getSize();
			try {
				for (int width : new int[] { 240, 360, 600, 1600 }) {
					panel.setSize(width, panel.getPreferredSize().height);
					panel.doLayout();
					for (Component c : panel.getComponents()) if (c instanceof Container container) container.doLayout();
					JButton address = button(panel, "Go to displayed address");
					assertTrue(address.isVisible());
					assertTrue(address.getWidth() > 0);
					assertTrue(address.getX() + address.getWidth() <= address.getParent().getWidth());
					for (String name : new String[] { "Previous location in history", "Next location in history", "More location context", "Go to displayed address" }) {
						JButton control = button(panel, name);
						assertEquals(name, control.getAccessibleContext().getAccessibleName());
						assertTrue(control.isFocusable());
					}
					if (width == 360) {
						JButton overflow = button(panel, "More location context");
						assertTrue(overflow.isVisible());
						overflow.doClick();
						JPopupMenu popup = (JPopupMenu) MenuSelectionManager.defaultManager().getSelectedPath()[0];
						assertEquals(4, popup.getComponentCount());
						// Program submenu must be anchored to the visible overflow button.
						((JMenuItem) popup.getComponent(0)).doClick();
						MenuSelectionManager.defaultManager().clearSelectedPath();
					}
				}
			}
			finally { panel.setSize(old); panel.revalidate(); }
		});
	}

	@Test
	public void testCrossProgramHistoryRestoresProgramAndAddress() throws Exception {
		ClassicSampleX86ProgramBuilder other = new ClassicSampleX86ProgramBuilder("history-other", false, this);
		try {
			decompile("01002cf5");
			runSwing(() -> {
				tool.getService(ProgramManager.class).openProgram(other.getProgram());
				tool.getService(GoToService.class).goTo(new ProgramLocation(other.getProgram(), other.addr("010059a3")));
			});
			codeBrowser.updateNow();
			waitForDecompiler();
			runSwing(() -> selectHistory("Previous location in history", program.getName(), "01002cf5"));
			codeBrowser.updateNow();
			waitForDecompiler();
			assertSame(program, codeBrowser.getProvider().getProgram());
			assertEquals(addr("01002cf5"), codeBrowser.getCurrentAddress());
			runSwing(() -> selectHistory("Next location in history", other.getProgram().getName(), "010059a3"));
			codeBrowser.updateNow();
			waitForDecompiler();
			assertSame(other.getProgram(), codeBrowser.getProvider().getProgram());
			assertEquals(other.addr("010059a3"), codeBrowser.getCurrentAddress());
		}
		finally { other.dispose(); }
	}

	@Test
	public void testDiskReloadUsesNewProgramInstance() throws Exception {
		decompile("01002cf5");
		var file = env.getProject().getProjectData().getRootFolder().createFile("context-reload", program, ghidra.util.task.TaskMonitor.DUMMY);
		runSwing(() -> assertTrue(tool.getService(ProgramManager.class).closeProgram(program, true)));
		Program reopened = tool.getService(ProgramManager.class).openProgram(file);
		assertNotSame(program, reopened);
		runSwing(() -> tool.getService(GoToService.class).goTo(new ProgramLocation(reopened, reopened.getAddressFactory().getAddress("010059a3"))));
		codeBrowser.updateNow();
		waitForDecompiler();
		waitForSwing();
		runSwing(() -> {
			assertSame(reopened, provider.getLocationContext().program());
			assertEquals(reopened.getName(), button(listingPanel(), "Switch open program").getText());
			assertTrue(button(listingPanel(), "Switch open program").getToolTipText().contains("/context-reload"));
			assertTrue(button(listingPanel(), "Go to displayed address").getText().contains("010059a3"));
		});
	}

	@Test
	public void testDetachedProviderAndLayoutRestore() {
		decompile("01002cf5");
		var manager = tool.getWindowManager();
		org.jdom2.Element[] saved = new org.jdom2.Element[1];
		runSwing(() -> {
			Object placeholder = invokeInstanceMethod("getPlaceholder", manager, new Class<?>[] { docking.ComponentProvider.class }, new Object[] { provider });
			invokeInstanceMethod("movePlaceholder", manager, new Class<?>[] { placeholder.getClass(), java.awt.Point.class }, new Object[] { placeholder, new java.awt.Point(100, 100) });
		});
		waitForSwing();
		runSwing(() -> {
			assertNotSame(SwingUtilities.getWindowAncestor(codeBrowser.getProvider().getComponent()), SwingUtilities.getWindowAncestor(provider.getComponent()));
			saved[0] = manager.saveWindowingDataToXml();
		});
		decompile("010059a3");
		runSwing(() -> manager.restoreWindowDataFromXml(saved[0]));
		waitForSwing();
		runSwing(() -> {
			LocationContextPanel panel = (LocationContextPanel) find(provider.getComponent(), "Location context");
			assertTrue(panel.isShowing());
			assertTrue(button(panel, "Go to displayed address").getText().contains("010059a3"));
			assertEquals("Following", provider.getLocationContext().state());
		});
	}

	@Test
	public void testRapidNavigationSettlesOnFinalRenderedFunction() {
		runSwing(() -> {
			GoToService goTo = tool.getService(GoToService.class);
			for (int i = 0; i < 200; i++) goTo.goTo(new ProgramLocation(program, addr(i % 2 == 0 ? "01002cf5" : "010059a3")));
		});
		codeBrowser.updateNow();
		waitForDecompiler();
		waitForSwing();
		runSwing(() -> {
			assertEquals(addr("010059a3"), codeBrowser.getCurrentAddress());
			assertTrue(provider.getController().getDecompileData().contains(provider.getLocationContext().location()));
			assertEquals("Following", provider.getLocationContext().state());
		});
	}

	@Test
	public void testRefreshAndPaintMeasurement() {
		decompile("01002cf5");
		runSwing(() -> {
			LocationContextPanel panel = listingPanel();
			java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(Math.max(1, panel.getWidth()), Math.max(1, panel.getHeight()), java.awt.image.BufferedImage.TYPE_INT_ARGB);
			var graphics = image.createGraphics();
			try {
				for (int i = 0; i < 100; i++) panel.refreshContext();
				long[] refresh = new long[1000];
				long[] paint = new long[1000];
				var bean = java.lang.management.ManagementFactory.getThreadMXBean();
				com.sun.management.ThreadMXBean allocation = bean instanceof com.sun.management.ThreadMXBean b && b.isThreadAllocatedMemorySupported() ? b : null;
				long thread = Thread.currentThread().threadId();
				long before = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread);
				for (int i = 0; i < refresh.length; i++) {
					long start = System.nanoTime();
					panel.refreshContext();
					refresh[i] = System.nanoTime() - start;
					start = System.nanoTime();
					panel.paint(graphics);
					paint[i] = System.nanoTime() - start;
				}
				long bytes = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread) - before;
				java.util.Arrays.sort(refresh);
				java.util.Arrays.sort(paint);
				System.out.printf("LOCATION_METRICS n=1000 refresh_p50_us=%.1f refresh_p95_us=%.1f paint_p50_us=%.1f paint_p95_us=%.1f combined_bytes_per_iteration=%d%n", refresh[500] / 1000.0, refresh[950] / 1000.0, paint[500] / 1000.0, paint[950] / 1000.0, bytes < 0 ? -1 : bytes / 1000);
				assertTrue("Refresh p95 must stay below 50ms on fixture", refresh[950] < 50_000_000);
			}
			finally { graphics.dispose(); }
		});
	}

	private void selectHistory(String control, String programName, String address) {
		JButton button = button(listingPanel(), control);
		button.getActionMap().get("locationHistory").actionPerformed(null);
		JPopupMenu menu = button.getComponentPopupMenu();
		for (Component component : menu.getComponents()) {
			if (component instanceof JMenuItem item && item.getText().startsWith(programName + " — ") && item.getText().contains(address)) {
				item.doClick();
				menu.setVisible(false);
				return;
			}
		}
		fail("History entry missing: " + programName + " at " + address);
	}

	@Test
	public void testExternalProgramNavigationAndMissingDestination() throws Exception {
		ClassicSampleX86ProgramBuilder other = new ClassicSampleX86ProgramBuilder("external-target", false, null);
		try {
			env.getProject().getProjectData().getRootFolder().createFile("external-target", other.getProgram(), ghidra.util.task.TaskMonitor.DUMMY);
			ghidra.program.model.symbol.ExternalLocation external;
			int tx = program.startTransaction("External fixture");
			try {
				external = program.getExternalManager().addExtFunction("context-library", "external-function", addr("010059a3"), SourceType.USER_DEFINED);
				program.getExternalManager().setExternalPath("context-library", "/external-target", true);
			}
			finally { program.endTransaction(tx, true); }
			decompile("01002cf5");
			runSwing(() -> assertTrue(tool.getService(GoToService.class).goToExternalLocation(external, false)));
			codeBrowser.updateNow();
			waitForDecompiler();
			runSwing(() -> {
				assertEquals("external-target", button(listingPanel(), "Switch open program").getText());
				assertEquals(addr("010059a3"), codeBrowser.getCurrentAddress());
				assertTrue(provider.getLocationContext().detail().contains("Source: connected Listing"));
			});
			tx = program.startTransaction("Missing external fixture");
			try { program.getExternalManager().setExternalPath("context-library", "/missing-target", true); }
			finally { program.endTransaction(tx, true); }
			runSwing(() -> assertFalse(tool.getService(GoToService.class).goToExternalLocation(external, false)));
			waitForSwing();
			runSwing(() -> assertEquals("external-target", button(listingPanel(), "Switch open program").getText()));
		}
		finally { other.dispose(); }
	}

	@Test
	public void testMultipleToolsKeepLocalSourceAndHistory() {
		decompile("01002cf5");
		var otherTool = env.launchAnotherDefaultTool();
		runSwing(() -> {
			otherTool.getService(ProgramManager.class).openProgram(program);
			otherTool.getService(GoToService.class).goTo(new ProgramLocation(program, addr("010059a3")));
		});
		waitForSwing();
		assertEquals(addr("01002cf5"), codeBrowser.getCurrentAddress());
		runSwing(() -> {
			assertTrue(button(listingPanel(), "Go to displayed address").getText().contains("01002cf5"));
			assertTrue(provider.getLocationContext().detail().contains("01002cf5"));
			var otherBrowser = getPlugin(otherTool, ghidra.app.plugin.core.codebrowser.CodeBrowserPlugin.class);
			LocationContextPanel panel = (LocationContextPanel) find(otherBrowser.getProvider().getComponent(), "Location context");
			assertTrue(button(panel, "Go to displayed address").getText().contains("010059a3"));
		});
	}

	@Test
	public void testSavedToolRestoresContextInNewTool() throws Exception {
		decompile("01002cf5");
		var file = env.getProject().getProjectData().getRootFolder().createFile("context-saved-tool", program, ghidra.util.task.TaskMonitor.DUMMY);
		runSwing(() -> {
			var manager = tool.getWindowManager();
			Object placeholder = invokeInstanceMethod("getPlaceholder", manager, new Class<?>[] { docking.ComponentProvider.class }, new Object[] { provider });
			invokeInstanceMethod("movePlaceholder", manager, new Class<?>[] { placeholder.getClass(), java.awt.Point.class }, new Object[] { placeholder, new java.awt.Point(120, 120) });
		});
		waitForSwing();
		var template = runSwing(() -> tool.saveToolToToolTemplate());
		var data = runSwing(() -> tool.saveDataStateToXml(false));
		template.setName("LocationAcceptance");
		runSwing(() -> env.getProject().getLocalToolChest().addToolTemplate(template));
		var restored = env.launchTool("LocationAcceptance", file);
		runSwing(() -> restored.restoreDataStateFromXml(data));
		waitForSwing();
		runSwing(() -> restored.getService(GoToService.class).goTo(new ProgramLocation(restored.getService(ProgramManager.class).getCurrentProgram(), addr("010059a3"))));
		waitForSwing();
		runSwing(() -> {
			var browser = getPlugin(restored, ghidra.app.plugin.core.codebrowser.CodeBrowserPlugin.class);
			LocationContextPanel panel = (LocationContextPanel) find(browser.getProvider().getComponent(), "Location context");
			assertNotNull(panel);
			var restoredDecompiler = getPlugin(restored, DecompilePlugin.class);
			DecompilerProvider restoredProvider = (DecompilerProvider) getInstanceField("connectedProvider", restoredDecompiler);
			assertNotSame(SwingUtilities.getWindowAncestor(browser.getProvider().getComponent()), SwingUtilities.getWindowAncestor(restoredProvider.getComponent()));
			assertTrue(button(panel, "Go to displayed address").getText().contains("010059a3"));
			assertTrue(button(listingPanel(), "Go to displayed address").getText().contains("01002cf5"));
		});
	}

	@Test
	public void testUndefinedAndDataLocationsDisableFunctionNavigation() {
		for (String address : new String[] { "01001000", "01001001" }) {
			runSwing(() -> tool.getService(GoToService.class).goTo(new ProgramLocation(program, addr(address))));
			codeBrowser.updateNow();
			waitForSwing();
			runSwing(() -> {
				assertFalse(button(listingPanel(), "Go to function entry").isEnabled());
				assertTrue(button(listingPanel(), "Go to displayed address").isEnabled());
				assertTrue(button(listingPanel(), "Go to function entry").getText().contains("no function"));
			});
		}
	}

	@Test
	public void testCloneNavigationDoesNotRelabelConnectedDecompiler() {
		decompile("01002cf5");
		DecompilerProvider[] clone = new DecompilerProvider[1];
		runSwing(() -> {
			clone[0] = decompiler.createNewDisconnectedProvider();
			clone[0].doSetProgram(program);
			clone[0].goTo(program, new ProgramLocation(program, addr("010059a3")));
			tool.showComponentProvider(clone[0], true);
		});
		waitForCondition(() -> runSwing(() -> !clone[0].isBusy()));
		waitForSwing();
		runSwing(() -> {
			assertTrue(clone[0].getLocationContext().state().startsWith("Independent"));
			assertEquals(addr("010059a3"), clone[0].getLocationContext().location().getAddress());
			assertEquals(addr("01002cf5"), provider.getLocationContext().location().getAddress());
		});
	}

	@Test
	public void testKeyboardHistoryAndPopupFocus() throws Exception {
		decompile("01002cf5");
		decompile("010059a3");
		JButton back = runSwing(() -> button(listingPanel(), "Previous location in history"));
		runSwing(() -> back.requestFocusInWindow());
		waitForCondition(() -> runSwing(() -> back.isFocusOwner()));
		triggerKey(back, 0, java.awt.event.KeyEvent.VK_SPACE, ' ');
		codeBrowser.updateNow();
		waitForSwing();
		assertEquals(addr("01002cf5"), codeBrowser.getCurrentAddress());
		JButton next = runSwing(() -> button(listingPanel(), "Next location in history"));
		runSwing(() -> next.requestFocusInWindow());
		waitForCondition(() -> runSwing(() -> next.isFocusOwner()));
		triggerKey(next, java.awt.event.InputEvent.ALT_DOWN_MASK, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.KeyEvent.CHAR_UNDEFINED);
		waitForCondition(() -> runSwing(() -> next.getComponentPopupMenu().isVisible()));
		triggerKey(next, 0, java.awt.event.KeyEvent.VK_ESCAPE, java.awt.event.KeyEvent.CHAR_UNDEFINED);
		waitForCondition(() -> runSwing(() -> !next.getComponentPopupMenu().isVisible()));
		runSwing(() -> next.transferFocus());
		waitForSwing();
		runSwing(() -> assertFalse(next.isFocusOwner()));
	}

	@Test
	public void testUnchangedRefreshDoesNotScheduleRepaints() {
		decompile("01002cf5");
		runSwing(() -> {
			LocationContextPanel panel = listingPanel();
			panel.refreshContext();
			RepaintManager original = RepaintManager.currentManager(panel);
			java.util.concurrent.atomic.AtomicInteger dirty = new java.util.concurrent.atomic.AtomicInteger();
			RepaintManager probe = new RepaintManager() {
				@Override public void addDirtyRegion(JComponent component, int x, int y, int w, int h) {
					if (component == panel || SwingUtilities.isDescendingFrom(component, panel)) dirty.incrementAndGet();
					super.addDirtyRegion(component, x, y, w, h);
				}
			};
			try {
				RepaintManager.setCurrentManager(probe);
				for (int i = 0; i < 1000; i++) panel.refreshContext();
				System.out.println("LOCATION_IDLE_REPAINTS n=1000 dirty_regions=" + dirty.get());
				assertEquals("Unchanged context must not repaint", 0, dirty.get());
			}
			finally { RepaintManager.setCurrentManager(original); }
		});
	}

	@Test
	public void testNavigationDispatchBaseline() {
		decompile("01002cf5");
		String property = System.getProperty("ghidra.ui.locationContext");
		ghidra.framework.plugintool.PluginTool baseline;
		try {
			System.clearProperty("ghidra.ui.locationContext");
			baseline = env.launchAnotherDefaultTool();
		}
		finally { System.setProperty("ghidra.ui.locationContext", property); }
		runSwing(() -> baseline.getService(ProgramManager.class).openProgram(program));
		waitForSwing();
		runSwing(() -> {
			var browser = getPlugin(baseline, ghidra.app.plugin.core.codebrowser.CodeBrowserPlugin.class);
			assertNull(find(browser.getProvider().getComponent(), "Location context"));
			for (var measuredTool : new ghidra.framework.plugintool.PluginTool[] { baseline, tool }) {
				long[] times = new long[200];
				GoToService service = measuredTool.getService(GoToService.class);
				for (int i = 0; i < times.length; i++) {
					long start = System.nanoTime();
					service.goTo(new ProgramLocation(program, addr(i % 2 == 0 ? "01002cf5" : "010059a3")));
					times[i] = System.nanoTime() - start;
				}
				java.util.Arrays.sort(times);
				System.out.printf("LOCATION_DISPATCH enabled=%s n=200 p50_us=%.1f p95_us=%.1f%n", measuredTool == tool, times[100]/1000.0, times[190]/1000.0);
			}
		});
		waitForDecompiler();
	}

	@Test
	public void testThemeDensityAndOverflowCaptures() throws Exception {
		decompile("01002cf5");
		var manager = generic.theme.ThemeManager.getInstance();
		var old = manager.getActiveTheme();
		java.io.File output = new java.io.File(System.getProperty("ghidra.test.property.output.dir"), "location-acceptance");
		output.mkdirs();
		try {
			for (var theme : new generic.theme.GTheme[] { new generic.theme.uirefresh.GraphiteTheme(), new generic.theme.uirefresh.UiRefreshCompactDarkTheme(), new generic.theme.uirefresh.UiRefreshHighContrastTheme() }) {
				runSwing(() -> manager.setTheme(theme));
				waitForSwing();
				runSwing(() -> {
					LocationContextPanel panel = listingPanel();
					java.awt.Dimension size = panel.getSize();
					try {
						for (int width : new int[] { 360, 1100 }) {
							panel.setSize(width, panel.getPreferredSize().height);
							panel.doLayout();
							for (Component c : panel.getComponents()) if (c instanceof Container container) container.doLayout();
							assertTrue(button(panel, "Go to displayed address").getWidth() > 0);
							var image = new java.awt.image.BufferedImage(width, panel.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
							var graphics = image.createGraphics();
							try { panel.paint(graphics); }
							finally { graphics.dispose(); }
							try { javax.imageio.ImageIO.write(image, "png", new java.io.File(output, theme.getName().replace(' ', '-') + "-" + width + ".png")); }
							catch (java.io.IOException e) { throw new AssertionError(e); }
						}
					}
					finally { panel.setSize(size); panel.revalidate(); }
				});
			}
		}
		finally { runSwing(() -> manager.setTheme(old)); }
	}

	@Test
	public void testNavigationCompletionLatency() {
		decompile("01002cf5");
		long[] times = new long[30];
		for (int i = 0; i < times.length; i++) {
			String address = i % 2 == 0 ? "010059a3" : "01002cf5";
			long start = System.nanoTime();
			decompile(address);
			waitForSwing();
			times[i] = System.nanoTime() - start;
			runSwing(() -> {
				assertEquals(addr(address), codeBrowser.getCurrentAddress());
				assertEquals("Following", provider.getLocationContext().state());
			});
		}
		java.util.Arrays.sort(times);
		System.out.printf("LOCATION_COMPLETION n=30 p50_ms=%.2f p95_ms=%.2f%n", times[15]/1_000_000.0, times[28]/1_000_000.0);
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

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
			assertEquals("Independent", clone.getLocationContext().state());
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

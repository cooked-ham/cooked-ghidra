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
package ghidra.app.nav;

import static org.junit.Assert.*;

import java.awt.event.KeyEvent;

import org.junit.*;

import docking.widgets.searchlist.SearchList;
import ghidra.app.services.ProgramManager;
import ghidra.framework.model.*;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.listing.Program;
import ghidra.test.AbstractGhidraHeadedIntegrationTest;
import ghidra.test.TestEnv;
import ghidra.util.task.TaskMonitor;

/** Exercises real program lifecycle events and modal input, not a fake program manager. */
public class ProgramSwitcherDialogTest extends AbstractGhidraHeadedIntegrationTest {

	private TestEnv env;
	private PluginTool tool;
	private ProgramManager manager;
	private ProgramSwitcherDialog dialog;
	private SearchList<Program> search;

	@Before
	public void setUp() throws Exception {
		env = new TestEnv();
		tool = env.showTool();
		manager = tool.getService(ProgramManager.class);
	}

	@After
	public void tearDown() {
		if (dialog != null) {
			runSwing(() -> dialog.close());
		}
		env.dispose();
	}

	@Test
	public void testDuplicateNamesUsePathAndEnterSwitchesCorrectProgram() throws Exception {
		Program first = open("one", "same.bin", ProgramManager.OPEN_CURRENT);
		Program second = open("two", "same.bin", ProgramManager.OPEN_VISIBLE);
		showSwitcher();
		assertEquals(2, size());
		runSwing(() -> search.setFilterText("same TWO"));
		assertEquals(1, size());
		assertSame(first, manager.getCurrentProgram());
		triggerKey(search.getTextField(), 0, KeyEvent.VK_ENTER, '\n');
		waitForSwing();
		assertSame(second, manager.getCurrentProgram());
		assertFalse(dialog.isShowing());
	}

	@Test
	public void testCancellationLeavesCurrentProgramUnchanged() throws Exception {
		Program first = open("one", "first.bin", ProgramManager.OPEN_CURRENT);
		Program second = open("two", "second.bin", ProgramManager.OPEN_VISIBLE);
		showSwitcher();
		runSwing(() -> search.setSelectedItem(second));
		pressButtonByText(dialog, "Cancel");
		assertSame(first, manager.getCurrentProgram());
	}

	@Test
	public void testEnterFromFocusedResultListSwitchesOnce() throws Exception {
		open("one", "first.bin", ProgramManager.OPEN_CURRENT);
		Program second = open("two", "second.bin", ProgramManager.OPEN_VISIBLE);
		showSwitcher();
		runSwing(() -> search.setSelectedItem(second));
		javax.swing.JList<?> list = findComponent(search, javax.swing.JList.class);
		assertNotNull(list);
		triggerKey(list, 0, KeyEvent.VK_ENTER, '\n');
		waitForSwing();
		assertSame(second, manager.getCurrentProgram());
		assertFalse(dialog.isShowing());
	}

	@Test
	public void testExactNameRanksBeforePrefixAndSubstring() throws Exception {
		Program substring = open("one", "old-program.bin", ProgramManager.OPEN_CURRENT);
		Program prefix = open("two", "program.bin.backup", ProgramManager.OPEN_VISIBLE);
		Program exact = open("three", "program.bin", ProgramManager.OPEN_VISIBLE);
		showSwitcher();
		runSwing(() -> {
			search.setFilterText("PROGRAM.BIN");
			assertSame(exact, search.getModel().getElementAt(0).value());
			assertSame(prefix, search.getModel().getElementAt(1).value());
			assertSame(substring, search.getModel().getElementAt(2).value());
			assertSame(exact, search.getSelectedItem());
		});
	}

	@Test
	public void testClosingSelectionRemovesItWithoutSwitchingToClosedProgram() throws Exception {
		Program first = open("one", "first.bin", ProgramManager.OPEN_CURRENT);
		Program second = open("two", "second.bin", ProgramManager.OPEN_VISIBLE);
		showSwitcher();
		runSwing(() -> {
			search.setFilterText("second.bin");
			search.setSelectedItem(second);
			manager.closeProgram(second, true);
			search.chooseItem(); // queued input can still target the old row
		});
		waitForSwing();
		assertEquals(0, size());
		assertSame(first, manager.getCurrentProgram());
		assertSwitchDisabled();
	}

	@Test
	public void testHiddenProgramsAreNotRevealed() throws Exception {
		open("one", "visible.bin", ProgramManager.OPEN_CURRENT);
		Program hidden = open("two", "hidden.bin", ProgramManager.OPEN_HIDDEN);
		showSwitcher();
		assertEquals(1, size());
		runSwing(() -> search.setFilterText("hidden"));
		assertEquals(0, size());
		assertFalse(manager.isVisible(hidden));
	}

	@Test
	public void testOpenAndRenameUpdateWhileShowing() throws Exception {
		open("one", "first.bin", ProgramManager.OPEN_CURRENT);
		showSwitcher();
		runSwing(() -> search.setFilterText("renamed"));
		Program second = open("two", "second.bin", ProgramManager.OPEN_VISIBLE);
		waitForSwing();
		assertEquals(0, size());
		int transaction = second.startTransaction("Rename switcher fixture");
		try {
			second.setName("renamed.bin");
		}
		finally {
			second.endTransaction(transaction, true);
		}
		second.flushEvents();
		waitForSwing();
		assertEquals(1, size());
		runSwing(() -> assertSame(second, search.getModel().getElementAt(0).value()));
	}

	@Test
	public void testEmptySwitcherAndNoMatchDisableSwitch() {
		showSwitcher();
		assertEquals(0, size());
		runSwing(() -> search.setFilterText("no match"));
		assertSwitchDisabled();
		triggerKey(search.getTextField(), 0, KeyEvent.VK_ENTER, '\n');
		assertNull(manager.getCurrentProgram());
		assertTrue(dialog.isShowing());
	}

	@Test
	public void testDisposalWithQueuedProgramEvent() throws Exception {
		Program first = open("one", "first.bin", ProgramManager.OPEN_CURRENT);
		Program second = open("two", "second.bin", ProgramManager.OPEN_VISIBLE);
		showSwitcher();
		runSwing(() -> {
			manager.setCurrentProgram(second);
			dialog.close();
		});
		waitForSwing();
		showSwitcher();
		assertEquals(2, size());
		runSwing(() -> search.setSelectedItem(first));
		pressButtonByText(dialog, "Switch");
		assertSame(first, manager.getCurrentProgram());
	}

	private Program open(String folderName, String name, int state) throws Exception {
		DomainFolder root = env.getProject().getProjectData().getRootFolder();
		DomainFolder folder = root.getFolder(folderName);
		if (folder == null) {
			folder = root.createFolder(folderName);
		}
		ProgramBuilder builder = new ProgramBuilder(name, ProgramBuilder._TOY, this);
		Program program = builder.getProgram();
		try {
			DomainFile file = folder.createFile(name, program, TaskMonitor.DUMMY);
			return manager.openProgram(file, DomainFile.DEFAULT_VERSION, state);
		}
		finally {
			program.release(this);
		}
	}

	@SuppressWarnings("unchecked")
	private void showSwitcher() {
		dialog = runSwing(() -> new ProgramSwitcherDialog(tool));
		showDialogWithoutBlocking(tool, dialog);
		waitForDialogComponent(ProgramSwitcherDialog.class);
		search = (SearchList<Program>) findComponent(dialog.getComponent(), SearchList.class);
		assertNotNull(search);
	}

	private int size() {
		return runSwing(() -> search.getModel().getSize());
	}

	private void assertSwitchDisabled() {
		runSwing(() -> {
			javax.swing.JButton button = findButtonByText(dialog.getComponent(), "Switch");
			assertNotNull(button);
			assertFalse(button.isEnabled());
		});
	}
}

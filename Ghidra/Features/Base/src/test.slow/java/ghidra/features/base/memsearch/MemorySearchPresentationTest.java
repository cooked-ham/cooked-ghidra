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
package ghidra.features.base.memsearch;

import docking.DockingChrome;
import docking.widgets.ViewStatusPanel;
import ghidra.features.base.memsearch.format.SearchFormat;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.listing.Program;
import ghidra.util.table.GhidraTableFilterPanel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;

import static org.junit.Assert.*;

public class MemorySearchPresentationTest extends AbstractMemSearchTest {
    private Object previousChrome;

    @Before
    @Override
    public void setUp() throws Exception {
        previousChrome = UIManager.get(DockingChrome.UI_KEY);
        runSwing(() -> UIManager.put(DockingChrome.UI_KEY, true));
        super.setUp();
        setSearchFormat(SearchFormat.HEX);
    }

    @After
    public void restoreChrome() {
        runSwing(() -> UIManager.put(DockingChrome.UI_KEY, previousChrome));
    }

    @Override
    protected Program buildProgram() throws Exception {
        ProgramBuilder builder = new ProgramBuilder("Search presentation", ProgramBuilder._X86, this);
        builder.createMemory(".text", "0x1000", 256);
        builder.setBytes("0x1000", "55 48 89 e5 90 c3");
        return builder.getProgram();
    }

    @Test
    public void testReadyLoadingNoMatchesAndFilteredResults() {
        waitForSwing();
        assertHeading("Ready to search memory");
        setInput("ff ee dd cc");
        runSwing(() -> {
            searchProvider.search();
            assertHeading("Searching memory…");
        });
        waitForSearch(0);
        assertHeading("No memory matches");
        setInput("55 48 89 e5");
        performSearchAll();
        waitForSearch(1);
        ViewStatusPanel status = findComponent(searchProvider.getResultsPanel(), ViewStatusPanel.class);
        assertNotNull(status);
        assertFalse(runSwing(() -> status.getComponent(0).isVisible()));
        GhidraTableFilterPanel<?> filter =
                findComponent(searchProvider.getResultsPanel(), GhidraTableFilterPanel.class);
        assertNotNull(filter);
        runSwing(() -> filter.setFilterText("no_such_memory_result"));
        waitForCondition(() -> runSwing(() ->
                searchProvider.getResultsPanel().getMatchCount() == 0));
        waitForSwing();
        assertHeading("No matches pass this filter");
        runSwing(() -> filter.setFilterText(""));
        waitForCondition(() -> runSwing(() ->
                searchProvider.getResultsPanel().getMatchCount() == 1));
        waitForSwing();
        assertFalse(runSwing(() -> status.getComponent(0).isVisible()));
    }

    private void assertHeading(String expected) {
        runSwing(() -> {
            ViewStatusPanel status =
                    findComponent(searchProvider.getResultsPanel(), ViewStatusPanel.class);
            assertNotNull(status);
            JPanel message = (JPanel) status.getComponent(0);
            assertEquals(expected, ((JLabel) message.getComponent(0)).getText());
            assertTrue(message.isVisible());
        });
    }
}

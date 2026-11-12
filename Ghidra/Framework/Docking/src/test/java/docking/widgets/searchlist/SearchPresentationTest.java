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
package docking.widgets.searchlist;

import docking.DockingChrome;
import docking.widgets.ViewStatusPanel;
import generic.test.AbstractGuiTest;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.junit.Assert.*;

public class SearchPresentationTest extends AbstractGuiTest {
    @Test
    public void testHighlightEscapesMarkupAndMatchesLiteralWords() {
        SearchTextHighlighter highlighter = new SearchTextHighlighter();
        assertEquals("&lt;b&gt;<b>A+B</b> &amp; <b>owner</b>&lt;/b&gt;",
                highlighter.highlight("<b>A+B & owner</b>", "a+b owner"));
        assertEquals("&lt;b&gt;", highlighter.highlight("<b>", ""));
        assertEquals("<b>[x]</b>", highlighter.highlight("[x]", "[x]"));
    }

    @Test
    public void testGroupsPreserveSelectionAndStatusSurvivesThemeSwitch() {
        runSwing(() -> {
            Object previous = UIManager.get(DockingChrome.UI_KEY);
            try {
                DefaultSearchListModel<String> model = new DefaultSearchListModel<>();
                model.add("Local actions", List.of("Open program", "Close program"));
                model.add("Global actions", List.of("Help"));
                SearchList<String> search = new SearchList<>(model, null);
                @SuppressWarnings("unchecked")
                JList<SearchListEntry<String>> typed = findComponent(search, JList.class);
                assertNotNull(typed);
                for (boolean modern : new boolean[]{true, false, true}) {
                    UIManager.put(DockingChrome.UI_KEY, modern);
                    JPanel row = (JPanel) typed.getCellRenderer().getListCellRendererComponent(
                            typed, model.getElementAt(0), 0, true, false);
                    row.setSize(220, row.getPreferredSize().height);
                    row.doLayout();
                    BorderLayout layout = (BorderLayout) row.getLayout();
                    Component heading = layout.getLayoutComponent(modern ? BorderLayout.NORTH : BorderLayout.WEST);
                    assertNotNull(heading);
                    assertEquals("Local actions", ((JLabel) heading).getText());
                    assertEquals(3, model.getSize());
                    search.setSelectedItem("Close program");
                    assertEquals("Close program", search.getSelectedItem());
                }
                search.setFilterText("no such action");
                assertEquals(0, model.getSize());
                assertNull(search.getSelectedItem());
                ViewStatusPanel status = findComponent(search, ViewStatusPanel.class);
                assertNotNull(status);
                status.setSize(220, 200);
                status.doLayout();
                assertTrue(status.getComponent(0).isVisible());
                search.setFilterText("");
                status.doLayout();
                assertFalse(status.getComponent(0).isVisible());
                search.dispose();
            } finally {
                UIManager.put(DockingChrome.UI_KEY, previous);
            }
        });
    }

    @Test
    public void testReplacementStatusRestoresStockContent() {
        runSwing(() -> {
            Object previous = UIManager.get(DockingChrome.UI_KEY);
            try {
                JPanel content = new JPanel();
                ViewStatusPanel panel = new ViewStatusPanel(content, true);
                panel.setSize(220, 180);
                panel.setStatus("Graph unavailable", "Choose a function to view its graph.");
                UIManager.put(DockingChrome.UI_KEY, true);
                panel.doLayout();
                assertFalse(content.isVisible());
                UIManager.put(DockingChrome.UI_KEY, false);
                panel.doLayout();
                assertTrue(content.isVisible());
                assertFalse(panel.getComponent(0).isVisible());
                UIManager.put(DockingChrome.UI_KEY, true);
                panel.setStatus(null, null);
                panel.doLayout();
                assertTrue(content.isVisible());
            } finally {
                UIManager.put(DockingChrome.UI_KEY, previous);
            }
        });
    }
}
